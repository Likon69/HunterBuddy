/*
 * Port of com.lambda.interaction.construction.simulation.context.BuildContext
 * (lambda 1.21.11) — direct 1:1 translation to Java.
 *
 * `sortDistance` is computed once via lazy initialization (lambda uses
 * `by lazy`). `expectedState` is the empty (fluid) state of the cachedState.
 */
package com.hunterbuddy.lambda;

import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public abstract class BuildContext implements Drawable {
    public abstract BlockHitResult getHitResult();
    public abstract Object getRotationRequest();
    public abstract int getHotbarIndex();
    public abstract BlockState getCachedState();
    public abstract BlockState getExpectedState();
    public abstract BlockPos getBlockPos();
    public abstract SortMode getSorter();

    private final double random = Math.random();

    /** Mirrors `val sortDistance by lazy` — computed once on first access. */
    protected double sortDistance;
    protected boolean sortDistanceComputed = false;

    public double getSortDistance() {
        if (!sortDistanceComputed) {
            sortDistance = computeSortDistance();
            sortDistanceComputed = true;
        }
        return sortDistance;
    }

    private double computeSortDistance() {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null || getHitResult() == null) {
            return Double.MAX_VALUE;
        }
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d v = getHitResult().getPos();
        double d = v.x - eyePos.x;
        double e = v.y - eyePos.y;
        double f = v.z - eyePos.z;
        return Math.sqrt(d * d + e * e + f * f);
    }

    public abstract boolean canUse();
}
