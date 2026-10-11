package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.core.Sensors;

public final class SimulatedSensors {
    private SimulatedSensors() {
    }

    public static Sensors idle() {
        return capture(false, false, false, false, 0, 0);
    }

    public static Sensors capture(boolean dead, boolean eating, boolean combat, boolean gliding, long blocks, long setbacks) {
        return new Sensors(
            0, 64, 0, 0, 64, 0,
            0, 0, 0, 0, 0,
            !gliding, false, false, false,
            gliding, false, false,
            dead ? 0 : 20, 20, 300, dead, false,
            -1, 0, 0,
            false, false, "none", eating,
            false, "simulated:world", 1, 1,
            false, false, true,
            false, false, false,
            false, false, "none",
            false, false, blocks,
            0, setbacks, false, false,
            false, eating, combat, null,
            64, 25, 2, 0
        );
    }
}
