package com.hunterbuddy.modules.waypointfollow;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class WaypointLoader {
    public static final int MAX_ATTEMPTS = 10;
    private static final int REFRESH_BURSTS = 5;
    private static final long REFRESH_PAUSE_MS = 250L;
    private static final long ATTEMPT_PAUSE_MS = 300L;
    private static final String THREAD_NAME = "waypoint-follower";

    private final BooleanSupplier loaded;
    private final Runnable attempt;
    private final Runnable onStalled;
    private final Consumer<String> onError;
    private Thread thread;

    public WaypointLoader(BooleanSupplier loaded, Runnable attempt, Runnable onStalled, Consumer<String> onError) {
        this.loaded = loaded;
        this.attempt = attempt;
        this.onStalled = onStalled;
        this.onError = onError;
    }

    public void start() {
        stop();
        thread = new Thread(this::run, THREAD_NAME);
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        if (thread != null && thread.isAlive()) thread.interrupt();
    }

    private void run() {
        try {
            refreshBursts();
            attemptUntilLoaded();
            if (!loaded.getAsBoolean()) onStalled.run();
        } catch (InterruptedException e) {
            return;
        } catch (Exception e) {
            onError.accept(e.getMessage());
        }
    }

    private void refreshBursts() throws InterruptedException {
        for (int i = 0; i < REFRESH_BURSTS; i++) {
            FollowerWaypointWorld.requestRefresh();
            Thread.sleep(REFRESH_PAUSE_MS);
        }
    }

    private void attemptUntilLoaded() throws InterruptedException {
        for (int i = 0; i < MAX_ATTEMPTS && !loaded.getAsBoolean(); i++) {
            Thread.sleep(ATTEMPT_PAUSE_MS);
            attempt.run();
        }
    }
}
