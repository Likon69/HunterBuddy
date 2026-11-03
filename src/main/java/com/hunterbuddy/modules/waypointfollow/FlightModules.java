package com.hunterbuddy.modules.waypointfollow;

import com.hunterbuddy.modules.ElytraRecast;
import com.hunterbuddy.modules.Pitch40;
import com.hunterbuddy.modules.RocketFly;
import com.hunterbuddy.modules.WaypointFollower.FlightMode;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

public final class FlightModules {
    private RocketFly rocketFly;
    private Pitch40 pitch40;
    private ElytraRecast elytraRecast;

    public void bind() {
        rocketFly = Modules.get().get(RocketFly.class);
        pitch40 = Modules.get().get(Pitch40.class);
        elytraRecast = Modules.get().get(ElytraRecast.class);
    }

    public Pitch40 pitch40() {
        return pitch40;
    }

    public ElytraRecast elytraRecast() {
        return elytraRecast;
    }

    public Module moduleOf(FlightMode mode) {
        return switch (mode) {
            case RocketFly -> rocketFly;
            case Pitch40 -> pitch40;
            case None -> null;
        };
    }

    public boolean enable(FlightMode mode) {
        Module module = moduleOf(mode);
        if (module == null || module.isActive()) return false;
        module.toggle();
        return true;
    }

    public void disable(FlightMode mode) {
        Module module = moduleOf(mode);
        if (module != null && module.isActive()) module.toggle();
    }

    public void releaseAll() {
        for (Module module : new Module[] {rocketFly, pitch40, elytraRecast}) {
            if (module != null && module.isActive()) module.toggle();
        }
    }
}
