package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.farmer.FarmerProfile;
import com.hunterbuddy.behavior.farmer.FarmerZone;
import com.hunterbuddy.behavior.farmer.FarmerZoneLookup;
import com.hunterbuddy.behavior.farmer.MaterialSite;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class PatchMeasurement {
    private final WorldReader world;
    private final FarmerZone zone;
    private final FarmerProfile profile;
    private final Set<Long> measured;
    private final Set<Long> queued = new HashSet<>();
    private final Set<Long> members = new LinkedHashSet<>();
    private final ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
    private BlockPos entry;
    private int count;
    private boolean unloaded;

    public PatchMeasurement(BlockPos seed, WorldReader world, FarmerZone zone, FarmerProfile profile, Set<Long> measured) {
        this.world = world;
        this.zone = zone;
        this.profile = profile;
        this.measured = measured;
        frontier.add(seed.toImmutable());
        queued.add(seed.asLong());
    }

    public boolean step() {
        BlockPos point = frontier.pollFirst();
        if (point == null || sufficient()) return true;
        if (!inside(point)) return done();
        if (!world.loaded(point)) {
            unloaded = true;
            return done();
        }
        if (!matches(point) || !measured.add(point.asLong())) return done();
        count++;
        members.add(point.asLong());
        if (entry == null && zone.containsWithMargin(point.getX() + 0.5, point.getZ() + 0.5, FarmerZoneLookup.TARGET_MARGIN)
            && exposed(point, world)) entry = point;
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = point.offset(direction);
            if (inside(neighbor) && !measured.contains(neighbor.asLong()) && queued.add(neighbor.asLong())) frontier.add(neighbor);
        }
        return done();
    }

    private boolean inside(BlockPos point) {
        return world.inBounds(point) && zone.contains(point.getX() + 0.5, point.getZ() + 0.5);
    }

    public boolean matches(BlockPos point) {
        return profile.blocks().contains(world.state(point).getBlock()) && world.hardness(point) >= 0;
    }

    public boolean sufficient() {
        return count >= MaterialSite.SHULKER_BLOCKS && entry != null;
    }

    public boolean done() {
        return frontier.isEmpty() || sufficient();
    }

    public boolean complete() {
        return frontier.isEmpty() && !unloaded;
    }

    public int count() {
        return count;
    }

    public MaterialSite site(long now) {
        if (!sufficient()) throw new IllegalStateException("a material site needs 1728 connected accessible blocks");
        MaterialSite result = new MaterialSite(entry, count, !complete(), now);
        result.blocks = blocks();
        return result;
    }

    public List<Long> blocks() {
        return new ArrayList<>(members);
    }

    public static boolean exposed(BlockPos point, WorldReader world) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = point.offset(direction);
            if (world.inBounds(neighbor) && world.loaded(neighbor) && world.cleared(neighbor)) return true;
        }
        return false;
    }
}
