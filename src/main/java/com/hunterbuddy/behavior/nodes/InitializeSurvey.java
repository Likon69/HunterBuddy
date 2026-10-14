package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.ChunkObservation;
import com.hunterbuddy.behavior.farmer.CoverageState;
import com.hunterbuddy.behavior.farmer.FarmerSettings;
import com.hunterbuddy.behavior.farmer.FarmerStepConfig;
import com.hunterbuddy.behavior.farmer.FarmerZoneLookup;
import com.hunterbuddy.behavior.farmer.MissionJournal;
import com.hunterbuddy.behavior.farmer.WorldKnowledge;
import com.hunterbuddy.behavior.mc.SurveyContext;
import com.hunterbuddy.behavior.mc.SurveyRoute;
import com.hunterbuddy.behavior.mc.SurveyScanner;
import com.hunterbuddy.behavior.mc.ZoneCoverage;

public final class InitializeSurvey extends Action {
    private final SurveyContext survey;
    private long startedAt;

    public InitializeSurvey(SurveyContext survey) {
        super("resolve-current-FarmerBuddy-step-and-zone");
        this.survey = survey;
        limit(2400);
    }

    @Override
    protected void onActionStart(TickContext ctx) {
        startedAt = ctx.tick();
        survey.selected = null;
        survey.coverage = null;
        survey.interrupted = false;
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        if (!survey.worldKey.equals(survey.world.storageKey())) return fail(FailureCode.REFUSED, "survey world changed");
        if (!survey.mission.isDone()) {
            if (ctx.tick() - startedAt > 100) return fail(FailureCode.TIMEOUT, "FarmerBuddy mission loading exceeded 5 seconds");
            return new Running("loading current FarmerBuddy step");
        }
        try {
            if (survey.coverage == null) resolve(ctx);
            if (!survey.coverage.initialize()) return new Running("enumerating chunks intersecting the Xaero polygon");
            survey.scanner = new SurveyScanner(survey.world, survey.zone, survey.profile, survey.coverage,
                survey.knowledge, survey.memory);
            survey.route = new SurveyRoute(survey);
            ctx.bb().remove(ctx.owner(), survey.verifiedSite);
            return Success.INSTANCE;
        } catch (RuntimeException ex) {
            return fail(FailureCode.REFUSED, "cannot initialize survey: " + ex.getMessage());
        }
    }

    private void resolve(TickContext ctx) {
        if (!survey.missionMode) ctx.bb().loadDurable("bot:farmerbuddy", survey.mission.join());
        int current = new MissionJournal(FarmerSettings.MAX_STEPS).currentStep(ctx, "bot:farmerbuddy");
        survey.step = survey.steps.stream().filter(FarmerStepConfig::enabled)
            .filter(step -> step.index() >= current).findFirst()
            .orElseThrow(() -> new IllegalStateException("no enabled current FarmerBuddy step"));
        survey.profile = survey.catalog.profile(survey.step.profile())
            .orElseThrow(() -> new IllegalStateException("unknown catalog profile: " + survey.step.profile()));
        FarmerZoneLookup.Resolved resolved = survey.lookup.resolve(survey.step.name());
        if (!resolved.ok()) throw new IllegalStateException(resolved.refusal());
        survey.zone = resolved.zone();
        survey.knowledge = WorldKnowledge.load(ctx);
        survey.memory = survey.knowledge.zone(survey.zone, survey.profile);
        long cutoff = System.currentTimeMillis() - 86_400_000L;
        survey.memory.coverage.replaceAll((key, observation) -> observation.observedAt() < cutoff
            ? new ChunkObservation(CoverageState.OBSERVED, observation.observedAt()) : observation);
        survey.coverage = new ZoneCoverage(survey.zone, survey.memory);
    }

    @Override
    protected void onResume(TickContext ctx, long ticks, long ms) {
        startedAt += ticks;
    }
}
