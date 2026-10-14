package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.EvidenceKind;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.ProgressEvidence;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.ChunkObservation;
import com.hunterbuddy.behavior.farmer.CoverageState;
import com.hunterbuddy.behavior.farmer.FarmerProfile;
import com.hunterbuddy.behavior.farmer.FarmerZone;
import com.hunterbuddy.behavior.farmer.FarmerZoneLookup;
import com.hunterbuddy.behavior.farmer.MaterialSite;
import com.hunterbuddy.behavior.farmer.WorldKnowledge;
import com.hunterbuddy.behavior.farmer.ZoneKnowledge;
import com.hunterbuddy.behavior.mc.PatchMeasurement;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.mc.ZoneCoverage;
import java.util.HashSet;
import java.util.function.Supplier;
import net.minecraft.util.math.BlockPos;

public final class VerifySite extends Action {
    private final WorldReader world;
    private final FarmerZone zone;
    private final FarmerProfile profile;
    private final WorldKnowledge knowledge;
    private final ZoneKnowledge memory;
    private final Supplier<MaterialSite> selected;
    private final Key<BlockPos> destination;
    private final Supplier<BlockPos> access;
    private int airborneTicks;
    private MaterialSite site;
    private PatchMeasurement measurement;
    private int reported;

    public VerifySite(WorldReader world, FarmerZone zone, FarmerProfile profile, WorldKnowledge knowledge,
                      ZoneKnowledge memory, Supplier<MaterialSite> selected, Supplier<BlockPos> access, Key<BlockPos> destination) {
        super("verify-connected-material-on-site-before-mining");
        this.world = world;
        this.zone = zone;
        this.profile = profile;
        this.knowledge = knowledge;
        this.memory = memory;
        this.selected = selected;
        this.destination = destination;
        this.access = access;
        limit(2400);
    }

    @Override
    protected void onActionStart(TickContext ctx) {
        site = selected.get();
        measurement = null;
        reported = 0;
        airborneTicks = 0;
        ctx.bb().remove(ctx.owner(), destination);
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        if (site == null) return fail(FailureCode.NO_OPTION, "no measured material site selected");
        BlockPos seed = site.seed();
        if (ctx.sensors().gliding() || !ctx.sensors().onGround()) {
            return airborneTicks++ < 200 ? new Running("waiting for ground support before site verification")
                : fail(FailureCode.NOT_CONFIRMED, "site verification needs another grounded arrival attempt");
        }
        airborneTicks = 0;
        BlockPos support = access.get();
        if (!zone.contains(ctx.sensors().x(), ctx.sensors().z())
            || support == null || world.feet().getSquaredDistance(support) > 256
            || !zone.containsWithMargin(seed.getX() + 0.5, seed.getZ() + 0.5, FarmerZoneLookup.TARGET_MARGIN)) {
            return unavailable(ctx, "inaccessible", "site must be checked on the ground inside the current zone");
        }
        if (!world.loaded(seed)) return unavailable(ctx, "inaccessible", "site chunk is not loaded on arrival");
        if (!profile.blocks().contains(world.state(seed).getBlock()) || !PatchMeasurement.exposed(seed, world)) {
            return unavailable(ctx, "inaccessible", "remembered exposed access block disappeared or is obstructed");
        }
        if (measurement == null) measurement = new PatchMeasurement(seed, world, zone, profile, new HashSet<>());
        long deadline = System.nanoTime() + 2_000_000;
        for (int work = 0; work < 8192 && System.nanoTime() < deadline; work++) {
            if (measurement.step()) break;
        }
        int count = measurement.count();
        if (count > reported) {
            ctx.progress(new ProgressEvidence(EvidenceKind.MILESTONE, count - reported, "connected site blocks checked on arrival"));
            reported = count;
        }
        if (!measurement.done()) return new Running("site verification blocks=" + count);
        if (!measurement.sufficient()) {
            site.quantity = count;
            String status = measurement.complete() ? "exhausted" : "inaccessible";
            String reason = measurement.complete() ? "only " + count + " connected accessible blocks remain"
                : "1728 connected blocks cannot be confirmed in loaded chunks";
            return unavailable(ctx, status, reason);
        }
        site.quantity = count;
        site.lowerBound = !measurement.complete();
        site.blocks = measurement.blocks();
        site.observedAt = System.currentTimeMillis();
        site.verifiedAt = site.observedAt;
        site.status = "verified";
        site.reason = "";
        ctx.bb().set(ctx.owner(), destination, seed);
        knowledge.save(ctx);
        return Success.INSTANCE;
    }

    private NodeResult unavailable(TickContext ctx, String status, String reason) {
        site.unavailable(status, reason);
        memory.coverage.put(ZoneCoverage.key(site.x >> 4, site.z >> 4),
            new ChunkObservation(CoverageState.OBSERVED, System.currentTimeMillis()));
        knowledge.save(ctx);
        return fail(FailureCode.UNREACHABLE, reason);
    }

    @Override
    protected void onResume(TickContext ctx, long ticks, long ms) {
        measurement = null;
        reported = 0;
        airborneTicks = 0;
        ctx.bb().remove(ctx.owner(), destination);
    }
}
