package com.hunterbuddy.modules.recast;

public final class RecastCounters {
    public static final int FAILED_TAKEOFF_PAUSE_TICKS = 80;

    private int ticks;
    private int rocketTicks;
    private int notGlidingTicks;
    private int pauseTicks;
    private boolean netherRocketUsed;

    public void clearKeepingPause() {
        ticks = 0;
        rocketTicks = 0;
        notGlidingTicks = 0;
        netherRocketUsed = false;
    }

    public void clear() {
        ticks = 0;
        rocketTicks = 0;
        notGlidingTicks = 0;
        pauseTicks = 0;
        netherRocketUsed = false;
    }

    public void tick() {
        ticks++;
        rocketTicks++;
    }

    public void restartTicks() {
        ticks = 0;
    }

    public int ticks() {
        return ticks;
    }

    public void primeRocketTimer(int rocketDelay) {
        rocketTicks = rocketDelay;
    }

    public void restartRocketTimer() {
        rocketTicks = 0;
    }

    public int rocketTicks() {
        return rocketTicks;
    }

    public int countNotGliding() {
        return ++notGlidingTicks;
    }

    public void clearNotGliding() {
        notGlidingTicks = 0;
    }

    public boolean isPaused() {
        return pauseTicks > 0;
    }

    public void countPauseDown() {
        pauseTicks--;
    }

    public void pause(int duration) {
        pauseTicks = duration;
    }

    public void clearPause() {
        pauseTicks = 0;
    }

    public boolean netherRocketUsed() {
        return netherRocketUsed;
    }

    public void markNetherRocketUsed() {
        netherRocketUsed = true;
    }

    public void clearNetherRocket() {
        netherRocketUsed = false;
    }
}
