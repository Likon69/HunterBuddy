package com.hunterbuddy.behavior.routine;

import com.hunterbuddy.behavior.hooks.Contributor;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.GateWait;
import java.util.List;
import java.util.Objects;
import meteordevelopment.meteorclient.settings.SettingGroup;

public final class GateCombatRoutine extends CoreRoutine {
    public GateCombatRoutine() {
        super("gate-combat", Hook.COMBAT, Contributor.ROUTINE);
    }

    @Override
    public void onRegister(SettingGroup settings) {
        Objects.requireNonNull(settings);
    }

    @Override
    protected List<HookEntry> buildEntries(RoutineContext context) {
        GateWait wait = new GateWait("combat gate", ctx -> ctx.sensors().combatTarget() || ctx.series().attackedWithin(100), 60_000);
        HookEntry entry = entry(new Condition("combat target or recent attack", sensors -> sensors.combatTarget()
            || context.ticks().series().attackedWithin(100)), wait);
        return List.of(new HookEntry(entry.hook(), entry.owner(), entry.contributor(), entry.priority(), entry.guard(), false, wait));
    }

    @Override
    protected String readyDetail() {
        return "KillAuraPlus; attacks=100 ticks; wait=60s";
    }
}
