package com.hunterbuddy.behavior.mc;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.event.events.PathEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.process.IBaritoneProcess;
import baritone.api.utils.IInputOverrideHandler;
import java.util.List;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;

public final class BaritoneBridge implements AbstractGameEventListener {
    private IBaritone baritone;
    private boolean resolved;
    private volatile String lastPathEvent = "none";

    private IBaritone instance() {
        if (!resolved) {
            resolved = true;
            FabricLoader loader = FabricLoader.getInstance();
            if (loader.isModLoaded("baritone-meteor") || loader.isModLoaded("baritone")) {
                baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
                if (baritone != null) baritone.getGameEventHandler().registerEventListener(this);
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
            lastPathEvent, current.getElytraProcess().isActive(), current.getElytraProcess().currentDestination() != null);
    }

    @Override
    public void onPathEvent(PathEvent event) {
        lastPathEvent = event.name();
    }
}
