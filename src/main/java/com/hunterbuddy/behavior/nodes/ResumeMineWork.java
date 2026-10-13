package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.TickContext;

public final class ResumeMineWork extends Node {
    public ResumeMineWork() {
        super("resume-mining-after-oxygen-recovery");
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        return new Running("oxygen recovered; resume the interrupted mining node");
    }
}
