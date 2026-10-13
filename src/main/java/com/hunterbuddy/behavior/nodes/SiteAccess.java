package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.SurveyContext;

public final class SiteAccess extends Decorator {
    private final SurveyContext survey;

    public SiteAccess(SurveyContext survey, Node child) {
        super("record-site-access-failure", child);
        this.survey = survey;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        NodeResult result = child().execute(ctx);
        if (result instanceof Failure failure && survey.selected != null && survey.selected.usable()
            && (failure.code() == FailureCode.UNREACHABLE || failure.code() == FailureCode.NO_PROGRESS
                || failure.code() == FailureCode.NO_OPTION || failure.code() == FailureCode.TIMEOUT)) {
            survey.selected.unavailable("inaccessible", failure.reason());
            survey.knowledge.save(ctx);
        }
        return result;
    }
}
