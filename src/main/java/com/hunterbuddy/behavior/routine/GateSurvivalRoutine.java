package com.hunterbuddy.behavior.routine;

import com.hunterbuddy.behavior.hooks.Contributor;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.GateWait;
import java.util.List;
import java.util.Objects;
import meteordevelopment.meteorclient.settings.SettingGroup;

public final class GateSurvivalRoutine extends CoreRoutine {
    public GateSurvivalRoutine() {
        super("gate-survival", Hook.SURVIVAL, Contributor.ROUTINE);
    }

    @Override
    public void onRegister(SettingGroup settings) {
        Objects.requireNonNull(settings);
    }

    @Override
    protected List<HookEntry> buildEntries(RoutineContext context) {
        return List.of(entry(new Condition("eating or critical health", sensors -> sensors.eating() || sensors.health() <= 6),
            new GateWait("survival gate", ctx -> ctx.sensors().eating() || ctx.sensors().health() <= 6, 60_000), false));
    }

    @Override
    protected String readyDetail() {
        return "AutoEatSync; critical health <= 6; wait=60s";
    }
}
