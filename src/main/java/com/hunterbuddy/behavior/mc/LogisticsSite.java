package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.farmer.FarmerZone;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.block.FallingBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

public final class LogisticsSite {
    private LogisticsSite() {}
    public static boolean inside(BlockPos center, FarmerZone zone) {
        return List.of(center.add(-2, 0, -2), center.add(-2, 0, 2), center.add(2, 0, -2), center.add(2, 0, 2), center.north(3)).stream()
            .allMatch(pos -> zone.containsWithMargin(pos.getX() + 0.5, pos.getZ() + 0.5, 3));
    }

    public static boolean dry(BlockPos center, WorldReader world) {
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            BlockPos pos = center.add(x, 0, z);
            if (!world.loaded(pos) || !world.inBounds(pos.up(2)) || !world.state(pos.down()).isFullCube(world.client().world, pos.down())
                || world.state(pos.down()).hasBlockEntity()
                || world.hardness(pos.down()) < 0
                || !world.state(pos.down()).getFluidState().isEmpty() || world.state(pos.down()).getBlock() instanceof FallingBlock)
                    return false;
            for (int y = 0; y <= 2; y++) if (!world.state(pos.up(y)).isAir()) return false;
        }
        return true;
    }

    public static BlockPos choose(WorldReader world, FarmerZone zone, int attempt) {
        BlockPos feet = world.feet();
        if (attempt == 0 && inside(feet, zone) && dry(feet, world)) return feet;
        for (int radius = 0; radius <= 16; radius++) for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
            BlockPos point = world.surface(feet.getX() + x, feet.getZ() + z);
            if (point != null && inside(point, zone) && dry(point, world) && attempt-- <= 0) return point;
        }
        List<BlockPos> corners = zone.corners();
        int cx = (int) Math.round(corners.stream().mapToInt(BlockPos::getX).average().orElseThrow());
        int cz = (int) Math.round(corners.stream().mapToInt(BlockPos::getZ).average().orElseThrow());
        for (int radius = 0; radius <= 64; radius++) for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
            BlockPos point = new BlockPos(cx + x, feet.getY(), cz + z);
            if (!inside(point, zone)) continue;
            if (attempt-- > 0) continue;
            if (world.loaded(point)) {
                int height = Math.max(world.bottomY() + 1, Math.min(world.topY() - 2,
                    world.client().world.getTopY(Heightmap.Type.MOTION_BLOCKING, point.getX(), point.getZ())));
                point = new BlockPos(point.getX(), height, point.getZ());
                if (world.hardness(point.down()) < 0 || world.state(point.down()).getBlock() instanceof FallingBlock ||
                    !world.state(point.down()).isFullCube(world.client().world, point.down())
                    && world.state(point.down()).getFluidState().isEmpty()) point = point.up();
                boolean containerFloor = false;
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) if (world.loaded(point.add(dx, -1, dz)) &&
                    world.state(point.add(dx, -1, dz)).hasBlockEntity()) containerFloor = true;
                if (containerFloor) continue;
            }
            return point;
        }
        return null;
    }

    public static List<BlockPos> shell(BlockPos center) {
        List<BlockPos> positions = new ArrayList<>();
        positions.add(center.down());
        List<BlockPos> floor = new ArrayList<>();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) if ((x != 0 || z != 0) && !(x == 0 && (z == -1 || z == -2)))
            floor.add(center.add(x, -1, z));
        floor.sort(Comparator.comparingInt(pos -> Math.abs(pos.getX() - center.getX()) + Math.abs(pos.getZ() - center.getZ())));
        positions.addAll(floor);
        for (int y = 0; y <= 1; y++) {
            List<BlockPos> ring = new ArrayList<>();
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
                if (Math.max(Math.abs(x), Math.abs(z)) == 2) ring.add(center.add(x, y, z));
            ring.sort(Comparator.comparingInt((BlockPos pos) -> Math.abs(pos.getX() - center.getX()) + Math.abs(pos.getZ() -
                center.getZ())).reversed());
            positions.addAll(ring);
        }
        return List.copyOf(positions);
    }
}
