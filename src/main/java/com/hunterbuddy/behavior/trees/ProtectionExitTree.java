package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.farmer.LogisticsFacts;
import com.hunterbuddy.behavior.mc.LogisticsContext;
import com.hunterbuddy.behavior.mc.LogisticsInventory;
import com.hunterbuddy.behavior.nodes.BreakBlock;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Deferred;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.PickupItem;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.SetValue;
import com.hunterbuddy.behavior.nodes.ShelterExitFallback;
import com.hunterbuddy.behavior.nodes.SwapArmor;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WaitUntil;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.util.GliderSwap;
import java.util.Set;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

public final class ProtectionExitTree {
    private ProtectionExitTree() {}

    public static Node build(LogisticsContext logistics) {
        return new Sequence("shelter-exit",
            new InventoryMilestone("choose-shelter-exit-from-current-mission", ctx -> {
                if (!logistics.facts.exitBreaking && !logistics.facts.groundExitRequired) {
                    logistics.facts.exitTop = logistics.topExitRequested();
                }
                logistics.save(ctx, "shelter exit selected from current mission state");
            }), new Deferred("chosen-shelter-exit", () -> logistics.facts.exitTop
                ? new ShelterExitFallback(logistics, top(logistics), ground(logistics)) : ground(logistics)),
            new InventoryMilestone("exit-confirmed", ctx -> {
                logistics.facts.departure = LogisticsFacts.coordinates(logistics.world.feet());
                logistics.outputs.mining().releaseExit();
                logistics.save(ctx, "exit confirmed at=" + logistics.world.feet().toShortString());
            }));
    }

    private static Node ground(LogisticsContext logistics) {
        BlockPos door = logistics.facts.door();
        Key<BlockPos> key = new Key<>("logistics-exit-door", BlockPos.class, Scope.OWNER, false);
        return new Sequence("one-block-exit",
            new InventoryMilestone("wall-evidence", ctx -> {
                LogisticsFacts facts = logistics.facts;
                if (!facts.exitBreaking) {
                    int index = -1;
                    for (int i = 0; i < facts.walls.size(); i++) {
                        if (LogisticsFacts.position(facts.walls.get(i)).equals(door)) index = i;
                    }
                    String actual = Registries.ITEM.getId(logistics.world.state(door).getBlock().asItem()).toString();
                    if (index < 0 || !facts.wallItems.get(index).equals(actual)) {
                        throw new IllegalStateException("protection door differs from its construction receipt");
                    }
                    facts.exitItem = actual;
                    facts.exitBaseline = logistics.world.countMatching(stack -> exitDrop(logistics, stack));
                    facts.exitBreaking = true;
                    logistics.save(ctx, "one protection block exit intent");
                }
                if (!logistics.world.cleared(door) && !Registries.ITEM.getId(
                    logistics.world.state(door).getBlock().asItem()).toString().equals(facts.exitItem)) {
                    throw new IllegalStateException("exit wall changed after break intent");
                }
                logistics.outputs.mining().allowExit(door);
            }), new SetValue<>(key, ctx -> door),
            LogisticsOperations.unless("exit block broken", () -> logistics.world.cleared(door),
                new Timeout(400, new WithLease(Set.of(Resource.MINING), LeasePolicy.WAIT,
                    new BreakBlock(key, false, logistics.world, logistics.outputs)))),
            new PickupItem(door, stack -> exitDrop(logistics, stack), () -> logistics.facts.exitBaseline,
                "protection-exit", logistics.world, logistics.baritone, logistics.outputs, logistics.mission),
            new InventoryMilestone("exit-block-picked-up", ctx -> {
                if (logistics.world.countMatching(stack -> exitDrop(logistics, stack)) != logistics.facts.exitBaseline + 1) {
                    throw new IllegalStateException("exit block recovery is ambiguous");
                }
                logistics.outputs.mining().releaseExit();
                logistics.save(ctx, "one exit block recovered");
            }), GroundTravel.horizontal(logistics.facts.origin().north(3), logistics.baritone, logistics.outputs));
    }

    private static Node top(LogisticsContext logistics) {
        return LogisticsOperations.unless("above the shelter",
            () -> logistics.world.client().player.isGliding()
                && logistics.world.feet().getY() >= logistics.facts.origin().getY() + 2,
            new Timeout(800, new Sequence("exit-through-top",
                new InventoryMilestone("flight-authorized", ctx -> {
                    if (ctx.landing() || logistics.outputs.landingFlight()) {
                        throw new IllegalStateException("flight exit is forbidden under Landing");
                    }
                }), LogisticsOperations.prepare(logistics,
                    stack -> GliderSwap.isGlider(stack) && LogisticsInventory.healthy(stack), 8),
                new WithLease(Set.of(Resource.SLOT, Resource.INTERACT), LeasePolicy.WAIT,
                    new SwapArmor(true, logistics.world, logistics.outputs, logistics.mission)),
                TakeoffTree.build(logistics.world, logistics.outputs),
                new WaitUntil(new Condition("above protection", sensors -> sensors.gliding()
                    && sensors.y() >= logistics.facts.origin().getY() + 2), 100))));
    }

    private static boolean exitDrop(LogisticsContext logistics, ItemStack stack) {
        return !stack.isEmpty() && (Registries.ITEM.getId(stack.getItem()).toString().equals(logistics.facts.exitItem)
            || logistics.facts.exitItem.equals("minecraft:stone") && stack.isOf(Items.COBBLESTONE));
    }
}
