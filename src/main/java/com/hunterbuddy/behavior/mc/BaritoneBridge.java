package com.hunterbuddy.behavior.mc;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.pathing.goals.Goal;
import baritone.api.process.IBaritoneProcess;
import baritone.api.utils.IInputOverrideHandler;
import java.util.List;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;

public final class BaritoneBridge {
    private final BaritoneEvents events = new BaritoneEvents();
    private IBaritone baritone;
    private boolean resolved;

    private IBaritone instance() {
        if (!resolved) {
            resolved = true;
            FabricLoader loader = FabricLoader.getInstance();
            if (loader.isModLoaded("baritone-meteor") || loader.isModLoaded("baritone")) {
                baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
                if (baritone != null) baritone.getGameEventHandler().registerEventListener(events);
            }
        }
        return baritone;
    }

    public Optional<IInputOverrideHandler> inputs() {
        return Optional.ofNullable(instance()).map(IBaritone::getInputOverrideHandler);
    }

    public BaritoneSnapshot capture() {
        IBaritone current = instance();
        if (current == null) return new BaritoneSnapshot(false, false, false, false, false, false, "none", false, false);
        boolean controlled = List.of(current.getCustomGoalProcess(), current.getFollowProcess(), current.getMineProcess(),
            current.getBuilderProcess(), current.getExploreProcess(), current.getFarmProcess(), current.getGetToBlockProcess(),
            current.getElytraProcess()).stream().anyMatch(IBaritoneProcess::isActive);
        return new BaritoneSnapshot(true, current.getPathingBehavior().isPathing(), current.getPathingBehavior().hasPath(),
            current.getCustomGoalProcess().isActive(), current.getPathingBehavior().getInProgress().isPresent(), controlled,
            events.last(), current.getElytraProcess().isActive(), current.getElytraProcess().currentDestination() != null);
    }

    public Settings settings() {
        requireInstance();
        return BaritoneAPI.getSettings();
    }

    public long failures() {
        return events.failures();
    }

    public boolean belongsTo(Goal goal) {
        IBaritone current = instance();
        if (current == null) return false;
        if (List.of(current.getFollowProcess(), current.getMineProcess(), current.getBuilderProcess(), current.getExploreProcess(),
            current.getFarmProcess(), current.getGetToBlockProcess(), current.getElytraProcess()).stream().anyMatch(IBaritoneProcess::isActive)) return false;
        if (current.getCustomGoalProcess().isActive()) return current.getCustomGoalProcess().getGoal() == goal;
        Goal pathGoal = current.getPathingBehavior().getGoal();
        return pathGoal == null || pathGoal == goal;
    }

    public void start(Goal goal) {
        IBaritone current = requireInstance();
        if (current.getElytraProcess().isActive()) current.getPathingBehavior().forceCancel();
        current.getCustomGoalProcess().setGoalAndPath(goal);
    }

    public boolean cancel() {
        return requireInstance().getPathingBehavior().cancelEverything();
    }

    public void forceCancel() {
        requireInstance().getPathingBehavior().forceCancel();
    }

    public void clearInputs() {
        requireInstance().getInputOverrideHandler().clearAllKeys();
    }

    private IBaritone requireInstance() {
        return Optional.ofNullable(instance()).orElseThrow(() -> new IllegalStateException("Baritone is unavailable"));
    }
}
