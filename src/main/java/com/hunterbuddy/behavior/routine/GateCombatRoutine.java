package com.hunterbuddy.behavior.routine;

import com.hunterbuddy.behavior.hooks.Contributor;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.GateCombat;
import com.hunterbuddy.behavior.resource.Outputs;
import java.util.List;
import java.util.Objects;
import meteordevelopment.meteorclient.settings.SettingGroup;

public final class GateCombatRoutine extends CoreRoutine {
    private final WorldReader world;
    private final BaritoneBridge bridge;
    private final Outputs outputs;

    public GateCombatRoutine(WorldReader world, BaritoneBridge bridge, Outputs outputs) {
        super("gate-combat", Hook.COMBAT, Contributor.ROUTINE);
        this.world = Objects.requireNonNull(world);
        this.bridge = Objects.requireNonNull(bridge);
        this.outputs = Objects.requireNonNull(outputs);
    }

    @Override
    public void onRegister(SettingGroup settings) {
        Objects.requireNonNull(settings);
    }

    @Override
    protected List<HookEntry> buildEntries(RoutineContext context) {
        GateCombat wait = new GateCombat(world, bridge, outputs);
        HookEntry entry = entry(new Condition("combat target or recent attack", sensors -> sensors.combatTarget()
            || context.ticks().series().attackedWithin(100)), wait);
        entry.guard().onFailureCooldown(200);
        return List.of(new HookEntry(entry.hook(), entry.owner(), entry.contributor(), entry.priority(), entry.guard(), false, wait));
    }

    @Override
    protected String readyDetail() {
        return "KillAuraPlus; attacks=100 ticks; wait=60s";
    }
}
