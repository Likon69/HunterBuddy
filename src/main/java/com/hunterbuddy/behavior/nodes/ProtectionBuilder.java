package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.core.TreeValidator;
import com.hunterbuddy.behavior.farmer.FarmerProfile;
import com.hunterbuddy.behavior.farmer.FarmerZone;
import com.hunterbuddy.behavior.farmer.LogisticsFacts;
import com.hunterbuddy.behavior.farmer.LogisticsMemory;
import com.hunterbuddy.behavior.farmer.MissionJournal;
import com.hunterbuddy.behavior.mc.InventoryLayout;
import com.hunterbuddy.behavior.mc.LogisticsSite;
import com.hunterbuddy.behavior.mc.ProtectionRegistry;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.behavior.trees.EquipmentTree;
import java.util.List;
import java.util.Set;
import net.minecraft.block.FallingBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

public final class ProtectionBuilder extends Node {
    private final LogisticsFacts state;
    private final LogisticsMemory journal;
    private final WorldReader world;
    private final Outputs outputs;
    private final boolean floorOnly;
    private Node child;
    private boolean preparing;
    private final FarmerProfile profile;
    private final MissionJournal mission;

    public ProtectionBuilder(LogisticsFacts state, LogisticsMemory journal, WorldReader world, Outputs outputs, boolean floorOnly,
        FarmerProfile profile, MissionJournal mission) {
        super(floorOnly ? "logistics-floor" : "logistics-protection");
        this.state = state;
        this.journal = journal;
        this.world = world;
        this.outputs = outputs;
        this.floorOnly = floorOnly;
        this.profile = profile;
        this.mission = mission;
    }

    @Override
    public List<Node> children() {
        return child == null ? List.of() : List.of(child);
    }

    @Override
    protected void onStart(TickContext ctx) {
        child = null;
        preparing = false;
        ProtectionRegistry.remember(ctx, mission, outputs.mining(), state.origin());
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        List<BlockPos> positions = LogisticsSite.shell(state.origin());
        int end = floorOnly ? 1 : positions.size();
        BlockPos pos = null;
        for (int i = 0; i < end; i++) {
            BlockPos candidate = positions.get(i);
            if (state.building != null && candidate.equals(LogisticsFacts.position(state.building))) {
                pos = candidate;
                break;
            }
            if (!solid(candidate)) {
                pos = candidate;
                break;
            }
            if (candidate.getY() >= state.origin().getY() && !recorded(candidate)) {
                state.walls.add(LogisticsFacts.coordinates(candidate));
                state.wallItems.add(Registries.ITEM.getId(world.state(candidate).getBlock().asItem()).toString());
                journal.save(ctx, state, "protection existing at=" + candidate.toShortString());
            }
        }
        if (pos == null) return Success.INSTANCE;
        BlockPos target = pos;
        FarmerZone zone = state.activeZone();
        if (!zone.containsWithMargin(pos.getX() + 0.5, pos.getZ() + 0.5, 3)) return fail(FailureCode.REFUSED,
            "protection position outside zone margin");
        if (!world.loaded(pos)) return new Running("waiting for protection chunk");
        if (state.building != null && child == null) {
            if (!pos.equals(LogisticsFacts.position(state.building))) return fail(FailureCode.REFUSED, "protection journal index changed");
            if (Registries.ITEM.getId(world.state(pos).getBlock().asItem()).toString().equals(state.buildingItem)) return confirmed(ctx,
                pos);
            if (!world.state(pos).isReplaceable()) return fail(FailureCode.REFUSED, "protection placement is ambiguous at " +
                pos.toShortString());
        }
        if (child == null && solid(pos)) {
            journal.save(ctx, state, "protection existing at=" + pos.toShortString());
            return new Running("using existing solid protection");
        }
        if (child == null) {
            if (!world.state(pos).isReplaceable()) return fail(FailureCode.UNREACHABLE, "protection space needs excavation");
            if (world.findSlot(this::block) < 0) {
                child = EquipmentTree.prepareHeld(new InventoryLayout(world, profile), this::block, 5, outputs, mission);
                preparing = true;
                TreeValidator.validate(child);
                TreeValidator.bind(child, path() + "/prepare-block", ctx.owner(), hook());
                return new Running("bringing protection blocks to the hotbar");
            }
            ItemStack chosen = world.client().player.getInventory().getStack(world.findSlot(this::block));
            state.building = LogisticsFacts.coordinates(pos);
            state.buildingItem = Registries.ITEM.getId(chosen.getItem()).toString();
            journal.save(ctx, state, "protection placing at=" + pos.toShortString());
            Key<BlockPos> key = new Key<>("logistics-protection-pos", BlockPos.class, Scope.OWNER, false);
            child = new Timeout(160, new WithLease(Set.of(Resource.SLOT, Resource.LOOK, Resource.INTERACT), LeasePolicy.WAIT,
                new Sequence("build-protection-block", new SetValue<>(key, tick -> target), new PlaceBlock(key, stack -> block(stack)
                    && Registries.ITEM.getId(stack.getItem()).toString().equals(state.buildingItem), !floorOnly, world, outputs))));
            TreeValidator.validate(child);
            TreeValidator.bind(child, path() + "/block", ctx.owner(), hook());
        }
        NodeResult result = child.execute(ctx);
        if (result instanceof Failure) return result;
        if (preparing && result instanceof Success) {
            preparing = false;
            child = null;
            return new Running("protection block ready");
        }
        return result instanceof Success ? confirmed(ctx, pos) : result;
    }

    private boolean recorded(BlockPos pos) {
        return state.walls.stream().map(LogisticsFacts::position).anyMatch(pos::equals);
    }

    private boolean solid(BlockPos pos) {
        return world.state(pos).isFullCube(world.client().world, pos) && !(world.state(pos).getBlock() instanceof FallingBlock) &&
            world.state(pos).getFluidState().isEmpty();
    }

    private boolean block(ItemStack stack) {
        return world.count(Items.OBSIDIAN) > 25 ? stack.isOf(Items.OBSIDIAN)
            : stack.isOf(Items.COBBLESTONE) || stack.isOf(Items.COBBLED_DEEPSLATE) || stack.isOf(Items.NETHERRACK) ||
                stack.isOf(Items.DIRT) || stack.isOf(Items.STONE);
    }

    private NodeResult confirmed(TickContext ctx, BlockPos pos) {
        if (pos.getY() >= state.origin().getY() && !recorded(pos)) {
            state.walls.add(LogisticsFacts.coordinates(pos));
            state.wallItems.add(state.buildingItem);
        }
        state.building = null;
        state.buildingItem = "";
        child = null;
        journal.save(ctx, state, "protection confirmed at=" + pos.toShortString());
        ctx.progress("protection block confirmed");
        return new Running("protection building");
    }
}
