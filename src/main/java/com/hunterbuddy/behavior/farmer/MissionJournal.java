package com.hunterbuddy.behavior.farmer;

import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.ArrayList;
import java.util.List;

public final class MissionJournal {
    private static final Key<Integer> STEP = new Key<>("farmer-step", Integer.class, Scope.WORLD, true);
    private static final Key<String> MILESTONE = new Key<>("farmer-milestone", String.class, Scope.WORLD, true);
    private final String owner;
    private final List<Key<Long>> delivered;
    private final List<Key<Long>> carried;
    private final List<Key<Long>> inProgress;

    public MissionJournal(int maxSteps) {
        this(maxSteps, null);
    }

    public MissionJournal(int maxSteps, String owner) {
        this.owner = owner;
        delivered = perStep(maxSteps, "farmer-delivered-");
        carried = perStep(maxSteps, "farmer-carried-");
        inProgress = perStep(maxSteps, "farmer-in-progress-");
    }

    public String owner(TickContext ctx) {
        return owner == null ? ctx.owner() : owner;
    }

    private static List<Key<Long>> perStep(int maxSteps, String prefix) {
        List<Key<Long>> keys = new ArrayList<>(maxSteps);
        for (int i = 0; i < maxSteps; i++) keys.add(new Key<>(prefix + i, Long.class, Scope.WORLD, true));
        return List.copyOf(keys);
    }

    public int currentStep(TickContext ctx) {
        return currentStep(ctx, owner(ctx));
    }

    public int currentStep(TickContext ctx, String owner) {
        return ctx.bb().get(owner == null ? owner(ctx) : owner, STEP).orElse(0);
    }

    public void currentStep(TickContext ctx, int stepIndex) {
        ctx.bb().set(owner(ctx), STEP, stepIndex);
    }

    public long delivered(TickContext ctx, int stepIndex) {
        return ctx.bb().get(owner(ctx), delivered.get(stepIndex)).orElse(0L);
    }

    public void delivered(TickContext ctx, int stepIndex, long value) {
        ctx.bb().set(owner(ctx), delivered.get(stepIndex), value);
    }

    public long carried(TickContext ctx, int stepIndex) {
        return ctx.bb().get(owner(ctx), carried.get(stepIndex)).orElse(0L);
    }

    public void carried(TickContext ctx, int stepIndex, long value) {
        ctx.bb().set(owner(ctx), carried.get(stepIndex), value);
    }

    public long inProgress(TickContext ctx, int stepIndex) {
        return ctx.bb().get(owner(ctx), inProgress.get(stepIndex)).orElse(0L);
    }

    public void inProgress(TickContext ctx, int stepIndex, long value) {
        ctx.bb().set(owner(ctx), inProgress.get(stepIndex), value);
    }

    public void milestone(TickContext ctx, String label) {
        milestone(ctx, owner(ctx), label);
    }

    public void milestone(TickContext ctx, String owner, String label) {
        ctx.bb().set(this.owner == null ? owner : this.owner, MILESTONE, label);
        ctx.checkpoint(this.owner == null ? owner : this.owner);
    }

    public String milestone(TickContext ctx) {
        return ctx.bb().get(owner(ctx), MILESTONE).orElse("");
    }

    public void equipment(TickContext ctx, String name, String state) {
        equipment(ctx, owner(ctx), name, state);
    }

    public void equipment(TickContext ctx, String owner, String name, String state) {
        ctx.bb().set(this.owner == null ? owner : this.owner, new Key<>("farmer-equipment-" + name, String.class, Scope.WORLD, true), state);
    }

    public String equipment(TickContext ctx, String name) {
        return ctx.bb().get(owner(ctx), new Key<>("farmer-equipment-" + name, String.class, Scope.WORLD, true)).orElse("");
    }
}
