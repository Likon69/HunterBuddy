package com.hunterbuddy.modules.regear.arealoader;

import com.hunterbuddy.modules.ElytraRecast;
import com.hunterbuddy.modules.Pitch40;
import com.hunterbuddy.modules.RocketFly;
import com.hunterbuddy.modules.WaypointFollower;
import com.hunterbuddy.modules.regear.util.BaritoneHelper;
import meteordevelopment.meteorclient.systems.modules.Modules;

final class AreaFlightModes {
    private final AreaLoader area;
    private final AreaDebug debug;
    private final Pitch40Tweaks pitch40Tweaks = new Pitch40Tweaks();

    private AreaLoader.OverworldFlightMode chosenMode;
    private boolean pitch40EnabledByUs;
    private boolean rocketFlyEnabledByUs;

    AreaFlightModes(AreaLoader area, AreaDebug debug) {
        this.area = area;
        this.debug = debug;
    }

    void engageOverworld() {
        chosenMode = area.overworldFlightMode.get();
        switch (chosenMode) {
            case PITCH40 -> enablePitch40();
            case ROCKETS -> enableRocketFly();
            default -> {
            }
        }
    }

    boolean engageNether() {
        if (area.netherPathMode.get() != AreaLoader.NetherPathMode.BARITONE_ELYTRA) return true;
        if (BaritoneHelper.isAvailable()) return true;
        debug.info("Baritone is not installed, Nether pathing is unavailable");
        return false;
    }

    boolean drivesPitch() {
        return chosenMode == AreaLoader.OverworldFlightMode.PITCH40 || chosenMode == AreaLoader.OverworldFlightMode.ROCKETS;
    }

    private void enablePitch40() {
        Pitch40 pitch40 = Modules.get().get(Pitch40.class);
        if (pitch40 == null) return;
        if (!pitch40.isActive()) {
            pitch40.toggle();
            pitch40EnabledByUs = true;
        }
        if (area.pitch40AutoFirework.get()) {
            pitch40Tweaks.forceAutoFirework(pitch40);
        }
        if (area.pitch40DisableAutoBoundAdjust.get()) {
            pitch40Tweaks.disableAutoBoundAdjust(pitch40);
        }
    }

    private void enableRocketFly() {
        RocketFly rocketFly = Modules.get().get(RocketFly.class);
        if (rocketFly == null || rocketFly.isActive()) return;
        rocketFly.toggle();
        rocketFlyEnabledByUs = true;
    }

    void release() {
        pitch40Tweaks.restore();
        releasePitch40();
        releaseRocketFly();
        chosenMode = null;
    }

    private void releasePitch40() {
        Pitch40 pitch40 = Modules.get().get(Pitch40.class);
        if (pitch40EnabledByUs && pitch40 != null && pitch40.isActive() && !isFollowerManagingPitch40()) {
            pitch40.toggle();
        }
        pitch40EnabledByUs = false;
        if (chosenMode != AreaLoader.OverworldFlightMode.PITCH40) return;
        ElytraRecast recast = Modules.get().get(ElytraRecast.class);
        if (recast != null && recast.isActive()) {
            recast.resetToMonitoring();
        }
    }

    private void releaseRocketFly() {
        RocketFly rocketFly = Modules.get().get(RocketFly.class);
        if (rocketFlyEnabledByUs && rocketFly != null && !isFollowerManagingRocketFly()) {
            rocketFly.resetYLock();
            if (rocketFly.isActive()) {
                rocketFly.toggle();
            }
        }
        rocketFlyEnabledByUs = false;
    }

    private boolean isFollowerManagingPitch40() {
        WaypointFollower follower = Modules.get().get(WaypointFollower.class);
        return follower != null && follower.isManagingPitch40();
    }

    private boolean isFollowerManagingRocketFly() {
        WaypointFollower follower = Modules.get().get(WaypointFollower.class);
        return follower != null && follower.isManagingRocketFly();
    }
}
