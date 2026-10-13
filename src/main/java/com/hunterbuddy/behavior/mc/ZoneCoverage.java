package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.farmer.CoverageState;
import com.hunterbuddy.behavior.farmer.FarmerZone;
import com.hunterbuddy.behavior.farmer.ZoneKnowledge;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

public final class ZoneCoverage {
    private final FarmerZone zone;
    private final ZoneKnowledge memory;
    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;
    private int x;
    private int z;
    private boolean initialized;
    private final List<ChunkPos> chunks = new ArrayList<>();

    public ZoneCoverage(FarmerZone zone, ZoneKnowledge memory) {
        this.zone = zone;
        this.memory = memory;
        minX = zone.corners().stream().mapToInt(BlockPos::getX).min().orElseThrow() >> 4;
        maxX = zone.corners().stream().mapToInt(BlockPos::getX).max().orElseThrow() >> 4;
        minZ = zone.corners().stream().mapToInt(BlockPos::getZ).min().orElseThrow() >> 4;
        maxZ = zone.corners().stream().mapToInt(BlockPos::getZ).max().orElseThrow() >> 4;
        x = minX;
        z = minZ;
    }

    public boolean initialize() {
        for (int work = 0; work < 512 && !initialized; work++) {
            if (intersects(x, z)) chunks.add(new ChunkPos(x, z));
            z++;
            if (z > maxZ) {
                z = minZ;
                x++;
            }
            initialized = x > maxX;
        }
        return initialized;
    }

    public List<ChunkPos> chunks() {
        return chunks;
    }

    public boolean intersects(int cx, int cz) {
        double left = cx * 16.0;
        double right = left + 16;
        double top = cz * 16.0;
        double bottom = top + 16;
        if (zone.corners().stream().allMatch(p -> p.getX() < left)
            || zone.corners().stream().allMatch(p -> p.getX() > right)) return false;
        if (zone.corners().stream().allMatch(p -> p.getZ() < top)
            || zone.corners().stream().allMatch(p -> p.getZ() > bottom)) return false;
        for (int i = 0; i < zone.corners().size(); i++) {
            BlockPos a = zone.corners().get(i);
            BlockPos b = zone.corners().get((i + 1) % zone.corners().size());
            double dx = b.getX() - a.getX();
            double dz = b.getZ() - a.getZ();
            if (cross(a, dx, dz, left, top) < 0 && cross(a, dx, dz, right, top) < 0
                && cross(a, dx, dz, left, bottom) < 0 && cross(a, dx, dz, right, bottom) < 0) return false;
        }
        return true;
    }

    private double cross(BlockPos a, double dx, double dz, double px, double pz) {
        return dx * (pz - a.getZ()) - dz * (px - a.getX());
    }

    public boolean complete() {
        return initialized && chunks.stream().allMatch(p -> memory.state(key(p.x, p.z)) == CoverageState.VERIFIED);
    }

    public long verified() {
        return chunks.stream().filter(p -> memory.state(key(p.x, p.z)) == CoverageState.VERIFIED).count();
    }

    public static String key(int x, int z) {
        return x + "," + z;
    }
}
