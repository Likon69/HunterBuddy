package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.OperationFailure;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.CoverageState;
import com.hunterbuddy.behavior.farmer.MaterialSite;
import com.hunterbuddy.util.Rockets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

public final class SurveyRoute {
    private final SurveyContext survey;
    private final ArrayDeque<BlockPos> waypoints = new ArrayDeque<>();
    private final Map<String, Integer> visits = new LinkedHashMap<>();
    private BlockPos waypoint;
    private int spacing;
    private int orbit;
    private BlockPos orbitCentre;
    private long waitingAt = -1;
    private boolean finished;

    public SurveyRoute(SurveyContext survey) {
        this.survey = survey;
        bands();
    }

    public BlockPos initial() {
        BlockPos hint = unverifiedHint();
        if (hint != null) return hint;
        MaterialSite site = survey.best();
        if (site != null) return site.seed();
        return waypoints.isEmpty() ? survey.home : waypoints.peekFirst();
    }

    private void bands() {
        spacing = Math.max(1, LoadedChunks.measuredRadius(survey.world) - 1);
        int min = survey.coverage.chunks().stream().mapToInt(pos -> pos.x).min().orElse(0);
        Map<Integer, List<ChunkPos>> strips = new LinkedHashMap<>();
        for (ChunkPos chunk : survey.coverage.chunks()) {
            if (survey.memory.state(ZoneCoverage.key(chunk.x, chunk.z)) == CoverageState.VERIFIED) continue;
            strips.computeIfAbsent(Math.floorDiv(chunk.x - min, spacing), ignored -> new ArrayList<>()).add(chunk);
        }
        BlockPos hint = unverifiedHint();
        BlockPos anchor = hint != null ? hint : survey.knowledge.biomeHints(survey.profile, survey.world.feet(), 8).stream()
            .filter(pos -> survey.coverage.intersects(pos.x, pos.z)).findFirst()
            .map(pos -> point(pos.x, pos.z)).orElse(survey.world.feet());
        if (hint != null) waypoints.add(hint);
        List<List<ChunkPos>> ordered = new ArrayList<>(strips.values());
        int max = survey.coverage.chunks().stream().mapToInt(pos -> pos.x).max().orElse(min);
        if (Math.abs((anchor.getX() >> 4) - max) < Math.abs((anchor.getX() >> 4) - min)) Collections.reverse(ordered);
        boolean reverse = false;
        if (!ordered.isEmpty()) {
            int low = ordered.getFirst().stream().mapToInt(pos -> pos.z).min().orElse(0);
            int high = ordered.getFirst().stream().mapToInt(pos -> pos.z).max().orElse(0);
            reverse = Math.abs((anchor.getZ() >> 4) - high) < Math.abs((anchor.getZ() >> 4) - low);
        }
        for (List<ChunkPos> strip : ordered) {
            strip.sort(Comparator.comparingInt(pos -> pos.z));
            ChunkPos first = strip.get(reverse ? strip.size() - 1 : 0);
            ChunkPos last = strip.get(reverse ? 0 : strip.size() - 1);
            int x = strip.stream().mapToInt(pos -> pos.x).min().orElseThrow() + spacing / 2;
            waypoints.add(point(x, first.z));
            if (last.z != first.z) waypoints.add(point(x, last.z));
            reverse = !reverse;
        }
    }

    private BlockPos unverifiedHint() {
        BlockPos hint = survey.siteHint;
        return hint != null && survey.memory.state(ZoneCoverage.key(hint.getX() >> 4, hint.getZ() >> 4)) != CoverageState.VERIFIED
            ? hint : null;
    }

    private BlockPos point(int x, int z) {
        return new BlockPos(x * 16 + 8, survey.home.getY(), z * 16 + 8);
    }

    public void observe(TickContext ctx, FlightPlan plan) {
        if (finished) return;
        survey.scanner.tick(ctx);
        if (survey.missionMode && survey.returnRequested.getAsBoolean()) {
            survey.interrupted = true;
            survey.selected = null;
            survey.result = "survey interrupted for mission retreat";
            finish(plan, survey.home, ctx);
            return;
        }
        MaterialSite site = survey.best();
        if (site != null) {
            survey.selected = site;
            survey.result = "site measured: " + site.quantity + " blocks, on-site validation required";
            finish(plan, site.seed(), ctx);
            return;
        }
        if (survey.coverage.complete()) {
            survey.interrupted = survey.missionMode && survey.temporarilyUnavailable();
            survey.result = survey.interrupted ? "survey paused while known sites are temporarily avoided"
                : "zone fully verified; no accessible site with at least 1728 blocks";
            finish(plan, survey.world.feet(), ctx);
            return;
        }
        plan.cruiseThrough = true;
        if (waypoint == null) {
            waypoint = waypoints.pollFirst();
            if (waypoint == null) waypoint = remaining(ctx, plan);
            steer(plan, waypoint, ctx);
        }
        if (!ctx.sensors().gliding() || plan.distance(waypoint) > 16 || !survey.world.loaded(waypoint)) return;
        int measured = Math.max(1, LoadedChunks.measuredRadius(survey.world) - 1);
        if (measured < spacing) {
            waypoints.clear();
            bands();
        }
        BlockPos next = waypoints.pollFirst();
        if (next == null) next = remaining(ctx, plan);
        waypoint = next;
        steer(plan, next, ctx);
    }

    private BlockPos remaining(TickContext ctx, FlightPlan plan) {
        if (!survey.scanner.idle()) {
            if (waitingAt < 0) waitingAt = ctx.tick();
            if (ctx.tick() - waitingAt < 600) {
                if (orbitCentre == null) orbitCentre = waypoint;
                double angle = Math.PI * (orbit++ % 4) / 2;
                return orbitCentre.add((int) Math.round(Math.cos(angle) * 64), 0, (int) Math.round(Math.sin(angle) * 64));
            }
        }
        waitingAt = -1;
        orbitCentre = null;
        ChunkPos next = survey.coverage.chunks().stream()
            .filter(pos -> survey.memory.state(ZoneCoverage.key(pos.x, pos.z)) != CoverageState.VERIFIED)
            .filter(pos -> visits.getOrDefault(ZoneCoverage.key(pos.x, pos.z), 0) < 3)
            .min(Comparator.comparingDouble(pos -> plan.distance(point(pos.x, pos.z)))).orElse(null);
        if (next == null) {
            survey.result = "Warning: incomplete survey, " + survey.coverageReport()
                + "; chunks unloaded or continuity unverifiable, absence of material has not been established";
            finish(plan, survey.world.feet(), ctx);
            return survey.home;
        }
        String key = ZoneCoverage.key(next.x, next.z);
        visits.merge(key, 1, Integer::sum);
        survey.scanner.rescan();
        return point(next.x, next.z);
    }

    private void steer(FlightPlan plan, BlockPos target, TickContext ctx) {
        if (target == null) throw new IllegalStateException("survey route has no destination");
        if (target.equals(plan.destination)) return;
        double distance = Math.hypot(target.getX() - survey.world.feet().getX(), target.getZ() - survey.world.feet().getZ());
        double back = Math.hypot(target.getX() - survey.home.getX(), target.getZ() - survey.home.getZ());
        int required = plan.budget.tripRockets(distance, back, false);
        if (survey.world.countMatching(Rockets::isPlain) < required) {
            survey.result = "incomplete survey: return reserve reached; " + survey.coverageReport();
            if (survey.missionMode) {
                survey.interrupted = true;
                survey.selected = null;
                finish(plan, survey.home, ctx);
                return;
            }
            throw new OperationFailure(FailureCode.BUDGET, survey.result);
        }
        int wear = plan.budget.tripWear(distance, back, false);
        if (plan.totalDurability() < wear) {
            survey.result = "incomplete survey: durability reserved for return; " + survey.coverageReport();
            if (survey.missionMode) {
                survey.interrupted = true;
                survey.selected = null;
                finish(plan, survey.home, ctx);
                return;
            }
            throw new OperationFailure(FailureCode.BUDGET, survey.result);
        }
        plan.destination = target;
        plan.bestDistance = plan.distance(target);
        plan.improvedAt = ctx.tick();
    }

    private void finish(FlightPlan plan, BlockPos target, TickContext ctx) {
        finished = true;
        plan.cruiseThrough = false;
        if (survey.selected == null) {
            plan.beginReturn(ctx);
        } else steer(plan, target, ctx);
        survey.scanner.save(ctx);
    }
}
