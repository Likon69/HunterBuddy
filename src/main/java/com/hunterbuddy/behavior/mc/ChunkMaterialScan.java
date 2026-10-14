package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.farmer.FarmerProfile;
import com.hunterbuddy.behavior.farmer.FarmerZone;
import com.hunterbuddy.behavior.farmer.MaterialSite;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

public final class ChunkMaterialScan {
    private final WorldChunk chunk;
    private final WorldReader world;
    private final FarmerZone zone;
    private final FarmerProfile profile;
    private final Set<Long> measured = new HashSet<>();
    private int section;
    private int cell;
    private boolean tested;
    private boolean uncertain;
    private PatchMeasurement patch;
    private MaterialSite found;

    public ChunkMaterialScan(WorldChunk chunk, WorldReader world, FarmerZone zone, FarmerProfile profile) {
        this.chunk = chunk;
        this.world = world;
        this.zone = zone;
        this.profile = profile;
        section = chunk.getSectionArray().length - 1;
    }

    public boolean step() {
        if (patch != null) {
            if (!patch.step()) return false;
            if (patch.sufficient()) found = patch.site(System.currentTimeMillis());
            uncertain |= !patch.complete();
            patch = null;
            if (found != null) return true;
        }
        if (world.client().world.getChunkManager().getChunk(chunk.getPos().x, chunk.getPos().z, ChunkStatus.FULL, false) != chunk) {
            uncertain = true;
            return true;
        }
        ChunkSection[] sections = chunk.getSectionArray();
        if (section < 0) return true;
        if (!tested) {
            tested = true;
            if (!sections[section].hasAny(state -> profile.blocks().contains(state.getBlock()))) cell = 4096;
        }
        if (cell >= 4096) {
            section--;
            cell = 0;
            tested = false;
            return section < 0;
        }
        int index = cell++;
        int x = chunk.getPos().getStartX() + (index & 15);
        int z = chunk.getPos().getStartZ() + ((index >> 4) & 15);
        int y = world.bottomY() + section * 16 + (15 - (index >> 8));
        BlockPos point = new BlockPos(x, y, z);
        if (!zone.contains(x + 0.5, z + 0.5) || measured.contains(point.asLong())
            || !profile.blocks().contains(world.state(point).getBlock())) return false;
        boolean exposed = PatchMeasurement.exposed(point, world);
        if (!exposed && profile.strategy().equals("layer")) return false;
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = point.offset(direction);
            if (world.inBounds(neighbor) && zone.contains(neighbor.getX() + 0.5, neighbor.getZ() + 0.5)
                && !world.loaded(neighbor)) uncertain = true;
        }
        if (world.hardness(point) >= 0 && exposed && !MineGeometry.lavaAdjacent(world, point)) {
            patch = new PatchMeasurement(point, world, zone, profile, measured);
        }
        return false;
    }

    public MaterialSite found() {
        return found;
    }

    public boolean verified() {
        return !uncertain && found == null;
    }
}
