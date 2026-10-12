package com.hunterbuddy.behavior.routine;

import com.hunterbuddy.behavior.core.RestartBudget;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.SafeRestart;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import meteordevelopment.meteorclient.settings.SettingGroup;

public final class IdleRoutine extends CoreRoutine {
    private final Consumer<String> alert;
    private final RestartBudget restarts;

    public IdleRoutine(Consumer<String> alert, RestartBudget restarts) {
        super("idle", Hook.IDLE);
        this.alert = Objects.requireNonNull(alert);
        this.restarts = Objects.requireNonNull(restarts);
    }

    @Override
    public void onRegister(SettingGroup settings) {
        Objects.requireNonNull(settings);
    }

    @Override
    protected List<HookEntry> buildEntries(RoutineContext context) {
        return List.of(entry(new Condition("fallback", sensors -> true), new SafeRestart(alert, restarts)));
    }

    @Override
    protected String readyDetail() {
        return "safe idle; restarts=" + restarts.count();
    }
}
