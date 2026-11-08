package com.hunterbuddy.pet;

public final class PetFlightDistance {
    public static final double MARK = 10000;
    private static final double MAX_STEP = 100;
    private Object world;
    private boolean tracking;
    private double lastX;
    private double lastZ;
    private double total;
    private long marks;

    public void reset() {
        world = null;
        tracking = false;
        total = 0;
        marks = 0;
    }

    public boolean update(Object currentWorld, boolean gliding, double x, double z) {
        if (currentWorld != world) {
            reset();
            world = currentWorld;
        }
        if (!gliding) {
            tracking = false;
            return false;
        }
        double step = tracking ? Math.hypot(x - lastX, z - lastZ) : 0;
        tracking = true;
        lastX = x;
        lastZ = z;
        if (step > MAX_STEP) return false;
        total += step;
        long reached = (long) (total / MARK);
        if (reached <= marks) return false;
        marks = reached;
        return true;
    }

    public double total() {
        return total;
    }
}
