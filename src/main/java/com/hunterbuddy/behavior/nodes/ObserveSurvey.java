package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.SurveyContext;

public final class ObserveSurvey extends Action {
    private final SurveyContext survey;

    public ObserveSurvey(SurveyContext survey) {
        super("measure-already-loaded-zone-chunks");
        this.survey = survey;
        limit(2400);
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        if (ctx.sensors().gliding()) return Success.INSTANCE;
        survey.scanner.tick(ctx);
        if (survey.best() != null || survey.scanner.idle()) return Success.INSTANCE;
        return new Running(survey.coverageReport());
    }

    @Override
    protected void onResume(TickContext ctx, long ticks, long ms) {
        survey.scanner.resumed();
    }
}
