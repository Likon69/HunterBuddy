package com.hunterbuddy.modules.visualrange;

import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class LeaveClassifier {
    private static final long TRANSFER_GRACE_MILLIS = 4000L;
    private static final long MINIMUM_SESSION_MILLIS = 45000L;
    private static final String SILENT_LEAVE_REASON = "Left the server";

    private volatile long pendingLeave;
    private volatile long joinedAt;

    public void joined() {
        pendingLeave = 0L;
        joinedAt = System.currentTimeMillis();
    }

    public void left(Consumer<Text> report, BooleanSupplier moduleActive) {
        if (!moduleActive.getAsBoolean()) return;
        Text serverReason = DisconnectMemory.takeFresh();
        if (serverReason != null) {
            pendingLeave = 0L;
            report.accept(serverReason);
        } else if (isStillArriving()) {
            pendingLeave = 0L;
        } else {
            long token = System.nanoTime();
            pendingLeave = token;
            MeteorExecutor.execute(() -> confirmDeparture(token, report, moduleActive));
        }
    }

    private boolean isStillArriving() {
        return joinedAt == 0L || System.currentTimeMillis() - joinedAt < MINIMUM_SESSION_MILLIS;
    }

    private void confirmDeparture(long token, Consumer<Text> report, BooleanSupplier moduleActive) {
        try {
            Thread.sleep(TRANSFER_GRACE_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return;
        }
        if (pendingLeave != token) return;
        pendingLeave = 0L;
        if (moduleActive.getAsBoolean()) report.accept(Text.literal(SILENT_LEAVE_REASON));
    }
}
