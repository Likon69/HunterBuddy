package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.farmer.ChunkObservation;
import com.hunterbuddy.behavior.farmer.CoverageState;
import com.hunterbuddy.behavior.mc.InventoryLayout;
import com.hunterbuddy.behavior.mc.LogisticsInventory;
import com.hunterbuddy.behavior.mc.MineDropReceipt;
import com.hunterbuddy.behavior.mc.MineGeometry;
import com.hunterbuddy.behavior.mc.MineSiteContext;
import com.hunterbuddy.behavior.mc.ZoneCoverage;
import com.hunterbuddy.behavior.nodes.BreakBlock;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.ConfirmMiningPickup;
import com.hunterbuddy.behavior.nodes.Deferred;
import com.hunterbuddy.behavior.nodes.Fail;
import com.hunterbuddy.behavior.nodes.Guard;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.ObserveMiningDrop;
import com.hunterbuddy.behavior.nodes.PickupItem;
import com.hunterbuddy.behavior.nodes.RepeatUntil;
import com.hunterbuddy.behavior.nodes.RepositionForMining;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.ValidateMineBlock;
import com.hunterbuddy.behavior.nodes.WaitUntil;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.Set;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

public final class MineBlockTree {
    private MineBlockTree() {}

    public static Node build(MineSiteContext mine, BlockPos point, boolean material, BlockPos stand) {
        return new Sequence("confirmed-break-and-all-drops",
            new InventoryMilestone("bind-mining-position", ctx -> {
                mine.target = point;
                ctx.bb().set(ctx.owner(), mine.targetKey, point);
            }),
            LogisticsOperations.unless("block already durably broken", () -> mine.facts.pending != null && mine.facts.broken,
                breakBlock(mine, point, material, stand)),
            new InventoryMilestone("restore-drop-receipt", ctx -> mine.receipt = new MineDropReceipt(mine, point)),
            new ObserveMiningDrop(mine), pickup(mine, point),
            new RepeatUntil("collect-all-observed-mining-drops", () -> mine.receipt.collected(), 32, pickup(mine, point)),
            new InventoryMilestone("record-confirmed-mining-pickup", ctx -> {
                if (!mine.receipt.collected()) throw new IllegalStateException("mining drops are not all confirmed in inventory");
                mine.facts.pending = null;
                mine.facts.broken = false;
                mine.save(ctx, "mine-site picked-up at=" + point.toShortString());
                mine.outputs.mining().releaseTool();
            })).resumable();
    }

    private static Node breakBlock(MineSiteContext mine, BlockPos point, boolean material, BlockPos stand) {
        return new Sequence("stand-prepare-guard-break-record",
            GroundTravel.build(stand, 0, mine.baritone, mine.outputs),
            new WaitUntil(new Condition("falling blocks settled", sensors -> mine.world.client().world
                .getEntitiesByClass(FallingBlockEntity.class, new Box(point).expand(2), entity -> true).isEmpty()), 60),
            new ValidateMineBlock(mine, point, material), tool(mine, point),
            new InventoryMilestone("record-before-mining", ctx -> {
                BlockPos anchor = BlockPos.fromLong(mine.facts.stairs.getLast());
                if (mine.profile.strategy().equals("surface") && stand.getY() < anchor.getY()) {
                    mine.facts.stairs.add(stand.asLong());
                    mine.protectSupport(stand.down());
                }
                if (mine.facts.pending != null && mine.facts.pending != point.asLong()) {
                    throw new IllegalStateException("previous mining drop must be reconciled first");
                }
                if (mine.facts.pending == null) {
                    mine.facts.stand = stand.asLong();
                    mine.facts.pending = point.asLong();
                    mine.facts.material = material;
                    mine.receipt = new MineDropReceipt(mine, point);
                    mine.receipt.begin();
                    mine.save(ctx, "mine-site before-break at=" + point.toShortString());
                }
                mine.outputs.mining().holdTool(point, 0);
            }),
            new RepositionForMining(mine, point, stand,
                new WithLease(Set.of(Resource.MINING, Resource.LOOK, Resource.SLOT), LeasePolicy.WAIT,
                new Guard(new Condition("zone and support still safe", sensors -> mine.inside(point)
                    && mine.inside(mine.world.feet()) && !mine.facts.supports.contains(point.asLong())
                    && !point.equals(mine.world.feet().down())
                    && (mine.world.cleared(point) || !MineGeometry.fallingAbove(mine.world, point))),
                    new BreakBlock(mine.targetKey, false, mine.world, mine.outputs).limit(1200)))),
            new InventoryMilestone("record-server-confirmed-break", ctx -> {
                if (!mine.outputs.mining().confirmed(point)) throw new IllegalStateException("server break receipt is missing");
                mine.facts.broken = true;
                if (mine.facts.material) mine.facts.mined++;
                else mine.facts.excavated++;
                mine.patch.remove(point);
                for (Direction direction : Direction.values()) {
                    BlockPos next = point.offset(direction);
                    if (mine.inside(next) && !mine.world.loaded(next)) mine.facts.unexplored = true;
                    if (mine.material(next)) mine.patch.add(next);
                }
                if (mine.material(point)) mine.patch.add(point);
                String chunk = ZoneCoverage.key(point.getX() >> 4, point.getZ() >> 4);
                mine.knowledge.zone(mine.zone, mine.profile).coverage.put(chunk,
                    new ChunkObservation(CoverageState.OBSERVED, System.currentTimeMillis(), "terrain changed by confirmed mining"));
                mine.knowledge.zone(mine.recordedZone, mine.profile).coverage.put(chunk,
                    new ChunkObservation(CoverageState.OBSERVED, System.currentTimeMillis(), "terrain changed by confirmed mining"));
                mine.site.status = "observed";
                mine.site.verifiedAt = 0;
                if (mine.facts.changedChunks.add(chunk)) mine.knowledge.save(ctx);
                mine.save(ctx, "mine-site broken at=" + point.toShortString());
            })).resumable();
    }

    private static Node tool(MineSiteContext mine, BlockPos point) {
        return new Deferred("prepare-healthy-mining-tool", () -> {
            ItemStack chosen = ItemStack.EMPTY;
            float speed = -1;
            for (int slot = 0; slot < mine.world.client().player.getInventory().size(); slot++) {
                ItemStack stack = mine.world.client().player.getInventory().getStack(slot);
                if (!LogisticsInventory.anyTool(stack) || !LogisticsInventory.healthy(stack)) continue;
                if (mine.profile.blocks().contains(mine.world.state(point).getBlock())
                    && !LogisticsInventory.tool(stack, mine.profile)) continue;
                if (mine.world.state(point).isToolRequired() && !stack.isSuitableFor(mine.world.state(point))) continue;
                float candidate = stack.getMiningSpeedMultiplier(mine.world.state(point));
                if (candidate > speed || candidate == speed && InventoryLayout.silkPickaxe(chosen)
                    && !InventoryLayout.silkPickaxe(stack)) {
                    chosen = stack.copy();
                    speed = candidate;
                }
            }
            if (chosen.isEmpty()) {
                mine.facts.outcome = "regear nécessaire: aucun outil sain adapté à ce bloc";
                return new Fail(FailureCode.MISSING_ITEM, mine.facts.outcome);
            }
            ItemStack expected = chosen;
            return EquipmentTree.prepareHeld(new InventoryLayout(mine.world), stack -> sameTool(stack, expected),
                0, mine.outputs, mine.journal);
        }).resumable();
    }

    private static boolean sameTool(ItemStack actual, ItemStack expected) {
        if (!LogisticsInventory.healthy(actual) || !actual.isOf(expected.getItem())) return false;
        ItemStack normalized = actual.copy();
        if (normalized.isDamageable()) normalized.setDamage(expected.getDamage());
        return ItemStack.areEqual(normalized, expected);
    }

    private static Node pickup(MineSiteContext mine, BlockPos point) {
        return new Deferred("pickup-confirmed-block-drop", () -> {
            int baseline = mine.receipt.pickupBaseline();
            return new ConfirmMiningPickup(mine, new PickupItem(point, mine.receipt::accepts, () -> baseline, "mine-" + point.asLong(),
                mine.world, mine.baritone, mine.outputs, mine.journal, mine.receipt::safePickup, mine.receipt::pickupPoint));
        }).resumable();
    }
}
