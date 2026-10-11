package com.hunterbuddy.util;

public final class ShipwreckRun {
    public static final long CHEST_LIMIT_MS = 180_000L;
    public static final long RETURN_LIMIT_MS = 120_000L;
    public static final long DEPARTURE_LIMIT_MS = 180_000L;
    private long chestStarted;
    private int attempts;
    private int airReturns;
    private double ascentSpeed = 0.2;
    private double ascentY;
    private boolean measuring;
    private long returningSince;
    private long departingSince;
    private int exitRecoveries;
    private boolean retryChest;
    private boolean emergency;

    public ShipwreckRun(long now) { startChest(now); }
    public void startChest(long now) {
        chestStarted = now;
        attempts = airReturns = 0;
        retryChest = false;
        returningSince = 0;
        departingSince = 0;
        measuring = false;
    }
    public boolean attempt() {
        if (attempts >= 4) return false;
        attempts++;
        return true;
    }
    public boolean returnForAir(long now) {
        if (airReturns >= 3) return false;
        airReturns++;
        beginReturn(now, true);
        return true;
    }
    public void beginReturn(long now, boolean retry) {
        if (returningSince == 0) returningSince = now;
        retryChest = retry;
        measuring = false;
    }
    public void endReturn() { returningSince = 0; retryChest = false; measuring = false; }
    public boolean returning() { return returningSince != 0; }
    public boolean retryChest() { return retryChest; }
    public boolean chestExpired(long now) { return now - chestStarted >= CHEST_LIMIT_MS; }
    public boolean returnExpired(long now) { return returning() && now - returningSince >= RETURN_LIMIT_MS; }
    public void depart(long now) {
        if (departingSince == 0) departingSince = now;
        endReturn();
    }
    public boolean departing() { return departingSince != 0; }
    public boolean departureExpired(long now) { return departing() && now - departingSince >= DEPARTURE_LIMIT_MS; }
    public boolean recoverExit() {
        if (exitRecoveries >= 3) return false;
        exitRecoveries++;
        return true;
    }
    public int exitRecoveries() { return exitRecoveries; }
    public void emergency() { emergency = true; }
    public boolean isEmergency() { return emergency; }
    public void endEmergency() { emergency = false; departingSince = 0; }
    public void measureAscent(double y, boolean submerged) {
        if (measuring && submerged) {
            double gain = y - ascentY;
            if (gain > 0.001 && gain < 1.0) ascentSpeed = 0.8 * ascentSpeed + 0.2 * Math.max(0.02, Math.min(0.3, gain));
        }
        ascentY = y;
        measuring = submerged;
    }
    public int airThreshold(double depth, double oxygenBonus) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(100.0, Math.ceil(Math.max(0.0, depth) / ascentSpeed / (1.0 + oxygenBonus)) + 40.0));
    }
    public int airReturns() { return airReturns; }
}
