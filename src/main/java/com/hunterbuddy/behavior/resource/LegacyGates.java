package com.hunterbuddy.behavior.resource;

import com.hunterbuddy.behavior.core.Sensors;
import com.hunterbuddy.behavior.hooks.Hook;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;

public final class LegacyGates {
    private BooleanSupplier ownMining = () -> false;
    private BooleanSupplier ownBaritone = () -> false;
    private List<GateOwner> active = List.of();
    private boolean borrowed;

    public void connect(BooleanSupplier ownMining, BooleanSupplier ownBaritone) {
        this.ownMining = Objects.requireNonNull(ownMining);
        this.ownBaritone = Objects.requireNonNull(ownBaritone);
    }

    public void poll(Sensors sensors) {
        borrowed = sensors.regearRunning() || sensors.regearPending();
        List<GateOwner> next = new ArrayList<>();
        if (sensors.regearRunning() || sensors.regearPending()) next.add(new GateOwner("AutoFlyingRegear", Hook.REGEAR, Set.of(Resource.values()), GatePolicy.LEGACY));
        if (sensors.eating()) next.add(new GateOwner("AutoEatSync", Hook.SURVIVAL, Set.of(Resource.SLOT), GatePolicy.LEGACY));
        if (sensors.combatTarget()) next.add(new GateOwner("KillAuraPlus", Hook.COMBAT, Set.of(Resource.LOOK, Resource.SLOT), GatePolicy.LEGACY));
        if (sensors.mlepMining() && !ownMining.getAsBoolean() && !ownBaritone.getAsBoolean()) {
            next.add(new GateOwner("HMine", Hook.BOT_PRE, Set.of(Resource.MINING), GatePolicy.LEGACY));
        }
        active = List.copyOf(next);
    }

    public List<GateOwner> active() {
        return active;
    }

    public boolean borrowedBaritone() {
        return borrowed;
    }

    public List<String> status() {
        return active.stream().map(gate -> gate.name() + " " + gate.resources()).toList();
    }
}
