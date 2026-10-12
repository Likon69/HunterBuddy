package com.hunterbuddy.behavior.routine;

import com.hunterbuddy.behavior.core.Sensors;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.mc.RoutineSettings;
import com.hunterbuddy.behavior.nodes.Condition;
import java.util.List;
import java.util.function.Supplier;
import meteordevelopment.meteorclient.settings.SettingGroup;

public final class DeathRoutine extends CoreRoutine {
    private Supplier<DeathPolicy> policy = () -> DeathPolicy.STOP;

    public DeathRoutine() {
        super("death", Hook.DEATH);
    }

    @Override
    public void onRegister(SettingGroup settings) {
        policy = RoutineSettings.death(settings);
    }

    private DeathPolicy policy() {
        return policy.get();
    }

    @Override
    protected List<HookEntry> buildEntries(RoutineContext context) {
        return List.of(entry(new Condition("dead", Sensors::dead), policy().behavior()));
    }

    @Override
    protected String readyDetail() {
        return "death policy=" + policy();
    }
}
