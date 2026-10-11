package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Sensors;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.Objects;
import java.util.function.Predicate;

public final class Condition extends Node {
    private final Predicate<Sensors> predicate;

    public Condition(String name, Predicate<Sensors> predicate) {
        super(name);
        this.predicate = Objects.requireNonNull(predicate);
    }

    public boolean test(Sensors sensors) {
        return predicate.test(sensors);
    }

    public Condition not() {
        return new Condition("not(" + name() + ")", predicate.negate());
    }

    public Condition and(Condition other) {
        return new Condition(name() + " & " + other.name(), predicate.and(other.predicate));
    }

    public Condition or(Condition other) {
        return new Condition(name() + " | " + other.name(), predicate.or(other.predicate));
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        return test(ctx.sensors()) ? Success.INSTANCE : fail(FailureCode.CONDITION_FALSE, name());
    }
}
