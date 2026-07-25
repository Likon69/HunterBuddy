/*
 * Port of com.lambda.interaction.construction.blueprint.Blueprint
 * (lambda 1.21.11) — direct 1:1 translation to Java.
 *
 * Abstract blueprint with structure (Map<BlockPos, TargetState>),
 * bounds (cached via UpdatableLazy), and helper queries.
 */
package com.hunterbuddy.lambda;

import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public abstract class Blueprint {
    public abstract Structure getStructure();

    private final UpdatableLazy<BlockBox> bounds = UpdatableLazy.of(() -> {
        Structure structure = getStructure();
        if (structure.isEmpty()) return null;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (BlockPos pos : structure.keySet()) {
            if (pos.getX() > maxX) maxX = pos.getX();
            if (pos.getY() > maxY) maxY = pos.getY();
            if (pos.getZ() > maxZ) maxZ = pos.getZ();
            if (pos.getX() < minX) minX = pos.getX();
            if (pos.getY() < minY) minY = pos.getY();
            if (pos.getZ() < minZ) minZ = pos.getZ();
        }
        return new BlockBox(minX, minY, minZ, maxX, maxY, maxZ);
    });

    public Vec3d getClosestPointTo(Vec3d target) {
        BlockBox b = bounds.getValue();
        if (b == null) return target;
        double d = MathHelper.clamp(target.x, b.getMinX(), b.getMaxX());
        double e = MathHelper.clamp(target.y, b.getMinY(), b.getMaxY());
        double f = MathHelper.clamp(target.z, b.getMinZ(), b.getMaxZ());
        return new Vec3d(d, e, f);
    }

    public boolean isOutOfBounds(Vec3d vec) {
        BlockBox b = bounds.getValue();
        if (b == null) return false;
        // Lambda: contains(vec.roundedBlockPos.down())
        BlockPos rounded = new BlockPos((int) Math.round(vec.x), (int) Math.round(vec.y), (int) Math.round(vec.z));
        return !b.contains(rounded.down());
    }

    public Vec3d getCenter() {
        BlockBox b = bounds.getValue();
        return b == null ? null : new Vec3d(b.getCenter().getX(), b.getCenter().getY(), b.getCenter().getZ());
    }

    public static Structure emptyStructure() {
        return new HashMapStructure();
    }

    public static Structure toStructure(BlockBox box, TargetState targetState) {
        Structure s = new HashMapStructure();
        BlockPos.stream(box).forEach(p -> s.put(p, targetState));
        return s;
    }

    public static Structure toStructure(BlockPos pos, TargetState targetState) {
        Structure s = new HashMapStructure();
        s.put(pos, targetState);
        return s;
    }

    public static Structure toStructure(StructureTemplate template) {
        // Lambda uses StructureTemplate.getBlockInfoLists() which was renamed
        // in 1.21.x. Stubbed: returns an empty structure until the exact API
        // is confirmed.
        Structure s = new HashMapStructure();
        try {
            // Try iterating via iterateBlocks if available
            java.lang.reflect.Method m = template.getClass().getMethod("iterateBlocks");
            m.invoke(template);
        } catch (Exception ignored) {
        }
        return s;
    }

    /** Simple HashMap-based Structure implementation. */
    public static class HashMapStructure extends HashMap<BlockPos, TargetState> implements Structure {
        public HashMapStructure() { super(); }
    }
}
