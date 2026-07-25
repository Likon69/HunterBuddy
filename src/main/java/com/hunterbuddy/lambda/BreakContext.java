/*
 * Port of com.lambda.interaction.construction.simulation.context.BreakContext
 * (lambda 1.21.11) — direct 1:1 translation to Java.
 */
package com.hunterbuddy.lambda;

import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class BreakContext extends BuildContext implements Automated {
    private final BlockHitResult hitResult;
    private final RotationRequest rotationRequest;
    private final int hotbarIndex;
    private final StackSelection itemSelection;
    private final boolean instantBreak;
    private final boolean insideBlock;
    private BlockState cachedState;
    private final Automated automated;

    private final int baseColor = 0x19DE0000;  // ARGB 222,0,0,25
    private final int sideColor = 0x64DE0000;  // ARGB 222,0,0,100

    public BreakContext(BlockHitResult hitResult, RotationRequest rotationRequest,
                       int hotbarIndex, StackSelection itemSelection,
                       boolean instantBreak, boolean insideBlock,
                       BlockState cachedState, Automated automated) {
        this.hitResult = hitResult;
        this.rotationRequest = rotationRequest;
        this.hotbarIndex = hotbarIndex;
        this.itemSelection = itemSelection;
        this.instantBreak = instantBreak;
        this.insideBlock = insideBlock;
        this.cachedState = cachedState;
        this.automated = automated;
        this.sortDistance = computeSortDistance();
    }

    @Override public BlockHitResult getHitResult() { return hitResult; }
    @Override public RotationRequest getRotationRequest() { return rotationRequest; }
    @Override public int getHotbarIndex() { return hotbarIndex; }
    public StackSelection getItemSelection() { return itemSelection; }
    public boolean isInstantBreak() { return instantBreak; }
    public boolean isInsideBlock() { return insideBlock; }
    @Override public BlockState getCachedState() { return cachedState; }
    public void setCachedState(BlockState s) { this.cachedState = s; }
    @Override public BlockState getExpectedState() { return BlockUtils.emptyState(cachedState); }
    @Override public BlockPos getBlockPos() { return hitResult.getBlockPos(); }
    @Override public SortMode getSorter() {
        return automated.getBreakConfig().getSorter();
    }

    // Automated delegation — full type fidelity
    @Override public BuildConfig getBuildConfig() { return automated.getBuildConfig(); }
    @Override public BreakConfig getBreakConfig() { return automated.getBreakConfig(); }
    @Override public Object getInteractConfig() { return automated.getInteractConfig(); }
    @Override public Object getRotationConfig() { return automated.getRotationConfig(); }
    @Override public Object getInventoryConfig() { return automated.getInventoryConfig(); }
    @Override public Object getHotbarConfig() { return automated.getHotbarConfig(); }
    @Override public Object getEatConfig() { return automated.getEatConfig(); }

    @Override
    public void render(Renderer3D renderer) {
        renderer.box(
            hitResult.getBlockPos(),
            new Color(baseColor),
            new Color(sideColor),
            ShapeMode.Both,
            0
        );
    }

    @Override
    public boolean canUse() {
        return automated.getBuildConfig().isBreakBlocks();
    }

    private double computeSortDistance() {
        var mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null || hitResult == null) {
            return Double.MAX_VALUE;
        }
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d v = hitResult.getPos();
        double d = v.x - eyePos.x;
        double e = v.y - eyePos.y;
        double f = v.z - eyePos.z;
        return Math.sqrt(d * d + e * e + f * f);
    }
}
