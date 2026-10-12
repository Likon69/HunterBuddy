package com.hunterbuddy.behavior.routine;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Timeout;
import java.util.List;
import java.util.Objects;
import meteordevelopment.meteorclient.settings.SettingGroup;

public final class FlightKeeperRoutine extends CoreRoutine {
    private final Node keepFlying;

    public FlightKeeperRoutine(Node keepFlying) {
        super("flight-keeper", Hook.FLIGHT);
        this.keepFlying = Objects.requireNonNull(keepFlying);
    }

    @Override
    public void onRegister(SettingGroup settings) {
        Objects.requireNonNull(settings);
    }

    @Override
    protected List<HookEntry> buildEntries(RoutineContext context) {
        return List.of(entry(new Condition("unclaimed flight", sensors -> sensors.gliding() && sensors.movementHolder() == null),
            new Timeout(12_000, keepFlying)));
    }

    @Override
    protected String readyDetail() {
        return "unclaimed flight; limit=12000 ticks";
    }
}
