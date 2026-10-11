package com.hunterbuddy.util;

public final class ShipwreckRun {
    public static final long CHEST_LIMIT_MS = 180_000L;
    public static final long RETURN_LIMIT_MS = 120_000L;
    public static final long DEPARTURE_LIMIT_MS = 180_000L;
    private long chestStarted;
    private int attempts;
    private long returningSince;
    private long departingSince;
    private int exitRecoveries;
    private boolean emergency;

    public ShipwreckRun(long now) { startChest(now); }
    public void startChest(long now) {
        chestStarted = now;
        attempts = 0;
        returningSince = 0;
        departingSince = 0;
    }
    public boolean attempt() {
        if (attempts >= 4) return false;
        attempts++;
        return true;
    }
    public void beginReturn(long now) {
        if (returningSince == 0) returningSince = now;
    }
    public void endReturn() { returningSince = 0; }
    public boolean returning() { return returningSince != 0; }
    public void shiftChestTimer(long pausedMs) { chestStarted += pausedMs; }
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
}
