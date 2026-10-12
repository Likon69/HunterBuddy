package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.resource.ConsumablePolicy;
import com.hunterbuddy.behavior.resource.LeaseState;
import com.hunterbuddy.behavior.resource.LookIntent;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.behavior.resource.SlotIntent;
import com.hunterbuddy.modules.regear.util.PlacementUtils;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;

public final class PlacementBridge {
    private final WorldReader world;
    private final Outputs outputs;
    private final ConsumablePolicy consumables;
    private long emittedAt = -4;
    private volatile Receipt receipt;

    public PlacementBridge(WorldReader world, Outputs outputs, ConsumablePolicy consumables) {
        this.world = Objects.requireNonNull(world);
        this.outputs = Objects.requireNonNull(outputs);
        this.consumables = Objects.requireNonNull(consumables);
    }

    public NodeResult place(TickContext ctx, BlockPos pos, Predicate<ItemStack> filter, boolean strict) {
        for (Resource resource : new Resource[]{Resource.SLOT, Resource.LOOK, Resource.INTERACT}) {
            if (ctx.leases().holder(resource).filter(lease -> lease.state() == LeaseState.GRANTED && lease.owner().equals(ctx.owner())).isEmpty()) {
                throw new IllegalStateException("placement without " + resource + " lease");
            }
        }
        if (!world.inBounds(pos) || !world.loaded(pos)) return new Failure(FailureCode.UNREACHABLE, "placement outside loaded world");
        int slot = world.findSlot(stack -> filter.test(stack) && consumables.placement(stack));
        if (slot < 0) return new Failure(FailureCode.MISSING_ITEM, "No hotbar block outside reserves; reserve=25 enderChestsMin=2");
        if (!PlacementUtils.canPlace(pos, strict)) return new Running("waiting for free placement space");
        var hit = PlacementUtils.resolvePlaceHit(pos, PlacementUtils.DEFAULT_REACH);
        if (hit == null || world.client().player.getEyePos().squaredDistanceTo(hit.getPos()) > PlacementUtils.DEFAULT_REACH * PlacementUtils.DEFAULT_REACH) {
            return new Failure(FailureCode.UNREACHABLE, "placement is out of reach");
        }
        float[] angles = PlacementUtils.rotationsForHit(hit);
        LookIntent look = new LookIntent(ctx.sensors().yaw() + Conditions.wrap(angles[0] - ctx.sensors().yaw()), angles[1]);
        outputs.look(ctx.owner(), look);
        outputs.slot(ctx.owner(), new SlotIntent(slot, true));
        if (!Conditions.facing(ctx.sensors(), look) || ctx.sensors().selectedSlot() != slot || ctx.sensors().serverSlot() != slot
            || !outputs.slotSettled(slot)) return new Running("aligning visible placement look and slot");
        if (outputs.nativeTick() - emittedAt < 4) return new Running("placement cadence=4");
        ItemStack stack = world.client().player.getInventory().getStack(slot);
        if (!filter.test(stack) || !consumables.placement(stack)) return new Failure(FailureCode.MISSING_ITEM, "reserve=25");
        emittedAt = outputs.nativeTick();
        receipt = new Receipt(world.identity(), pos.toImmutable(), ((BlockItem) stack.getItem()).getBlock());
        return PlacementUtils.placeBlock(pos, new FindItemResult(slot, stack.getCount()), false, true, strict)
            ? Success.INSTANCE : new Running("placement not emitted");
    }

    public boolean pillarItem(ItemStack stack) {
        return consumables.pillar(stack);
    }

    public boolean confirmed(BlockPos pos) {
        Receipt current = receipt;
        return current != null && current.world.equals(world.identity()) && current.pos.equals(pos) && current.confirmed.get()
            && world.loaded(pos) && world.state(pos).getBlock() == current.block;
    }

    public void worldLost() {
        receipt = null;
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (event.isCancelled() || world.client().getNetworkHandler() == null
            || event.connection != world.client().getNetworkHandler().getConnection()) return;
        if (event.packet instanceof BlockUpdateS2CPacket update) confirm(update.getPos(), update.getState());
        else if (event.packet instanceof ChunkDeltaUpdateS2CPacket update) update.visitUpdates(this::confirm);
    }

    private void confirm(BlockPos pos, BlockState state) {
        Receipt current = receipt;
        if (current != null && current.pos.equals(pos)) current.confirmed.set(state.getBlock() == current.block);
    }

    private static final class Receipt {
        private final WorldIdentity world;
        private final BlockPos pos;
        private final Block block;
        private final AtomicBoolean confirmed = new AtomicBoolean();

        private Receipt(WorldIdentity world, BlockPos pos, Block block) {
            this.world = world;
            this.pos = pos;
            this.block = block;
        }
    }
}
