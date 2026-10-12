package com.hunterbuddy.behavior.routine;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.Sensors;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.If;
import com.hunterbuddy.behavior.nodes.Selector;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import meteordevelopment.meteorclient.settings.SettingGroup;

public final class DeathRoutine extends CoreRoutine {
    private RoutineContext context;

    public DeathRoutine() {
        super("death", Hook.DEATH);
    }

    @Override
    public void onRegister(SettingGroup settings) {
        Objects.requireNonNull(settings);
    }

    @Override
    protected List<HookEntry> buildEntries(RoutineContext context) {
        this.context = context;
        Node behavior = new Selector("death-policy", Arrays.stream(DeathPolicy.values())
            .map(policy -> new If(new Condition("death-policy-" + policy.name(),
                sensors -> context.deathPolicy() == policy), policy.behavior()))
            .toArray(Node[]::new));
        return List.of(entry(new Condition("dead", Sensors::dead), behavior));
    }

    @Override
    protected String readyDetail() {
        return context == null ? "death policy=not installed" : "death policy=" + context.deathPolicy();
    }
}
