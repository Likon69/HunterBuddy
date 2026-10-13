package com.hunterbuddy.behavior.resource;

import baritone.api.Settings;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.WorldReader;
import java.util.Objects;
import net.minecraft.util.math.BlockPos;

public final class ElytraOutput {
    private final BaritoneBridge bridge;
    private final WorldReader world;
    private Lease lease;
    private BlockPos destination;
    private Mode mode = Mode.IDLE;
    private Saved saved;
    private boolean rockets;
    private Settings.Setting<Boolean> holdSetting;
    private Settings.Setting<Boolean> standingSetting;

    public ElytraOutput(BaritoneBridge bridge, WorldReader world) { this.bridge = bridge; this.world = world; }

    public void apply(Lease lease, BlockPos destination, boolean rockets) {
        if (this.lease == lease && this.destination.equals(destination) && mode == Mode.ACTIVE) return;
        if (mode != Mode.IDLE) throw new IllegalStateException("elytra output still owns its lease");
        if (!bridge.elytraAvailable() || !world.elytraEquipped()) throw new IllegalStateException("Baritone elytra flight is unavailable");
        if (bridge.capture().inControl() || bridge.capture().pathing() || bridge.capture().calcInProgress()) throw new IllegalStateException("another Baritone process still owns control");
        this.lease = lease;
        this.destination = destination.toImmutable();
        this.rockets = rockets;
        saveSettings();
        mode = Mode.ACTIVE;
        bridge.flyTo(destination);
    }

    private void saveSettings() {
        var settings = bridge.settings();
        holdSetting = booleanSetting("elytraholdfireworks");
        standingSetting = booleanSetting("elytrastandingtakeoff");
        saved = new Saved(holdSetting == null ? false : holdSetting.value, settings.elytraAutoJump.value,
            standingSetting == null ? false : standingSetting.value, settings.elytraAutoSwap.value, settings.allowInventory.value);
        if (holdSetting != null) holdSetting.value = !rockets;
        settings.elytraAutoJump.value = false;
        if (standingSetting != null) standingSetting.value = false;
        settings.elytraAutoSwap.value = false;
        settings.allowInventory.value = false;
    }

    @SuppressWarnings("unchecked")
    private Settings.Setting<Boolean> booleanSetting(String name) {
        Settings.Setting<?> setting = bridge.settings().byLowerName.get(name);
        return setting != null && setting.value instanceof Boolean ? (Settings.Setting<Boolean>) setting : null;
    }

    private void restoreSettings() {
        var settings = bridge.settings();
        if (holdSetting != null && Objects.equals(holdSetting.value, !rockets)) holdSetting.value = saved.hold();
        if (!settings.elytraAutoJump.value) settings.elytraAutoJump.value = saved.jump();
        if (standingSetting != null && !standingSetting.value) standingSetting.value = saved.standing();
        if (!settings.elytraAutoSwap.value) settings.elytraAutoSwap.value = saved.swap();
        if (!settings.allowInventory.value) settings.allowInventory.value = saved.inventory();
    }

    public boolean owns() { return mode != Mode.IDLE && mode != Mode.YIELDING && bridge.ownsFlight(destination); }
    public boolean attached() { return mode != Mode.IDLE; }
    public boolean noRockets() { return owns() && (!rockets || lease.state() != LeaseState.GRANTED); }
    public boolean borrowed() { return mode == Mode.YIELDING; }

    public void suspend(boolean borrowed) {
        if (mode == Mode.IDLE) return;
        if (borrowed && mode != Mode.YIELDING) { restoreSettings(); mode = Mode.YIELDING; }
        else if (mode != Mode.YIELDING) mode = Mode.PAUSED;
        bridge.pause(mode != Mode.YIELDING || bridge.ownsFlight(destination));
    }

    public void advance() { if (mode == Mode.YIELDING && !bridge.ownsFlight(destination)) bridge.pause(false); }

    public boolean resume() {
        if (mode == Mode.IDLE || mode == Mode.ACTIVE) return true;
        if (!bridge.ownsFlight(destination) && (bridge.capture().inControl() || bridge.capture().pathing() || bridge.capture().calcInProgress())) return false;
        if (mode == Mode.YIELDING) saveSettings();
        bridge.pause(false);
        if (!bridge.ownsFlight(destination)) bridge.flyTo(destination);
        mode = Mode.ACTIVE;
        return true;
    }

    public boolean stop(Lease lease) {
        if (this.lease != lease || mode == Mode.IDLE) return true;
        bridge.pause(false);
        if (bridge.ownsFlight(destination)) { bridge.forceCancel(); bridge.clearInputs(); }
        if (mode != Mode.YIELDING) restoreSettings();
        mode = Mode.IDLE;
        this.lease = null;
        return true;
    }

    public void release() { if (lease != null) stop(lease); }
    public String status() { return mode == Mode.IDLE ? null : "ELYTRA " + mode + " " + destination + " rockets=" + rockets; }
    private record Saved(boolean hold, boolean jump, boolean standing, boolean swap, boolean inventory) { }
    private enum Mode { IDLE, ACTIVE, PAUSED, YIELDING }
}
