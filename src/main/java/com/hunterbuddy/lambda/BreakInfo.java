/*
 * Port of com.lambda.interaction.managers.breaking.BreakInfo (lambda 1.21.11).
 */
package com.hunterbuddy.lambda;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;

/**
 * One block being broken, and the packets that say so.
 *
 * <p>The state here is what separates a break that lands from one the server ignores: how many
 * ticks it has been going, whether the opening packets have gone out, and — for the old Grim
 * route — whether the delay has been bypassed yet.
 */
public class BreakInfo {
    /**
     * The vertical offset the old Grim bypass sends its extra starts at.
     *
     * <p>A position that far above the block is outside anything the server will accept as a real
     * break, which is the point: the packets are counted without changing the world, and the
     * block's own countdown is what finishes it.
     */
    public static final int OLD_GRIM_Y_OFFSET = 955;

    public enum BreakType {
        Primary,
        Secondary
    }

    public final BreakContext context;
    public final BreakType type;

    /** Set once the opening packets have gone out and the block is counting down. */
    public boolean breaking;

    /** Ticks since breaking started, which is what the progress is measured against. */
    public int breakingTicks;

    /** Whether vanilla itself would drop this block in one hit. */
    public boolean vanillaInstantBreakable;

    /** Old Grim only: whether the burst that skips the server's delay has been sent. */
    public boolean bypassedDelay;

    public boolean broken;

    public BreakInfo(BreakContext context, BreakType type) {
        this.context = context;
        this.type = type;
        this.bypassedDelay = context.getBreakConfig().getBreakMode() != BreakConfig.BreakMode.OldGrim;
    }

    public BlockPos getBlockPos() {
        return context.getBlockPos();
    }

    /**
     * How much progress counts as broken.
     *
     * <p>Only the primary break is allowed to finish early; a secondary one has to be genuinely
     * complete, or the pair desynchronise and the server keeps the block.
     */
    public float getBreakThreshold() {
        return type == BreakType.Primary ? context.getBreakConfig().getBreakThreshold() : 1.0f;
    }

    public void startBreakPacket(int yAdd) {
        sendBreak(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, yAdd);
    }

    public void stopBreakPacket(int yAdd) {
        sendBreak(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, yAdd);
    }

    public void abortBreakPacket() {
        sendBreak(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, 0);
    }

    /**
     * Sends one action, sequenced, against the face the context was built from.
     *
     * <p>Sequenced because the server issues an id per action and answers with it; a client that
     * never quotes one is not answering the same conversation. The face comes from the hit result
     * rather than being assumed, so every packet about this block agrees with every other.
     */
    private void sendBreak(PlayerActionC2SPacket.Action action, int yAdd) {
        if (MeteorClient.mc.interactionManager == null || MeteorClient.mc.world == null) return;

        BlockPos p = context.getBlockPos();
        BlockPos target = new BlockPos(p.getX(), p.getY() + yAdd, p.getZ());

        MeteorClient.mc.interactionManager.sendSequencedPacket(MeteorClient.mc.world,
            sequence -> new PlayerActionC2SPacket(action, target, context.getHitResult().getSide(), sequence));
    }
}
