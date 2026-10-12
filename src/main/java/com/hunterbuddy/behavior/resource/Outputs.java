package com.hunterbuddy.behavior.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class Outputs {
    private final Leases leases;
    private final Supplier<String> actor;
    private MovementOutput movementOutput;
    private LookOutput lookOutput;
    private SlotOutput slotOutput;
    private LongSupplier nativeTicks;
    private MovementIntent movement;
    private LookIntent look;
    private SlotIntent slot;
    private Lease movementLease;
    private Lease lookLease;
    private Lease slotLease;
    private long begunAt = -1;

    public Outputs(Leases leases, Supplier<String> actor) {
        this.leases = Objects.requireNonNull(leases);
        this.actor = Objects.requireNonNull(actor);
    }

    public void connect(MovementOutput movement, LookOutput look, SlotOutput slot, LongSupplier nativeTicks) {
        if (movementOutput != null) throw new IllegalStateException("outputs already connected");
        Objects.requireNonNull(movement);
        Objects.requireNonNull(look);
        Objects.requireNonNull(slot);
        Objects.requireNonNull(nativeTicks);
        movementOutput = movement;
        lookOutput = look;
        slotOutput = slot;
        this.nativeTicks = nativeTicks;
    }

    public void begin(long tick) {
        if (begunAt == tick) return;
        clear();
        begunAt = tick;
    }

    public void movement(String owner, MovementIntent intent) {
        movementLease = requireLease(owner, Resource.MOVEMENT);
        movement = Objects.requireNonNull(intent);
    }

    public void look(String owner, LookIntent intent) {
        lookLease = requireLease(owner, Resource.LOOK);
        look = Objects.requireNonNull(intent);
    }

    public void slot(String owner, SlotIntent intent) {
        slotLease = requireLease(owner, Resource.SLOT);
        slot = Objects.requireNonNull(intent);
    }

    private Lease requireLease(String owner, Resource resource) {
        if (owner == null || owner.startsWith("plugin:") || !owner.equals(actor.get())) throw new IllegalStateException("invalid output owner");
        return leases.holder(resource).filter(lease -> lease.state() == LeaseState.GRANTED && lease.owner().equals(owner))
            .orElseThrow(() -> new IllegalStateException("output without granted " + resource + " lease: " + owner));
    }

    private boolean valid(Lease lease, Resource resource) {
        return lease != null && lease.state() == LeaseState.GRANTED && leases.holder(resource).orElse(null) == lease;
    }

    public void flush(long tick) {
        if (movementOutput == null) return;
        tick = nativeTicks.getAsLong();
        if (!valid(movementLease, Resource.MOVEMENT)) movement = null;
        if (!valid(lookLease, Resource.LOOK)) look = null;
        if (!valid(slotLease, Resource.SLOT)) slot = null;
        try {
            movementOutput.apply(movement, tick);
            lookOutput.apply(look);
            slotOutput.apply(slot, slotLease, tick);
        } catch (RuntimeException ex) {
            try {
                release(tick);
            } catch (RuntimeException cleanup) {
                if (cleanup != ex) ex.addSuppressed(cleanup);
            }
            throw ex;
        }
    }

    public boolean slotSettled(int selected) {
        return slotOutput == null || slotOutput.settled(selected, nativeTicks.getAsLong());
    }

    public boolean foreignMovement() {
        return movementOutput != null && movementOutput.foreignInput();
    }

    public void discardSlot() {
        if (slotOutput != null) slotOutput.discard();
    }

    public List<String> status() {
        List<String> lines = new ArrayList<>();
        if (movement != null && valid(movementLease, Resource.MOVEMENT)) lines.add("MOVEMENT " + movementLease.owner() + " " + movement);
        if (look != null && valid(lookLease, Resource.LOOK)) lines.add("LOOK " + lookLease.owner() + " " + look);
        if (slot != null && valid(slotLease, Resource.SLOT)) lines.add("SLOT " + slotLease.owner() + " " + slot);
        if (movementOutput != null) {
            List<String> forced = movementOutput.forcedInputs();
            if (!forced.isEmpty()) lines.add("Baritone forced inputs: " + String.join(", ", forced));
        }
        return lines.isEmpty() ? List.of("Outputs: none") : List.copyOf(lines);
    }

    public void release(long tick) {
        clear();
        if (nativeTicks != null) tick = nativeTicks.getAsLong();
        RuntimeException failure = null;
        try {
            if (movementOutput != null) movementOutput.release(tick);
        } catch (RuntimeException ex) {
            failure = ex;
        }
        try {
            if (slotOutput != null) slotOutput.release(tick);
        } catch (RuntimeException ex) {
            if (failure == null) failure = ex;
            else if (failure != ex) failure.addSuppressed(ex);
        }
        if (failure != null) throw failure;
    }

    private void clear() {
        movement = null;
        look = null;
        slot = null;
        movementLease = null;
        lookLease = null;
        slotLease = null;
    }
}
