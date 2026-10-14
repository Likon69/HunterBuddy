package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.core.Sensors;
import com.hunterbuddy.behavior.core.TargetPoint;
import java.util.Objects;
import java.util.function.Function;

public final class ResumeGate {
    private final WorldReader world;
    private final Function<Sensors, String> refusal;
    private TargetPoint anchor;
    private int stable;
    private long setbacks;
    private long started = -1;
    private long queueAt = -1;
    private long screenAt = -1;
    private String cause = "waiting for connection";

    public ResumeGate(WorldReader world, Function<Sensors, String> refusal) {
        this.world = Objects.requireNonNull(world);
        this.refusal = Objects.requireNonNull(refusal);
    }

    public void reset() { anchor = null; stable = 0; started = -1; queueAt = -1; screenAt = -1; cause = "waiting for connection"; }

    public Decision check(Sensors sensors, long nowMs) {
        if (started < 0) started = nowMs;
        if (sensors.dead() || sensors.health() <= 0) return new Decision(State.DEAD, "Player died while waiting for reconnection");
        if (sensors.inQueue()) {
            if (queueAt < 0) queueAt = nowMs;
            if (nowMs - queueAt > 900_000) return new Decision(State.STOP, "Session queue wait exceeded 15 minutes during reconnection");
            return waitFor("2b2t queue");
        }
        queueAt = -1;
        if (sensors.screenOpen() && !sensors.screenOurs() && sensors.onGround() && !sensors.gliding()) {
            if (screenAt < 0) screenAt = nowMs;
            if (nowMs - screenAt > 60_000) return new Decision(State.STOP, "Foreign screen '" + sensors.screenType() + "' exceeded 60 seconds during reconnection");
            return waitFor("foreign screen " + sensors.screenType());
        }
        screenAt = -1;
        if (nowMs - started > 900_000) return new Decision(State.STOP, "Resume gate exceeded 15 minutes: " + cause);
        String blocked = refusal.apply(sensors);
        if (blocked != null) return waitFor(blocked);
        if (sensors.serverLagging() || !world.loadedRadiusOne()) return waitFor("server lag or neighboring chunks not loaded");
        if (sensors.gliding()) {
            cause = "ok";
            return new Decision(State.READY, cause);
        }
        TargetPoint point = new TargetPoint(sensors.x(), sensors.y(), sensors.z());
        if (anchor == null || anchor.distance(point.x(), point.y(), point.z()) > 0.1 || setbacks != sensors.setbacks()) {
            anchor = point;
            stable = 0;
            setbacks = sensors.setbacks();
        }
        if (++stable < 60) { cause = "stable position " + stable + "/60"; return new Decision(State.WAIT, cause); }
        cause = "ok";
        return new Decision(State.READY, cause);
    }

    private Decision waitFor(String reason) { cause = reason; anchor = null; stable = 0; return new Decision(State.WAIT, reason); }
    public String cause() { return cause; }
    public record Decision(State state, String cause) { }
    public enum State { WAIT, READY, STOP, DEAD }
}
