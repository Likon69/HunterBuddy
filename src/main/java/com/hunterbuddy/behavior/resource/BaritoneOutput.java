package com.hunterbuddy.behavior.resource;

import baritone.api.Settings;
import baritone.api.utils.interfaces.IGoalRenderPos;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.BaritoneSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;

public final class BaritoneOutput {
    private final BaritoneBridge bridge;
    private final Leases leases;
    private final ConsumablePolicy consumables;
    private final LongSupplier ticks;
    private Lease lease;
    private BaritoneIntent intent;
    private Settings settings;
    private Saved saved;
    private List<Item> appliedItems;
    private List<Block> appliedProtected;
    private Consumer<Signal> events;
    private Stage stage = Stage.IDLE;
    private long revokingAt;
    private long pausedAt;
    private boolean graceReported;
    private BlockPos lastGoal;

    public BaritoneOutput(BaritoneBridge bridge, Leases leases, ConsumablePolicy consumables, LongSupplier ticks) {
        this.bridge = Objects.requireNonNull(bridge);
        this.leases = Objects.requireNonNull(leases);
        this.consumables = Objects.requireNonNull(consumables);
        this.ticks = Objects.requireNonNull(ticks);
    }

    public void apply(BaritoneIntent desired, Lease current, Consumer<Signal> events) {
        if (desired == null) {
            if (stage == Stage.RUNNING && lease != null && lease.state() != LeaseState.GRANTED) stop(lease);
            return;
        }
        if (lease == current && intent == desired) return;
        if (stage != Stage.IDLE) throw new IllegalStateException("Baritone goal still owns its lease");
        lease = current;
        intent = desired;
        if (desired.goal() instanceof IGoalRenderPos goal) lastGoal = goal.getGoalPos();
        this.events = Objects.requireNonNull(events);
        settings = bridge.settings();
        saved = new Saved(settings.allowBreak.value, settings.allowPlace.value,
            List.copyOf(settings.acceptableThrowawayItems.value), List.copyOf(settings.blocksToDisallowBreaking.value));
        stage = Stage.RUNNING;
        settings.allowBreak.value = desired.allowBreak();
        settings.allowPlace.value = desired.allowPlace();
        appliedItems = new ArrayList<>(consumables.throwawayItems(saved.items()));
        appliedProtected = new ArrayList<>(saved.protectedBlocks());
        settings.acceptableThrowawayItems.value = appliedItems;
        settings.blocksToDisallowBreaking.value = appliedProtected;
        bridge.start(desired.goal());
        events.accept(Signal.STARTED);
    }

    public boolean owns(Lease candidate, BaritoneIntent desired) {
        return stage == Stage.RUNNING && lease == candidate && intent == desired;
    }

    public boolean ownsControl() {
        return stage != Stage.IDLE && stage != Stage.BORROWED && stage != Stage.YIELDING && bridge.belongsTo(intent.goal());
    }

    public boolean stop(Lease candidate) {
        if (lease != candidate || stage == Stage.IDLE) return true;
        bridge.pause(false);
        if (stage == Stage.PAUSED || stage == Stage.YIELDING) stage = Stage.RUNNING;
        if (!bridge.belongsTo(intent.goal())) {
            finish(false);
            return true;
        }
        if (stage == Stage.RUNNING) {
            stage = Stage.REVOKING;
            revokingAt = ticks.getAsLong();
            leases.revoke(lease);
            events.accept(Signal.REVOKING);
            if (bridge.cancel()) finish(true);
        }
        return stage == Stage.IDLE;
    }

    public void advance() {
        if (stage == Stage.BORROWED) return;
        if (stage == Stage.PAUSED || stage == Stage.YIELDING) {
            if (stage == Stage.YIELDING && !bridge.belongsTo(intent.goal())) bridge.pause(false);
            if (stage == Stage.PAUSED && !graceReported && ticks.getAsLong() - pausedAt >= GatePolicy.LEGACY.movementGraceTicks() && !bridge.pauseReady()) {
                events.accept(Signal.PAUSE_DELAYED);
                graceReported = true;
            }
            return;
        }
        if (stage != Stage.IDLE && !bridge.belongsTo(intent.goal())) {
            finish(false);
            return;
        }
        if (stage == Stage.RUNNING) {
            if (settings.acceptableThrowawayItems.value.equals(appliedItems)) {
                appliedItems = new ArrayList<>(consumables.throwawayItems(saved.items()));
                settings.acceptableThrowawayItems.value = appliedItems;
            }
            return;
        }
        if (stage != Stage.REVOKING) return;
        BaritoneSnapshot snapshot = bridge.capture();
        if (!snapshot.pathing() && !snapshot.calcInProgress()) finish(true);
        else if (ticks.getAsLong() - revokingAt >= 10) {
            try {
                bridge.forceCancel();
                events.accept(Signal.FORCED_CANCEL);
            } finally {
                finish(true);
            }
        }
    }

    public void release() {
        if (stage == Stage.BORROWED) stage = Stage.IDLE;
        if (lease != null) stop(lease);
    }

    public void worldLost() {
        lastGoal = null;
        if (lease == null) { stage = Stage.IDLE; return; }
        bridge.pause(false);
        if (bridge.belongsTo(intent.goal())) { bridge.forceCancel(); bridge.clearInputs(); }
        finish(false);
    }

    public void suspend(boolean borrowed) {
        if (stage == Stage.IDLE || stage == Stage.BORROWED) {
            if (borrowed) stage = Stage.BORROWED;
            return;
        }
        if (stage == Stage.REVOKING) return;
        if (stage == Stage.RUNNING) {
            pausedAt = ticks.getAsLong();
            graceReported = false;
            events.accept(Signal.PAUSED);
        }
        if (borrowed && stage != Stage.YIELDING) restoreSettings();
        if (stage != Stage.YIELDING) stage = borrowed ? Stage.YIELDING : Stage.PAUSED;
        bridge.pause(stage != Stage.YIELDING || bridge.belongsTo(intent.goal()));
    }

    public boolean resume() {
        if (stage == Stage.BORROWED) {
            BaritoneSnapshot snapshot = bridge.capture();
            if (snapshot.inControl() || snapshot.pathing() || snapshot.calcInProgress()) return false;
            stage = Stage.IDLE;
            return true;
        }
        if (stage != Stage.PAUSED && stage != Stage.YIELDING) return true;
        if (stage == Stage.YIELDING) {
            BaritoneSnapshot snapshot = bridge.capture();
            if (!bridge.retains(intent.goal()) && (snapshot.inControl() || snapshot.pathing() || snapshot.calcInProgress())) return false;
            settings = bridge.settings();
            saved = new Saved(settings.allowBreak.value, settings.allowPlace.value, List.copyOf(settings.acceptableThrowawayItems.value),
                List.copyOf(settings.blocksToDisallowBreaking.value));
            settings.allowBreak.value = intent.allowBreak();
            settings.allowPlace.value = intent.allowPlace();
            appliedItems = new ArrayList<>(consumables.throwawayItems(saved.items()));
            appliedProtected = new ArrayList<>(saved.protectedBlocks());
            settings.acceptableThrowawayItems.value = appliedItems;
            settings.blocksToDisallowBreaking.value = appliedProtected;
        }
        if (!bridge.retains(intent.goal())) {
            BaritoneSnapshot snapshot = bridge.capture();
            if (snapshot.inControl() || snapshot.pathing() || snapshot.calcInProgress()) return false;
            bridge.pause(false);
            bridge.start(intent.goal());
        } else bridge.pause(false);
        stage = Stage.RUNNING;
        events.accept(Signal.RESUMED);
        return true;
    }

    public boolean borrowed() {
        return stage == Stage.YIELDING || stage == Stage.BORROWED;
    }

    private void finish(boolean clearInputs) {
        Lease released = lease;
        try {
            if (clearInputs) bridge.clearInputs();
        } finally {
            try {
                restoreSettings();
                events.accept(Signal.RELEASED);
            } finally {
                stage = Stage.IDLE;
                lease = null;
                intent = null;
                settings = null;
                saved = null;
                appliedItems = null;
                appliedProtected = null;
                events = null;
                leases.release(released);
            }
        }
    }

    public String status() {
        if (stage == Stage.BORROWED) return "BARITONE waiting for legacy handoff";
        return stage == Stage.IDLE ? null : "BARITONE " + lease.owner() + " " + stage + " " + intent.goal();
    }

    public BlockPos lastGoal() { return lastGoal; }

    private void restoreSettings() {
        if (Objects.equals(settings.allowBreak.value, intent.allowBreak())) settings.allowBreak.value = saved.allowBreak();
        if (Objects.equals(settings.allowPlace.value, intent.allowPlace())) settings.allowPlace.value = saved.allowPlace();
        if (Objects.equals(settings.acceptableThrowawayItems.value, appliedItems)) settings.acceptableThrowawayItems.value = new ArrayList<>(saved.items());
        if (Objects.equals(settings.blocksToDisallowBreaking.value, appliedProtected)) settings.blocksToDisallowBreaking.value = new ArrayList<>(saved.protectedBlocks());
    }

    private record Saved(boolean allowBreak, boolean allowPlace, List<Item> items, List<Block> protectedBlocks) {
    }

    public enum Signal {
        STARTED("Baritone goal started"), REVOKING("REVOKING grace=10"), FORCED_CANCEL("forceCancel grace=10"),
        PAUSED("Baritone paused; goal retained"), RESUMED("Baritone resumed; attempt retained"),
        PAUSE_DELAYED("Baritone pause still finishing an unsafe movement after grace=10"),
        RELEASED("Baritone released; settings restored conditionally");

        private final String detail;

        Signal(String detail) {
            this.detail = detail;
        }

        public String detail() {
            return detail;
        }
    }

    private enum Stage {
        IDLE, RUNNING, PAUSED, YIELDING, BORROWED, REVOKING
    }
}
