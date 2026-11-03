package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.recast.AgeGate;
import com.hunterbuddy.modules.recast.FlightGear;
import com.hunterbuddy.modules.recast.GliderWearer;
import com.hunterbuddy.modules.recast.PitchSteering;
import com.hunterbuddy.modules.recast.RecastCounters;
import com.hunterbuddy.modules.recast.RecastSettings;
import com.hunterbuddy.modules.recast.RocketLauncher;
import com.hunterbuddy.modules.regear.util.ElytraTakeoff;
import com.hunterbuddy.modules.regear.util.Utils;
import com.hunterbuddy.util.GliderSwap;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;

public class ElytraRecast extends Module {
    private static final int NETHER_CLIMB_TICKS = 10;
    private static final double PITCH40_BOUND_MARGIN = 10.0;
    private static final String UP_ARROW = "↑";

    private enum RocketOutcome {
        FIRED,
        UNAVAILABLE,
        DISABLED
    }

    private enum State {
        MONITORING,
        JUMPING,
        ACTIVATING,
        ASCENDING
    }

    private final RecastSettings config = new RecastSettings(settings);
    private final RecastCounters counters = new RecastCounters();
    private final RocketLauncher rocketLauncher = new RocketLauncher();
    private final GliderWearer wearer = new GliderWearer();
    private final ElytraTakeoff takeoff = new ElytraTakeoff();
    private final AgeGate preTickGate = new AgeGate();
    private final AgeGate postTickGate = new AgeGate();
    private final ElytraTakeoff.Listener takeoffListener = new TakeoffOutcome();

    private State state;
    private Pitch40 pitch40;

    public ElytraRecast() {
        super(
            HunterBuddyAddon.HUNT_CATEGORY,
            "ElytraRecast",
            "Flight recovery fallback that monitors and recovers when you stop flying or drop too low."
        );
    }

    public boolean isRecovering() {
        return takeoff.isActive() || state == State.ASCENDING;
    }

    public boolean isAscending() {
        return state == State.ASCENDING;
    }

    public void resetToMonitoring() {
        if (!isActive()) return;
        state = State.MONITORING;
        counters.clearKeepingPause();
        takeoff.cancel();
    }

    public void requestTakeoff() {
        if (!isActive() || mc.player == null) return;
        wearer.clearPending();
        counters.clearPause();
        beginRecovery("manual takeoff requested");
    }

    @Override
    public String getInfoString() {
        if (state == null) return null;
        if (takeoff.isActive()) return "TAKEOFF " + takeoff.getPhase();
        if (mc.player == null) return state.name();
        return switch (state) {
            case MONITORING -> monitoringInfo(mc.player);
            case ASCENDING -> ascendingInfo(mc.player);
            default -> "TAKEOFF";
        };
    }

    @Override
    public void onActivate() {
        wearer.clearPending();
        if (mc.player == null) return;
        if (!FlightGear.hasElytra(mc.player)) {
            error("No elytra equipped!");
            toggle();
            return;
        }
        counters.clear();
        takeoff.cancel();
        pitch40 = Modules.get().get(Pitch40.class);
        state = State.MONITORING;
        debug("Monitoring flight...");
    }

    @Override
    public void onDeactivate() {
        wearer.clearPending();
        takeoff.cancel();
        Utils.setPressed(mc.options.jumpKey, false);
        state = null;
    }

    @EventHandler
    private void onPreTick(TickEvent.Pre event) {
        if (mc.player != null) wearer.retryPendingWear(mc.player);
        if (!canRun() || preTickGate.alreadySeen(mc.player.age)) return;
        preTickGate.record(mc.player.age);
        if (takeoff.isActive()) {
            takeoff.tickPre();
            return;
        }
        counters.tick();
        if (state == State.MONITORING) tickMonitoring();
        else if (state == State.ASCENDING) tickAscending();
    }

    @EventHandler
    private void onPostTick(TickEvent.Post event) {
        if (mc.player == null || !takeoff.isActive() || postTickGate.alreadySeen(mc.player.age)) return;
        postTickGate.record(mc.player.age);
        takeoff.tickPost();
    }

    private boolean canRun() {
        return mc.player != null && mc.world != null && state != null && FlightGear.hasElytra(mc.player);
    }

    private void tickMonitoring() {
        ClientPlayerEntity player = mc.player;
        if (counters.isPaused()) {
            counters.countPauseDown();
            return;
        }
        if (GliderSwap.rocketAttached(player)) return;
        if (FlightGear.isNether(mc.world)) watchNetherFlight(player);
        else watchAltitude(player);
    }

    private void watchNetherFlight(ClientPlayerEntity player) {
        if (player.isGliding()) {
            counters.clearNotGliding();
            return;
        }
        if (counters.countNotGliding() < config.fallDelay.get()) return;
        debug("Not flying for " + config.fallDelay.get() + " ticks.");
        beginRecovery("recovery triggered");
        counters.clearNotGliding();
    }

    private void watchAltitude(ClientPlayerEntity player) {
        if (player.getY() >= minimumAltitude()) return;
        if (player.isGliding() && isPitch40Active()) return;
        debug("Below minimum altitude Y=" + (int) player.getY());
        beginRecovery("below minimum altitude");
    }

    private double minimumAltitude() {
        if (isPitch40Active() && config.usePitch40Bounds.get()) {
            return pitch40.pitch40LowerBounds.get() - PITCH40_BOUND_MARGIN;
        }
        return config.minAltitude.get();
    }

    private boolean isPitch40Active() {
        return pitch40 != null && pitch40.isActive();
    }

    private void beginRecovery(String reason) {
        if (!FlightGear.hasRockets()) {
            if (config.disableIfNoRockets.get()) {
                disableWithError("No firework rockets available! Disabling ElytraRecast.");
                return;
            }
            warning("No firework rockets available, the recovery may fail.");
        }
        debug(reason);
        counters.restartTicks();
        counters.primeRocketTimer(config.rocketDelay.get());
        wearer.wearForRecovery(mc.player);
        if (mc.player.isGliding()) enterAscending();
        else startTakeoff();
    }

    private void startTakeoff() {
        takeoff.start(-1, takeoffListener);
    }

    private void enterAscending() {
        state = State.ASCENDING;
        counters.primeRocketTimer(config.rocketDelay.get());
        counters.restartTicks();
        useRocketOrDisable("No rockets for ascent! Disabling.");
    }

    private void returnToMonitoring() {
        state = State.MONITORING;
        counters.restartTicks();
        counters.clearNetherRocket();
    }

    private void tickAscending() {
        ClientPlayerEntity player = mc.player;
        if (!player.isGliding()) {
            recoverLostFlight();
        } else if (isPitch40Active()) {
            returnToMonitoring();
        } else if (FlightGear.isNether(mc.world)) {
            ascendInNether(player);
        } else {
            ascendInOverworld(player);
        }
    }

    private void recoverLostFlight() {
        if (!FlightGear.hasRockets() && config.disableIfNoRockets.get()) {
            disableWithError("Lost flight and no rockets! Disabling.");
            return;
        }
        startTakeoff();
    }

    private void ascendInNether(ClientPlayerEntity player) {
        PitchSteering.steer(player, config.targetPitch.get(), config.rotationSpeed.get());
        if (!counters.netherRocketUsed()) {
            RocketOutcome outcome = useRocketOrDisable("No rockets for Nether recovery! Disabling.");
            if (outcome == RocketOutcome.DISABLED) return;
            if (outcome == RocketOutcome.FIRED) counters.markNetherRocketUsed();
        }
        if (counters.ticks() > NETHER_CLIMB_TICKS) {
            debug("Nether recovery complete - baritone takes over.");
            returnToMonitoring();
        }
    }

    private void ascendInOverworld(ClientPlayerEntity player) {
        if (player.getY() >= config.targetAltitude.get()) {
            debug("Target altitude Y=" + config.targetAltitude.get() + " reached! Resuming monitoring.");
            returnToMonitoring();
            return;
        }
        PitchSteering.steer(player, config.targetPitch.get(), config.rotationSpeed.get());
        if (counters.rocketTicks() < config.rocketDelay.get()) return;
        if (useRocketOrDisable("Out of rockets during ascent! Disabling.") == RocketOutcome.FIRED) {
            counters.restartRocketTimer();
        }
    }

    private RocketOutcome useRocketOrDisable(String exhaustedMessage) {
        if (rocketLauncher.use()) return RocketOutcome.FIRED;
        if (rocketLauncher.isWaiting() || !config.disableIfNoRockets.get()) return RocketOutcome.UNAVAILABLE;
        disableWithError(exhaustedMessage);
        return RocketOutcome.DISABLED;
    }

    private void disableWithError(String message) {
        error(message);
        toggle();
    }

    private void debug(String message) {
        if (config.debugMessages.get()) info(message);
    }

    private void debugWarning(String message) {
        if (config.debugMessages.get()) warning(message);
    }

    private String monitoringInfo(ClientPlayerEntity player) {
        String altitude = String.format("Y=%.0f", player.getY());
        if (FlightGear.isNether(mc.world)) return altitude;
        return altitude + String.format(" (min:%.0f)", minimumAltitude());
    }

    private String ascendingInfo(ClientPlayerEntity player) {
        String altitude = UP_ARROW + String.format("%.0f", player.getY());
        if (FlightGear.isNether(mc.world)) return altitude;
        return altitude + "/" + config.targetAltitude.get();
    }

    private final class TakeoffOutcome implements ElytraTakeoff.Listener {
        @Override
        public void onTakeoffSuccess(double speedBps) {
            enterAscending();
            debug(String.format("Recovery takeoff complete at %.1f b/s", speedBps));
        }

        @Override
        public void onTakeoffFailed(String reason) {
            state = State.MONITORING;
            counters.pause(RecastCounters.FAILED_TAKEOFF_PAUSE_TICKS);
            counters.restartTicks();
            debugWarning("Recovery takeoff failed: " + reason);
        }

        @Override
        public void onTakeoffDebug(String message) {
            debug(message);
        }
    }
}
