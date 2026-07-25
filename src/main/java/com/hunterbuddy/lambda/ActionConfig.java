/*
 * Port of com.lambda.config.blocks.ActionConfig (lambda 1.21.11).
 */
package com.hunterbuddy.lambda;

import net.minecraft.world.tick.TickScheduler;
import java.util.Collection;

public interface ActionConfig {
    SortMode getSorter();
    void setSorter(SortMode v);
    Collection<TickScheduler> getTickStageMask();
    void setTickStageMask(Collection<TickScheduler> v);
}
