/*
 * Stands in for lambda's BuildSimulator: turns a block position into the BreakContext the
 * breaking engine consumes.
 */
package com.hunterbuddy.lambda;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Builds the description of a break: which block, which face, which tool.
 *
 * <p>The face matters more than it looks. Every packet about a block carries one, and the server
 * compares it against where the player is; a face picked by convention rather than from geometry
 * is a break claimed from an impossible angle.
 */
public final class BreakContextFactory {
    private BreakContextFactory() {
    }

    public static BreakContext create(BlockPos pos, Automated automated) {
        if (MeteorClient.mc.world == null || MeteorClient.mc.player == null) return null;

        BlockPos immutable = pos.toImmutable();
        BlockState state = MeteorClient.mc.world.getBlockState(immutable);
        if (state.isAir()) return null;

        Direction side = BlockUtils.getDirection(immutable);

        // Aimed at the middle of the face that was chosen, not the middle of the block: it is the
        // point a player looking at that face would actually hit.
        Vec3d hit = Vec3d.ofCenter(immutable).add(
            side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);

        BlockHitResult hitResult = new BlockHitResult(hit, side, immutable, false);

        FindItemResult tool = InvUtils.findFastestTool(state);
        int hotbarIndex = tool.found() && tool.isHotbar()
            ? tool.slot()
            : MeteorClient.mc.player.getInventory().getSelectedSlot();

        boolean instantBreak = BlockUtils.canInstaBreak(immutable);
        boolean insideBlock = MeteorClient.mc.player.getBlockPos().equals(immutable);

        return new BreakContext(hitResult, new RotationRequest(), hotbarIndex, null,
            instantBreak, insideBlock, state, automated);
    }

    /**
     * The smallest thing that satisfies {@link Automated}: a holder for the two configs the
     * breaking path actually reads. The rest of lambda's automation surface is not ported.
     */
    public static final class Configs implements Automated {
        private final BuildConfig buildConfig = new BuildConfig();
        private final BreakConfig breakConfig;

        public Configs(BreakConfig breakConfig) {
            this.breakConfig = breakConfig;
        }

        @Override public BuildConfig getBuildConfig() { return buildConfig; }
        @Override public BreakConfig getBreakConfig() { return breakConfig; }
        @Override public Object getInteractConfig() { return null; }
        @Override public Object getRotationConfig() { return null; }
        @Override public Object getInventoryConfig() { return null; }
        @Override public Object getHotbarConfig() { return null; }
        @Override public Object getEatConfig() { return null; }
    }
}
