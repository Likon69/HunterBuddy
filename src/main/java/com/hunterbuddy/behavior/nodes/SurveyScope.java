package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.FarmerZoneLookup;
import com.hunterbuddy.behavior.mc.SurveyContext;

public final class SurveyScope extends Decorator {
    private final SurveyContext survey;

    public SurveyScope(SurveyContext survey, Node child) {
        super("survey-in-captured-world", child);
        this.survey = survey;
    }

    @Override
    protected void onStart(TickContext ctx) {
        survey.zone = null;
        survey.step = null;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        if (!survey.worldKey.equals(survey.world.storageKey())) return fail(FailureCode.REFUSED, "survey world or dimension changed");
        if (survey.zone != null && survey.step != null) {
            FarmerZoneLookup.Resolved current = survey.lookup.resolve(survey.step.hint());
            if (!current.ok()) {
                survey.result = "Invalid Xaero zone: " + current.refusal();
                return fail(FailureCode.REFUSED, survey.result);
            }
            if (!survey.zone.sameFootprint(current.zone())) {
                survey.result = "Xaero zone moved: new coverage must be verified";
                survey.zone = current.zone();
                return fail(FailureCode.NO_PROGRESS, survey.result);
            }
        }
        NodeResult result = child().execute(ctx);
        if (result instanceof Failure failure) {
            survey.result = failure.reason() + (survey.coverage == null ? "" : "; " + survey.coverageReport())
                + "; absence of material has not been established";
        }
        return result;
    }

    @Override
    protected void onResume(TickContext ctx, long ticks, long ms) {
        if (survey.scanner != null) survey.scanner.resumed();
    }

    @Override
    protected void onStop(TickContext ctx, ExitReason reason) {
        if (survey.scanner != null && survey.world.available() && survey.worldKey.equals(survey.world.storageKey())) {
            survey.scanner.save(ctx);
        }
    }
}
