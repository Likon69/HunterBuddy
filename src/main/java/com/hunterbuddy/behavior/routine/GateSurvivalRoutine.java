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
        HookEntry entry = entry(new Condition("AutoEatSync eating", sensors -> sensors.eating()),
            new GateWait("survival gate", ctx -> ctx.sensors().eating(), 60_000, false), false);
        entry.guard().onFailureCooldown(200);
        return List.of(entry);
    }

    @Override
    protected String readyDetail() {
        return "AutoEatSync; wait=60s; cooldown=200 ticks";
    }
}
