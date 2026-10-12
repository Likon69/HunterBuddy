package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.core.Sensors;

public final class SimulatedSensors {
    private SimulatedSensors() {
    }

    public static Sensors idle() {
        return capture(false, false, false, false, 0, 0);
    }

    public static Sensors capture(boolean dead, boolean eating, boolean combat, boolean gliding, long blocks, long setbacks) {
        return capture(dead, eating, combat, gliding, blocks, setbacks, 0);
    }

    public static Sensors session(boolean inQueue, boolean screenOpen, boolean screenOurs, boolean dead) {
        return session(inQueue, screenOpen, screenOurs, dead, "simulated screen");
    }

    public static Sensors session(boolean inQueue, boolean screenOpen, boolean screenOurs, boolean dead, String screenType) {
        return capture(dead, false, false, false, 0, 0, 0, inQueue, screenOpen, screenOurs, screenType);
    }

    public static Sensors position(double x) {
        return capture(false, false, false, false, 0, 0, x);
    }

    public static Sensors position(double x, long setbacks) {
        return capture(false, false, false, false, 0, setbacks, x);
    }

    private static Sensors capture(boolean dead, boolean eating, boolean combat, boolean gliding, long blocks, long setbacks, double x) {
        return capture(dead, eating, combat, gliding, blocks, setbacks, x, false, false, false, "none");
    }

    private static Sensors capture(boolean dead, boolean eating, boolean combat, boolean gliding, long blocks, long setbacks,
                                   double x, boolean inQueue, boolean screenOpen, boolean screenOurs, String screenType) {
        return new Sensors(
            x, 64, 0, (int) Math.floor(x), 64, 0,
            0, 0, 0, 0, 0,
            !gliding, false, false, false,
            gliding, false, false,
            dead ? 0 : 20, 20, 300, dead, false,
            -1, 0, 0,
            screenOpen, screenOurs, screenOpen ? screenType : "none", eating,
            false, "simulated:world", 1, 1,
            inQueue, false, true,
            false, false, false,
            false, false, "none",
            false, false, blocks,
            0, setbacks, false, false,
            false, eating, combat, null,
            64, 25, 2, 0
        );
    }
}
