package com.hunterbuddy.behavior.mc;

import java.util.Comparator;
import java.util.List;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class SurfaceMineSelection {
    private SurfaceMineSelection() {}

    public static boolean choose(MineSiteContext mine) {
        mine.patch.removeIf(point -> mine.world.loaded(point) && !mine.material(point));
        List<BlockPos> points = mine.patch.stream().filter(mine::material)
            .filter(point -> !mine.facts.supports.contains(point.asLong()))
            .sorted(Comparator.<BlockPos>comparingInt(BlockPos::getY).reversed()
                .thenComparingDouble(point -> point.getSquaredDistance(mine.world.feet()))).toList();
        if (points.isEmpty()) return false;
        int highest = points.getFirst().getY();
        for (BlockPos point : points) {
            if (point.getY() != highest) break;
            if (mine.rejected.contains(point)) continue;
            if (!MineGeometry.clear(mine.world, point.up()) || MineGeometry.fallingAbove(mine.world, point)
                || !MineGeometry.floor(mine.world, point.down())) continue;
            BlockPos stand = stand(mine, point);
            if (stand == null) continue;
            mine.target = point;
            mine.stand = stand;
            return true;
        }
        return false;
    }

    private static BlockPos stand(MineSiteContext mine, BlockPos point) {
        BlockPos anchor = BlockPos.fromLong(mine.facts.stairs.getLast());
        BlockPos best = null;
        double distance = Double.POSITIVE_INFINITY;
        for (Direction direction : Direction.Type.HORIZONTAL) {
            for (int height = 0; height <= 1; height++) {
                BlockPos feet = point.offset(direction).up(height);
                if (!mine.inside(feet) || !MineGeometry.standing(mine.world, feet)) continue;
                if (feet.getY() < anchor.getY() && (feet.getY() != anchor.getY() - 1
                    || Math.abs(feet.getX() - anchor.getX()) + Math.abs(feet.getZ() - anchor.getZ()) != 1)) continue;
                double score = feet.getSquaredDistance(mine.world.feet());
                if (score < distance) {
                    distance = score;
                    best = feet;
                }
            }
        }
        return best;
    }
}
