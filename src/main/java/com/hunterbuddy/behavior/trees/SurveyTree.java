package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.mc.FlightPlan;
import com.hunterbuddy.behavior.mc.SiteArrival;
import com.hunterbuddy.behavior.mc.SurveyContext;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Deferred;
import com.hunterbuddy.behavior.nodes.Guard;
import com.hunterbuddy.behavior.nodes.InitializeSurvey;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.MiningBounds;
import com.hunterbuddy.behavior.nodes.ObserveSurvey;
import com.hunterbuddy.behavior.nodes.Retry;
import com.hunterbuddy.behavior.nodes.Selector;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.SiteAccess;
import com.hunterbuddy.behavior.nodes.SteadyFlight;
import com.hunterbuddy.behavior.nodes.StopBot;
import com.hunterbuddy.behavior.nodes.Succeed;
import com.hunterbuddy.behavior.nodes.SurveyScope;
import com.hunterbuddy.behavior.nodes.TaskScope;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.VerifySite;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.Set;

public final class SurveyTree {
    private SurveyTree() {}

    public static Node build(SurveyContext survey) {
        Node work = new SurveyScope(survey, new Sequence("observe-survey-select-and-verify",
            preparation(survey),
            new Deferred("continuous-flight-over-measured-bands", () -> flight(survey)), outcome(survey)));
        Node attempts = new Retry(3, 0, work).on(FailureCode.UNREACHABLE, FailureCode.NO_OPTION,
            FailureCode.NO_PROGRESS, FailureCode.TIMEOUT, FailureCode.NOT_CONFIRMED);
        return new TaskScope(new MiningBounds(survey.outputs.mining(),
            point -> survey.zone != null && survey.zone.contains(point.getX() + 0.5, point.getZ() + 0.5),
            new Selector("survey-with-return-reserve-fallback", attempts,
            new Deferred("yield-after-survey-attempts", () -> survey.missionMode
                && (survey.coverage == null || !survey.coverage.complete() || survey.temporarilyUnavailable())
                ? new InventoryMilestone("yield-incomplete-survey-to-mission", ctx -> {
                    survey.selected = survey.memory == null ? null : survey.best();
                    survey.interrupted = survey.returnRequested.getAsBoolean() || survey.temporarilyUnavailable();
                })
                : new Sequence("return-after-survey", home(survey),
                    StopBot.withReason(ExitReason.FAILED, ctx -> survey.result))))));
    }

    private static Node preparation(SurveyContext survey) {
        FlightPlan hold = new FlightPlan(survey.world::feet, () -> survey.home, false,
            survey.world, survey.baritone, survey.outputs);
        return new WithLease(Set.of(Resource.MOVEMENT, Resource.LOOK), LeasePolicy.WAIT,
            new SteadyFlight(hold, new Timeout(4800, new Sequence("resolve-zone-and-observe-local-chunks",
                new InitializeSurvey(survey), new ObserveSurvey(survey)))));
    }

    private static Node flight(SurveyContext survey) {
        survey.selected = survey.best();
        if (survey.selected != null && survey.world.client().player.isOnGround()
            && Math.hypot(survey.selected.x - survey.world.feet().getX(), survey.selected.z - survey.world.feet().getZ()) <= 32
            && survey.zone.contains(survey.world.client().player.getX(), survey.world.client().player.getZ())) return new Succeed();
        FlightPlan plan = new FlightPlan(survey.route::initial, () -> survey.home, false,
            survey.world, survey.baritone, survey.outputs);
        plan.cruiseThrough = survey.selected == null;
        plan.observer = ctx -> survey.route.observe(ctx, plan);
        return new SiteAccess(() -> survey.selected, survey.knowledge, true, FlyToTree.build(plan));
    }

    private static Node outcome(SurveyContext survey) {
        return new Selector("site-found-or-coverage-warning",
            new Guard(new Condition("sufficient measured site found", sensors -> survey.selected != null),
                new Sequence("reach-and-verify-site-before-mining",
                    new Deferred("access-selected-site", () -> {
                        survey.arrivalStand = SiteArrival.stand(survey.selected, survey.profile, survey.zone, survey.world);
                        return new SiteAccess(() -> survey.selected, survey.knowledge, false,
                            GroundTravel.build(survey.arrivalStand, 0, survey.baritone, survey.outputs));
                    }),
                    new Deferred("verify-selected-site", () -> new Timeout(2400,
                        new VerifySite(survey.world, survey.zone, survey.profile,
                            survey.knowledge, survey.memory, () -> survey.selected, () -> survey.arrivalStand, survey.verifiedSite))),
                    new Deferred("report-verified-site", () -> survey.missionMode ? new Succeed()
                        : StopBot.withReason(ExitReason.COMPLETED, ctx -> "Site " + survey.profile.label() + " verified at "
                            + survey.selected.seed().toShortString() + ": at least " + survey.selected.quantity + " blocks; "
                            + survey.coverageReport())))),
            new Guard(new Condition("no sufficient site selected", sensors -> survey.selected == null),
                new Deferred("report-survey-outcome", () -> survey.missionMode
                    && (survey.interrupted || !survey.coverage.complete()) ? new Succeed()
                    : new Sequence("return-and-report-coverage", home(survey), StopBot.withReason(ctx -> survey.result)))));
    }

    private static Node home(SurveyContext survey) {
        return new Guard(new Condition("survey home world unchanged", sensors -> survey.worldKey.equals(survey.world.storageKey())),
            FlyToTree.build(() -> survey.home, () -> survey.home, true, survey.world, survey.baritone, survey.outputs));
    }
}
