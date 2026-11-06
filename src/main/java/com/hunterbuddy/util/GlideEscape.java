package com.hunterbuddy.util;

public class GlideEscape {
    private static final double BLOCKED_SPEED = 0.3;
    private static final int BLOCKED_TICKS = 20;
    private static final int ESCAPE_TICKS = 60;
    private static final int TURN_TICKS = 10;
    private static final float TURN = 135.0f;

    private int slowTicks;
    private int escapeTicks;
    private int side = 1;
    private float escapeYaw;

    public boolean update(double speedH, boolean burning, float yaw) {
        slowTicks = speedH < BLOCKED_SPEED ? slowTicks + 1 : 0;
        if (escapeTicks != 0 || !(burning || slowTicks >= BLOCKED_TICKS)) return false;
        side = -side;
        escapeYaw = yaw + TURN * side;
        escapeTicks = ESCAPE_TICKS;
        slowTicks = 0;
        return true;
    }

    public boolean escaping() {
        return escapeTicks > 0;
    }

    public boolean turning() {
        return escaping() && ESCAPE_TICKS - escapeTicks < TURN_TICKS;
    }

    public float yaw() {
        return escapeYaw;
    }

    public void tick() {
        if (escapeTicks > 0) escapeTicks--;
    }

    public void reset() {
        slowTicks = 0;
        escapeTicks = 0;
    }
}
