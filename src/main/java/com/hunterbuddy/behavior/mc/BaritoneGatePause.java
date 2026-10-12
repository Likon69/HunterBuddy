package com.hunterbuddy.behavior.mc;

import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;

public final class BaritoneGatePause implements IBaritoneProcess {
    private Stage stage = Stage.INACTIVE;
    private boolean ready;

    public void pause(boolean pause) {
        if (!pause) ready = false;
        stage = pause ? Stage.PAUSING : Stage.INACTIVE;
    }

    public boolean ready() {
        return ready;
    }

    @Override
    public boolean isActive() {
        return stage == Stage.PAUSING;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        ready = isSafeToCancel;
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    @Override
    public boolean isTemporary() {
        return true;
    }

    @Override
    public void onLostControl() {
        pause(false);
    }

    @Override
    public double priority() {
        return Double.MAX_VALUE;
    }

    @Override
    public String displayName0() {
        return "TreeJava gate pause";
    }

    private enum Stage {
        INACTIVE, PAUSING
    }
}
