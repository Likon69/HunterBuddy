package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.bot.BotContext;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.Set;

public sealed interface LabTask permits WalkTask, LookTask {
    String name();
    String target();
    Set<Resource> resources();
    void initialize(BotContext context);
    Node build(BotContext context, WorldReader world, Outputs outputs);
}
