package com.hunterbuddy.util;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;

public final class SwapWindow {
    private static volatile boolean silentFlag;

    private final boolean raisedFlag;
    private final Runnable action;
    private int remaining;
    private boolean over;

    private SwapWindow(int ticks, boolean silent, Runnable action) {
        this.remaining = ticks;
        this.raisedFlag = silent;
        this.action = action;
        if (silent) {
            silentFlag = true;
            if (MeteorClient.mc.player != null) MeteorClient.mc.player.setSprinting(false);
        }
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    public static SwapWindow after(int ticks, boolean silent, Runnable action) {
        return new SwapWindow(ticks, silent, action);
    }

    public static boolean silent() {
        return silentFlag;
    }

    public boolean done() {
        return over;
    }

    public void cancel() {
        end();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (over) return;
        if (MeteorClient.mc.player == null) {
            end();
            return;
        }
        if (raisedFlag) MeteorClient.mc.player.setSprinting(false);
        remaining--;
        if (remaining <= 0) {
            try {
                action.run();
            } finally {
                end();
            }
        }
    }

    private void end() {
        if (over) return;
        over = true;
        MeteorClient.EVENT_BUS.unsubscribe(this);
        if (raisedFlag) silentFlag = false;
    }
}
