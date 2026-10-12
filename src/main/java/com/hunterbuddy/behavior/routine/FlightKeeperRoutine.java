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
        HookEntry entry = entry(new Condition("unclaimed flight", sensors -> sensors.gliding() && sensors.movementHolder() == null),
            new Timeout(12_000, keepFlying));
        String owner = entry.owner();
        entry.guard().stayWhile(new Condition("unclaimed or retained flight", sensors -> sensors.gliding()
            && (sensors.movementHolder() == null || owner.equals(sensors.movementHolder()))));
        return List.of(entry);
    }

    @Override
    protected String readyDetail() {
        return "unclaimed flight; limit=12000 ticks";
    }
}
