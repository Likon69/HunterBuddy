package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.core.Sensors;

public final class SimulatedSensors {
    private SimulatedSensors() {
    }

    public static Sensors idle() {
        return new Sensors(
            0, 64, 0, 0, 64, 0,
            0, 0, 0, 0, 0,
            true, false, false, false,
            false, false, false,
            20, 20, 300, false, false,
            -1, 0, 0,
            false, false, "none", false,
            false, "simulated:world", 1, 1,
            false, false, true,
            false, false, false,
            false, false, "none",
            false, false, 0,
            0, 0, false, false,
            false, false, false, null,
            64, 25, 2, 0
        );
    }
}
