/*
 * Port of com.lambda.interaction.managers.Request (lambda 1.21.11) — interface stub.
 * Lambda has multiple Request subtypes (BreakRequest, InteractRequest, etc).
 * This is the base marker interface for them.
 */
package com.hunterbuddy.lambda;

import net.minecraft.world.tick.TickScheduler;
import java.util.Collection;

public interface Request extends ActionInfo {
    boolean isFresh();
    void setFresh(boolean fresh);
    boolean isNowOrNothing();
    TickScheduler getTickStageMask();
    java.util.Collection<TickScheduler> getBlacklistedStages();
    Automated getAutomated();
}
