package com.hunterbuddy.behavior.mc;

import baritone.api.utils.input.Input;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.resource.LeaseState;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.modules.HMine;
import com.hunterbuddy.modules.mine.BreakMath;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class MiningBridge {
    private final WorldReader world;
    private final BaritoneBridge baritone;
    private final BooleanSupplier ownBaritone;
    private volatile Job job;
    private boolean clicking;
    private boolean foreign;
    private BlockPos toolPos;
    private int heldTool = -1;
    private final java.util.Map<BlockPos, WorldIdentity> temporaryChests = new java.util.HashMap<>();

    public void trackTemporaryChest(BlockPos pos) { temporaryChests.put(pos.toImmutable(), world.identity()); }
    public void forgetTemporaryChest(BlockPos pos) { temporaryChests.remove(pos); }

    public void holdTool(BlockPos pos, int slot) {
        toolPos = pos.toImmutable();
        heldTool = slot;
    }

    public void releaseTool() {
        toolPos = null;
        heldTool = -1;
    }

    public MiningBridge(WorldReader world, BaritoneBridge baritone, BooleanSupplier ownBaritone) {
        this.world = Objects.requireNonNull(world);
        this.baritone = Objects.requireNonNull(baritone);
        this.ownBaritone = Objects.requireNonNull(ownBaritone);
    }

    private HMine mine() {
        return Modules.get().get(HMine.class);
    }

    public boolean active() {
        HMine mine = mine();
        return mine != null && mine.isActive();
    }

    public String refusal() {
        if (!active()) return "HMine is inactive";
        Setting<?> mode = mine().settings.get("mode");
        if (mode == null || mode.get() != HMine.SpeedmineMode.PACKET) return "HMine must use PACKET mode";
        Setting<?> rotate = mine().settings.get("rotate");
        return rotate != null && Boolean.TRUE.equals(rotate.get()) ? "HMine rotate must be off for visible LOOK" : null;
    }

    public boolean ownsMining() {
        HMine mine = mine();
        if (mine == null || !mine.isMining()) foreign = false;
        Job current = job;
        return !foreign && current != null && current.world.equals(world.identity());
    }

    public boolean protectedBlock(BlockPos pos) {
        return baritone.settings().blocksToDisallowBreaking.value.contains(world.state(pos).getBlock());
    }

    public boolean vanilla(BlockPos pos) {
        HMine mine = mine();
        if (mine == null) return false;
        BlockState state = world.state(pos);
        Setting<?> blocks = mine.settings.get("leave-to-vanilla");
        if (blocks != null && blocks.get() instanceof List<?> list && list.contains(state.getBlock())) return true;
        Setting<?> oneShot = mine.settings.get("vanilla-one-shot");
        return oneShot != null && Boolean.TRUE.equals(oneShot.get())
            && state.calcBlockBreakingDelta(world.client().player, world.client().world, pos) >= 1;
    }

    public int bestTool(BlockPos pos) {
        if (pos.equals(toolPos)) return heldTool;
        int faster = BreakMath.fasterThanHeld(world.client().player, world.state(pos));
        return faster < 0 ? world.client().player.getInventory().getSelectedSlot() : faster;
    }

    public boolean canQueue(BlockPos pos) {
        HMine mine = mine();
        if (mine == null || !mine.isMining()) return true;
        return ownsMining() && job.pos.equals(pos);
    }

    public void queue(TickContext ctx, BlockPos pos, Direction face) {
        requireLease(ctx);
        if (!active() || !canQueue(pos)) throw new IllegalStateException("HMine is unavailable or busy");
        if (job == null || !job.pos.equals(pos) || !job.world.equals(world.identity())) job = new Job(pos.toImmutable(), world.identity());
        mine().queueMiningData(mine().new MiningData(pos, face));
    }

    public boolean holdVanilla(TickContext ctx, BlockPos pos) {
        requireLease(ctx);
        if (!(world.client().crosshairTarget instanceof BlockHitResult hit) || !hit.getBlockPos().equals(pos)) {
            releaseClick();
            return false;
        }
        if (job == null || !job.pos.equals(pos) || !job.world.equals(world.identity())) job = new Job(pos.toImmutable(), world.identity());
        var inputs = baritone.inputs().orElseThrow();
        if (!clicking && inputs.isInputForcedDown(Input.CLICK_LEFT)) return false;
        clicking = true;
        if (!inputs.isInputForcedDown(Input.CLICK_LEFT)) inputs.setInputForceState(Input.CLICK_LEFT, true);
        return true;
    }

    public boolean confirmed(BlockPos pos) {
        Job current = job;
        return current != null && current.world.equals(world.identity()) && current.pos.equals(pos) && current.confirmed.get();
    }

    public void prepare(TickContext ctx, BlockPos pos) {
        requireLease(ctx);
        job = new Job(pos.toImmutable(), world.identity());
    }

    public void releaseClick() {
        if (!clicking) return;
        baritone.inputs().ifPresent(inputs -> {
            if (inputs.isInputForcedDown(Input.CLICK_LEFT)) inputs.setInputForceState(Input.CLICK_LEFT, false);
        });
        clicking = false;
    }

    public void worldLost() {
        releaseTool();
        temporaryChests.clear();
        releaseClick();
        job = null;
        foreign = false;
    }

    private void requireLease(TickContext ctx) {
        if (ctx.leases().holder(Resource.MINING).filter(lease -> lease.state() == LeaseState.GRANTED
            && lease.owner().equals(ctx.owner())).isEmpty()) throw new IllegalStateException("mining without MINING lease");
    }

    @EventHandler
    private void onBreaking(StartBreakingBlockEvent event) {
        if (!ownBaritone.getAsBoolean() && (!clicking || job == null || !job.pos.equals(event.blockPos))) foreign = true;
    }

    @EventHandler
    private void onSend(PacketEvent.Send event) {
        if (event.packet instanceof PlayerActionC2SPacket action
            && action.getAction() == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK
            && world.available() && world.identity().equals(temporaryChests.get(action.getPos()))
            && !InventoryLayout.silkPickaxe(world.client().player.getInventory().getStack(com.hunterbuddy.util.ServerSlot.get().slot()))) {
            event.cancel();
            return;
        }
        if (!ownBaritone.getAsBoolean() && event.packet instanceof PlayerActionC2SPacket action
            && action.getAction() == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK
            && (job == null || !job.pos.equals(action.getPos()))) foreign = true;
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (event.isCancelled() || world.client().getNetworkHandler() == null
            || event.connection != world.client().getNetworkHandler().getConnection()) return;
        if (event.packet instanceof BlockUpdateS2CPacket update) confirm(update.getPos(), update.getState());
        else if (event.packet instanceof ChunkDeltaUpdateS2CPacket update) update.visitUpdates(this::confirm);
    }

    private void confirm(BlockPos pos, BlockState state) {
        Job current = job;
        if (current != null && current.pos.equals(pos) && (state.isAir() || state.getBlock() instanceof FluidBlock)) current.confirmed.set(true);
    }

    private static final class Job {
        private final BlockPos pos;
        private final WorldIdentity world;
        private final AtomicBoolean confirmed = new AtomicBoolean();

        private Job(BlockPos pos, WorldIdentity world) {
            this.pos = pos;
            this.world = world;
        }
    }
}
