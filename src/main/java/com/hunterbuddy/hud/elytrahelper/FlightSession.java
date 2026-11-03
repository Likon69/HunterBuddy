package com.hunterbuddy.hud.elytrahelper;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

public final class FlightSession {
    private static final double TICKS_PER_SECOND = 20.0;
    private static final double MILLIS_PER_SECOND = 1000.0;

    private long startTime;
    private long lastUpdate;
    private long flyingMillis;
    private boolean wasFlying;
    private Vec3d startPosition;
    private Vec3d lastPosition;
    private double distance;
    private double peakSpeed;

    public void update(ClientPlayerEntity player, long now) {
        boolean flying = player.isGliding();
        Vec3d position = player.getEntityPos();
        if (flying && !wasFlying) beginFlight(position, now);
        if (flying) accumulate(player, position, now);
        else lastPosition = null;
        lastUpdate = now;
        wasFlying = flying;
    }

    public boolean hasStarted() {
        return startTime > 0;
    }

    public double sessionSeconds(long now) {
        return (now - startTime) / MILLIS_PER_SECOND;
    }

    public long flyingSeconds() {
        return (long) (flyingMillis / MILLIS_PER_SECOND);
    }

    public double distance() {
        return distance;
    }

    public double averageSpeed() {
        if (flyingMillis <= 0) return 0.0;
        return distance / (flyingMillis / MILLIS_PER_SECOND);
    }

    public double peakSpeed() {
        return peakSpeed;
    }

    public boolean hasStartPosition() {
        return startPosition != null;
    }

    public double distanceFromStart(Vec3d position) {
        return position.distanceTo(startPosition);
    }

    public static double speedOf(ClientPlayerEntity player) {
        return player.getVelocity().length() * TICKS_PER_SECOND;
    }

    private void beginFlight(Vec3d position, long now) {
        if (startTime == 0) {
            startTime = now;
            startPosition = position;
            distance = 0.0;
            peakSpeed = 0.0;
        }
        lastPosition = position;
        lastUpdate = now;
    }

    private void accumulate(ClientPlayerEntity player, Vec3d position, long now) {
        flyingMillis += now - lastUpdate;
        if (lastPosition != null) distance += lastPosition.distanceTo(position);
        lastPosition = position;
        peakSpeed = Math.max(peakSpeed, speedOf(player));
    }
}
