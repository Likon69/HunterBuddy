package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.core.Sensors;
import com.hunterbuddy.behavior.resource.LookIntent;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

public final class FlightTerrain {
    private FlightTerrain() {}

    public static boolean landing(BlockPos point, WorldReader world) {
        if (point == null) return false;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos floor = point.add(x, -1, z);
                if (!world.loaded(floor) || !world.inBounds(floor.up(3))
                    || !world.state(floor).isFullCube(world.client().world, floor)
                    || !world.state(floor).getFluidState().isEmpty() || world.state(floor).isOf(Blocks.MAGMA_BLOCK)) return false;
                for (int y = 1; y <= 3; y++) if (!world.state(floor.up(y)).isAir()) return false;
            }
        }
        return true;
    }

    public static boolean launch(BlockPos point, WorldReader world) {
        if (!landing(point, world)) return false;
        for (int y = 1; y <= 6; y++) {
            BlockPos head = point.up(y);
            if (!world.inBounds(head) || !world.loaded(head) || !world.state(head).isAir()) return false;
        }
        return true;
    }

    public static double clearance(FlightPlan plan, Sensors sensors, float yaw) {
        double altitude = plan.altitude;
        if (plan.world.client().world.getDimension().hasCeiling() && sensors.y() < 128) return altitude;
        double angle = Math.toRadians(yaw);
        for (int distance = 8; distance <= 96; distance += 8) {
            for (int side = -3; side <= 3; side += 3) {
                int x = (int) Math.floor(sensors.x() - Math.sin(angle) * distance + Math.cos(angle) * side);
                int z = (int) Math.floor(sensors.z() + Math.cos(angle) * distance + Math.sin(angle) * side);
                BlockPos column = new BlockPos(x, (int) sensors.y(), z);
                if (!plan.world.loaded(column)) return altitude;
                int top = plan.world.client().world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
                altitude = Math.max(altitude, top + 24);
            }
        }
        return Math.min(plan.world.topY() + 48, altitude);
    }

    public static boolean corridor(WorldReader world, Sensors sensors, float yaw, double distance) {
        double angle = Math.toRadians(yaw);
        for (double step = 2; step <= distance; step += 2) {
            for (int side = -2; side <= 2; side += 2) {
                for (int height = -1; height <= 2; height++) {
                    BlockPos pos = BlockPos.ofFloored(sensors.x() - Math.sin(angle) * step + Math.cos(angle) * side,
                        sensors.y() + height, sensors.z() + Math.cos(angle) * step + Math.sin(angle) * side);
                    if (!world.loaded(pos)) return false;
                    if (pos.getY() > world.topY()) continue;
                    if (!world.inBounds(pos) || !world.state(pos).getCollisionShape(world.client().world, pos).isEmpty()) return false;
                }
            }
        }
        return true;
    }

    public static float heading(WorldReader world, Sensors sensors, float desired) {
        double ahead = Math.max(16, Math.hypot(sensors.velocityX(), sensors.velocityZ()) * 24);
        for (float offset : new float[]{0, 35, -35, 70, -70, 120, -120, 180}) {
            float candidate = desired + offset;
            if (corridor(world, sensors, candidate, ahead)) return candidate;
        }
        return desired + 180;
    }

    public static LookIntent rotation(Sensors sensors, float yaw, float pitch) {
        float turn = Math.clamp(Conditions.wrap(yaw - sensors.yaw()), -12, 12);
        float rate = pitch < sensors.pitch() ? 5.45f : 0.90f;
        float tilt = Math.clamp(pitch - sensors.pitch(), -rate, rate);
        return new LookIntent(sensors.yaw() + turn, Math.clamp(sensors.pitch() + tilt, -75, 65));
    }
}
