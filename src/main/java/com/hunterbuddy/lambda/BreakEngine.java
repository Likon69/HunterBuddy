/*
 * Port of the breaking half of com.lambda.interaction.managers.breaking.BreakManager
 * (lambda 1.21.11): startBreaking, updateBreakProgress, onBlockBreak and the delay bookkeeping.
 */
package com.hunterbuddy.lambda;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

/**
 * Drives blocks from requested to broken, one primary break at a time.
 *
 * <p>The order of the opening packets is the whole thing. A break is not "start now, stop when
 * done" — that is what a legit client does and it needs you to keep looking at the block. Here the
 * opening burst is shaped by the bypass mode, the block then counts down on its own, and the
 * closing stop is sent when the progress says so.
 */
public class BreakEngine {
    private final List<BreakInfo> infos = new ArrayList<>();

    /** Ticks to wait before another block may be started. */
    private int breakDelay;

    /** Old Grim insists on a minimum spacing of its own. */
    private int oldGrimBreakDelay;

    /** Ticks a break may sit without progressing before it is abandoned. */
    private static final int STALE_TICKS = 60;

    private int idleTicks;

    public void clear() {
        for (BreakInfo info : infos) info.abortBreakPacket();

        infos.clear();
        breakDelay = 0;
        oldGrimBreakDelay = 0;
        idleTicks = 0;
    }

    public boolean isBreaking() {
        return !infos.isEmpty();
    }

    public BlockPos currentPos() {
        return infos.isEmpty() ? null : infos.get(0).getBlockPos();
    }

    /**
     * Offers a block to break.
     *
     * <p>Refused while a break is running or the inter-block delay has not run out: the client can
     * only be breaking one block at a time, and starting a second resets both.
     */
    public boolean request(BreakContext context) {
        if (context == null) return false;
        if (breakDelay > 0 || oldGrimBreakDelay > 0) return false;

        for (BreakInfo info : infos) {
            if (info.getBlockPos().equals(context.getBlockPos())) return false;
        }

        if (!infos.isEmpty()) return false;

        infos.add(new BreakInfo(context, BreakInfo.BreakType.Primary));
        idleTicks = 0;
        return true;
    }

    /** Called when the server says the block is gone, so the entry stops being drawn or retried. */
    public void onBlockRemoved(BlockPos pos) {
        infos.removeIf(info -> info.getBlockPos().equals(pos));
    }

    /**
     * Must be driven from {@code TickEvent.Pre}.
     *
     * <p>Nothing here puts a break on the wire. The rotation is queued, and the packets go out in
     * its callback — which Meteor runs once the look has actually been sent, later in the same
     * tick. Driven from {@code Post} instead, that callback lands a tick late and every break
     * opens against last tick's angle.
     */
    public void tick() {
        if (MeteorClient.mc.player == null || MeteorClient.mc.world == null) return;

        if (breakDelay > 0) breakDelay--;
        if (oldGrimBreakDelay > 0) oldGrimBreakDelay--;

        // Purged here rather than after the pass below, because the pass below no longer finishes
        // anything: it queues callbacks. A block that fell did so after the last purge, so this is
        // the first moment the flag exists to be read.
        infos.removeIf(info -> info.broken
            || MeteorClient.mc.world.getBlockState(info.getBlockPos()).isAir());

        if (infos.isEmpty()) return;

        // A break that stops progressing — the block was replaced, the server never answered, the
        // player walked off — is abandoned rather than left holding the queue forever.
        if (++idleTicks > STALE_TICKS) {
            for (BreakInfo info : infos) info.abortBreakPacket();
            infos.clear();
            return;
        }

        for (BreakInfo info : new ArrayList<>(infos)) {
            preprocessTool(info);

            BreakConfig config = info.context.getBreakConfig();

            if (config.isRotate()) {
                BlockPos pos = info.getBlockPos();
                Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), 50, () -> resume(info));
            } else {
                resume(info);
            }
        }
    }

    /**
     * The half of a tick that touches the wire, run once the player is looking at the block.
     *
     * <p>Lambda gates the same thing on its own {@code rotated} flag and sends nothing until it is
     * set. Here the gate is the callback itself: reaching this method means the look went out
     * first, which is the only ordering a server that checks your angle will accept.
     */
    private void resume(BreakInfo info) {
        if (MeteorClient.mc.player == null || MeteorClient.mc.world == null) return;

        // The callback outlives the tick that queued it. In between, the module can have been
        // switched off or the server can have taken the block.
        if (!infos.contains(info)) return;

        updateBreakProgress(info);
    }

    /** The hotbar swap: a slot packet, so it neither needs the rotation nor waits for it. */
    private void preprocessTool(BreakInfo info) {
        BreakConfig config = info.context.getBreakConfig();

        if (config.getSwapMode() == BreakConfig.SwapMode.None || info.breaking) return;

        int slot = info.context.getHotbarIndex();

        if (slot >= 0 && slot < 9 && MeteorClient.mc.player.getInventory().getSelectedSlot() != slot) {
            MeteorClient.mc.player.getInventory().setSelectedSlot(slot);
            MeteorClient.mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
    }

    /**
     * The first tick of a break: the opening packets, shaped by the bypass mode.
     *
     * <p>Grim sends a stop before the start. Old Grim adds a start far above the block, which the
     * server counts without acting on. A block that is instant to us but not to vanilla needs a
     * second stop, or the server never hears that we finished.
     */
    private void startBreaking(BreakInfo info) {
        BreakConfig config = info.context.getBreakConfig();

        float delta = info.context.getCachedState()
            .calcBlockBreakingDelta(MeteorClient.mc.player, MeteorClient.mc.world, info.getBlockPos());

        boolean instant = delta >= info.getBreakThreshold();
        info.vanillaInstantBreakable = delta >= 1.0f;

        boolean isGrim = config.getBreakMode() == BreakConfig.BreakMode.Grim;
        boolean oldGrim = config.getBreakMode() == BreakConfig.BreakMode.OldGrim && !info.vanillaInstantBreakable;
        boolean requiresSecondStop = instant && !info.vanillaInstantBreakable;

        if (!instant) {
            info.breaking = true;
            info.breakingTicks = 1;
        }

        if (isGrim) info.stopBreakPacket(0);

        info.startBreakPacket(0);

        if (oldGrim) info.startBreakPacket(BreakInfo.OLD_GRIM_Y_OFFSET);
        if (requiresSecondStop) info.stopBreakPacket(0);

        BreakConfig.SwingMode swing = config.getSwing();
        if (swing.isEnabled() && (swing != BreakConfig.SwingMode.End || instant)) swingHand();

        if (instant) onBlockBreak(info);
    }

    /**
     * Every tick after the first: count, and close the break when the count says it is done.
     *
     * <p>The closing stop is sent here, not at the start. Sending both at once is what makes the
     * server drop the block the instant it is asked, which the anticheat reads for what it is.
     */
    private void updateBreakProgress(BreakInfo info) {
        BreakConfig config = info.context.getBreakConfig();

        if (!info.breaking) {
            startBreaking(info);
            return;
        }

        info.breakingTicks++;

        // Old Grim holds new breaks behind a delay of its own; a burst of starts at the offset
        // position spends that delay without touching the world.
        if (info.type == BreakInfo.BreakType.Primary
            && config.getBreakMode() == BreakConfig.BreakMode.OldGrim
            && !info.bypassedDelay
            && info.breakingTicks > 6) {

            for (int i = 0; i < 22; i++) info.startBreakPacket(BreakInfo.OLD_GRIM_Y_OFFSET);
            info.bypassedDelay = true;
        }

        float delta = info.context.getCachedState()
            .calcBlockBreakingDelta(MeteorClient.mc.player, MeteorClient.mc.world, info.getBlockPos());
        float progress = delta * (info.breakingTicks - config.getFudgeFactor());

        if (progress >= info.getBreakThreshold()) {
            // Old Grim refuses anything quicker than six ticks, however fast the block really is.
            if (info.type == BreakInfo.BreakType.Primary
                && config.getBreakMode() == BreakConfig.BreakMode.OldGrim
                && info.breakingTicks <= 6) {
                return;
            }

            onBlockBreak(info);

            if (info.type == BreakInfo.BreakType.Primary) info.stopBreakPacket(0);

            BreakConfig.SwingMode swing = config.getSwing();
            if (swing.isEnabled() && swing != BreakConfig.SwingMode.Start) swingHand();
        } else if (config.getSwing() == BreakConfig.SwingMode.Constant) {
            swingHand();
        }

        idleTicks = 0;
    }

    private void onBlockBreak(BreakInfo info) {
        BreakConfig config = info.context.getBreakConfig();

        info.broken = true;
        breakDelay = config.getBreakDelay();

        if (config.getBreakMode() == BreakConfig.BreakMode.OldGrim) oldGrimBreakDelay = 6;
    }

    private void swingHand() {
        if (MeteorClient.mc.player != null) MeteorClient.mc.player.swingHand(Hand.MAIN_HAND);
    }
}
