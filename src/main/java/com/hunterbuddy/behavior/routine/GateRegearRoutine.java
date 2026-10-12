package com.hunterbuddy.behavior.routine;

import com.hunterbuddy.behavior.hooks.Contributor;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.GateWait;
import java.util.List;
import java.util.Objects;
import meteordevelopment.meteorclient.settings.SettingGroup;

public final class GateRegearRoutine extends CoreRoutine {
    public GateRegearRoutine() {
        super("gate-regear", Hook.REGEAR, Contributor.ROUTINE);
    }

    @Override
    public void onRegister(SettingGroup settings) {
        Objects.requireNonNull(settings);
    }

    @Override
    protected List<HookEntry> buildEntries(RoutineContext context) {
        return List.of(entry(new Condition("regear running or pending", sensors -> sensors.regearRunning() || sensors.regearPending()),
            new GateWait("regear gate", sensors -> sensors.regearRunning() || sensors.regearPending(), 900_000)));
    }

    @Override
    protected String readyDetail() {
        return "AutoFlyingRegear; wait=15min";
    }
}
