package com.hunterbuddy.behavior.routine;

import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.Sensors;
import com.hunterbuddy.behavior.hooks.Contributor;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Guard;
import com.hunterbuddy.behavior.nodes.StopBot;

public final class DeathRoutine {
    public HookEntry entry() {
        Node stop = new StopBot(ExitReason.DEAD, "Player died");
        return new HookEntry(Hook.DEATH, "core:death", Contributor.CORE, 0,
            new Guard(new Condition("dead", Sensors::dead), stop), true, stop);
    }
}
