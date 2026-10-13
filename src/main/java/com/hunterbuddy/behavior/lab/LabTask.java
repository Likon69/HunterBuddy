package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.bot.BotContext;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.Set;

public sealed interface LabTask permits InventoryTask, WalkTask, LookTask, GoToTask, BreakColumnTask, PlaceTask, PillarTask, OpenTask, FlightTask {
    String name();
    String target();
    Set<Resource> resources();
    void initialize(BotContext context);
    Node build(BotContext context, WorldReader world, Outputs outputs);

    default boolean recoverFailure() {
        return false;
    }

    default boolean flightAware() { return false; }
}
