package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.CoverageState;
import com.hunterbuddy.behavior.farmer.FarmerProfile;
import com.hunterbuddy.behavior.farmer.FarmerZone;
import com.hunterbuddy.behavior.farmer.MaterialSite;
import com.hunterbuddy.behavior.farmer.WorldKnowledge;
import com.hunterbuddy.behavior.farmer.ZoneKnowledge;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

public final class SurveyScanner {
    private final WorldReader world;
    private final FarmerZone zone;
    private final FarmerProfile profile;
    private final ZoneCoverage coverage;
    private final WorldKnowledge knowledge;
    private final ZoneKnowledge memory;
    private final ArrayDeque<ChunkPos> pending = new ArrayDeque<>();
    private final Set<String> queued = new HashSet<>();
    private final Set<String> attempted = new HashSet<>();
    private ChunkMaterialScan scan;
    private ChunkPos current;
    private long refreshedAt = -20;
    private long savedAt;
    private boolean dirty;

    public SurveyScanner(WorldReader world, FarmerZone zone, FarmerProfile profile, ZoneCoverage coverage,
                         WorldKnowledge knowledge, ZoneKnowledge memory) {
        this.world = world;
        this.zone = zone;
        this.profile = profile;
        this.coverage = coverage;
        this.knowledge = knowledge;
        this.memory = memory;
    }

    public void tick(TickContext ctx) {
        if (ctx.tick() - refreshedAt >= 20) refresh(ctx);
        long deadline = System.nanoTime() + 2_000_000;
        for (int work = 0; work < 8192 && System.nanoTime() < deadline; work++) {
            if (scan == null && !next()) break;
            if (!scan.step()) continue;
            finish(ctx);
            if (SiteSelection.best(memory, zone, world.feet(), System.currentTimeMillis()) != null) break;
        }
        if (dirty && ctx.tick() - savedAt >= 1200) save(ctx);
    }

    private void refresh(TickContext ctx) {
        List<WorldChunk> loaded = LoadedChunks.read(world);
        for (WorldChunk chunk : loaded) {
            ChunkPos position = chunk.getPos();
            if (!coverage.intersects(position.x, position.z)) continue;
            String key = ZoneCoverage.key(position.x, position.z);
            if (memory.state(key) == CoverageState.UNKNOWN) {
                coverage.update(position.x, position.z, CoverageState.OBSERVED);
                dirty = true;
            }
            if (memory.state(key) != CoverageState.VERIFIED && !attempted.contains(key) && queued.add(key)) pending.add(position);
        }
        refreshedAt = ctx.tick();
    }

    private boolean next() {
        while (!pending.isEmpty()) {
            current = pending.removeFirst();
            String key = ZoneCoverage.key(current.x, current.z);
            queued.remove(key);
            WorldChunk chunk = world.client().world.getChunkManager().getChunk(current.x, current.z, ChunkStatus.FULL, false);
            if (chunk == null) continue;
            attempted.add(key);
            scan = new ChunkMaterialScan(chunk, world, zone, profile);
            return true;
        }
        return false;
    }

    private void finish(TickContext ctx) {
        MaterialSite site = scan.found();
        if (site != null) {
            String key = Long.toString(site.seed().asLong());
            MaterialSite previous = memory.sites.get(key);
            if (previous != null) {
                site.flightAvoidedUntil = previous.flightAvoidedUntil;
                site.shelterId = previous.shelterId;
                site.started = previous.started;
            }
            if (previous == null || !previous.status.equals("inaccessible")) memory.sites.put(key, site);
        }
        CoverageState state = scan.verified() ? CoverageState.VERIFIED : CoverageState.OBSERVED;
        coverage.update(current.x, current.z, state);
        dirty = true;
        if (scan.verified()) ctx.progress("zone chunk verified " + current.x + "," + current.z);
        scan = null;
        current = null;
        if (site != null) save(ctx);
    }

    public boolean idle() {
        return scan == null && pending.isEmpty();
    }

    public void rescan() {
        attempted.clear();
        refreshedAt = -20;
    }

    public void save(TickContext ctx) {
        if (dirty) knowledge.save(ctx);
        dirty = false;
        savedAt = ctx.tick();
    }

    public void resumed() {
        scan = null;
        current = null;
        pending.clear();
        queued.clear();
        rescan();
    }
}
