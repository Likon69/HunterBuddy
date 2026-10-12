package com.hunterbuddy.behavior.routine;

import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.nodes.StopBot;

public enum DeathPolicy {
    STOP;

    public Node behavior() {
        return new StopBot(ExitReason.DEAD, "Player died");
    }
}
