package com.hunterbuddy.behavior.mc;

import com.google.gson.Gson;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.WorldKnowledge;

public final class FlightPerformance {
    private static final Gson GSON = new Gson();
    private static final Key<String> DATA = new Key<>("flight-performance", String.class, Scope.WORLD, true);
    public double metres;
    public long rockets;
    public long durability;

    public static FlightPerformance load(TickContext ctx) {
        String data = ctx.bb().get(WorldKnowledge.OWNER, DATA).orElse("");
        FlightPerformance result = data.isEmpty() ? new FlightPerformance() : GSON.fromJson(data, FlightPerformance.class);
        if (result == null || !Double.isFinite(result.metres) || result.metres < 0 || result.rockets < 0 || result.durability < 0) {
            throw new IllegalStateException("invalid saved flight measurements");
        }
        return result;
    }

    public double rocketsPerKm() {
        return metres < 500 ? 12 : Math.max(4, rockets * 1000.0 / metres * 1.25);
    }

    public double durabilityPerKm() {
        return metres < 500 ? 40 : Math.max(30, durability * 1000.0 / metres * 1.25);
    }

    public void save(TickContext ctx) {
        ctx.bb().set(WorldKnowledge.OWNER, DATA, GSON.toJson(this));
        ctx.checkpoint(WorldKnowledge.OWNER);
    }
}
