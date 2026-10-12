package com.hunterbuddy.behavior.core;

import java.util.Objects;

public final class Series {
    private Sensors snapshot;
    private long sampledTick = -1;
    private long positionTick = -1;
    private long stillSince = -1;
    private double stillX;
    private double stillY;
    private double stillZ;
    private double highestY = Double.NaN;
    private long yImprovedAt = -1;
    private long attackedAt = -1;
    private String distanceTarget;
    private double shortestDistance = Double.NaN;
    private long distanceTick = -1;
    private long distanceImprovedAt = -1;

    void sample(long tick, Sensors sensors, boolean samplesValid) {
        Objects.requireNonNull(sensors);
        if (tick < 0 || tick < sampledTick) throw new IllegalArgumentException("non-monotone series tick");
        if (snapshot != null && (!snapshot.dimensionKey().equals(sensors.dimensionKey())
            || snapshot.worldId() != sensors.worldId() || snapshot.playerId() != sensors.playerId())) reset();
        if (tick == sampledTick) {
            if (!samplesValid) {
                positionTick = -1;
                stillSince = -1;
            }
            return;
        }
        if (sampledTick < 0 || tick != sampledTick + 1) stillSince = -1;
        sampledTick = tick;
        snapshot = sensors;
        if (sensors.hurtThisTick()) attackedAt = tick;
        positionTick = samplesValid && Double.isFinite(sensors.x()) && Double.isFinite(sensors.y())
            && Double.isFinite(sensors.z()) ? tick : -1;
        if (positionTick < 0) {
            stillSince = -1;
            return;
        }
        if (stillSince < 0 || sensors.x() != stillX || sensors.y() != stillY || sensors.z() != stillZ) {
            stillSince = tick;
            stillX = sensors.x();
            stillY = sensors.y();
            stillZ = sensors.z();
        }
        if (Double.isNaN(highestY)) highestY = sensors.y();
        else if (sensors.y() > highestY) {
            highestY = sensors.y();
            yImprovedAt = tick;
        }
    }

    void distance(String target, double value) {
        if (sampledTick < 0) throw new IllegalStateException("distance before series capture");
        if (target == null) {
            if (!Double.isNaN(value)) throw new IllegalArgumentException("missing target requires unavailable distance");
            clearDistance();
            return;
        }
        if (target.isBlank() || !Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException("invalid goal distance");
        }
        if (!target.equals(distanceTarget)) {
            clearDistance();
            distanceTarget = target;
        }
        if (positionTick != sampledTick || distanceTick == sampledTick) return;
        distanceTick = sampledTick;
        if (Double.isNaN(shortestDistance)) shortestDistance = value;
        else if (value < shortestDistance) {
            shortestDistance = value;
            distanceImprovedAt = sampledTick;
        }
    }

    public boolean yBest(int ticks) {
        return recent(yImprovedAt, ticks);
    }

    public boolean distanceBest(int ticks) {
        return recent(distanceImprovedAt, ticks);
    }

    public boolean stillFor(int ticks) {
        requireWindow(ticks);
        return stillSince >= 0 && positionTick == sampledTick && sampledTick - stillSince >= ticks;
    }

    public boolean attackedWithin(int ticks) {
        return recent(attackedAt, ticks);
    }

    private boolean recent(long eventTick, int ticks) {
        requireWindow(ticks);
        return eventTick >= 0 && sampledTick - eventTick < ticks;
    }

    private void requireWindow(int ticks) {
        if (ticks < 1) throw new IllegalArgumentException("series window must be positive");
    }

    private void clearDistance() {
        distanceTarget = null;
        shortestDistance = Double.NaN;
        distanceTick = -1;
        distanceImprovedAt = -1;
    }

    void reset() {
        snapshot = null;
        sampledTick = -1;
        positionTick = -1;
        stillSince = -1;
        highestY = Double.NaN;
        yImprovedAt = -1;
        attackedAt = -1;
        clearDistance();
    }
}
