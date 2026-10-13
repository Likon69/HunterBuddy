package com.hunterbuddy.behavior.resource;

import com.hunterbuddy.behavior.mc.FlightBridge;
import com.hunterbuddy.behavior.mc.InventoryBridge;
import com.hunterbuddy.behavior.mc.MiningBridge;
import com.hunterbuddy.behavior.mc.PlacementBridge;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraft.util.math.BlockPos;

public final class Outputs {
    private final Leases leases;
    private final Supplier<String> actor;
    private MovementOutput movementOutput;
    private LookOutput lookOutput;
    private SlotOutput slotOutput;
    private BaritoneOutput baritoneOutput;
    private BaritoneIntent baritone;
    private Lease baritoneLease;
    private Consumer<BaritoneOutput.Signal> baritoneEvents;
    private MiningBridge mining;
    private PlacementBridge placement;
    private InventoryBridge inventory;
    private FlightBridge flight;
    private ElytraOutput elytra;
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

    public void connectBaritone(BaritoneOutput output) {
        if (baritoneOutput != null) throw new IllegalStateException("Baritone output already connected");
        baritoneOutput = Objects.requireNonNull(output);
        if (!(leases instanceof ResourceArbiter arbiter)) throw new IllegalStateException("Baritone needs the resource arbiter");
        arbiter.releaseGuard(this::releaseReady);
    }

    public void connectInventory(InventoryBridge inventory) {
        if (this.inventory != null) throw new IllegalStateException("inventory bridge already connected");
        this.inventory = Objects.requireNonNull(inventory);
    }

    private boolean releaseReady(Lease lease) {
        boolean pathReady = !lease.resources().contains(Resource.BARITONE) || baritoneOutput.stop(lease);
        if (elytra != null) pathReady &= elytra.stop(lease);
        if (flight != null) flight.release(lease);
        boolean inventoryReady = inventory == null || !lease.resources().contains(Resource.INVENTORY) || inventory.release(lease);
        return pathReady && inventoryReady;
    }

    public InventoryBridge inventory() {
        return Objects.requireNonNull(inventory, "Inventory bridge is not connected");
    }

    public void connectFlight(FlightBridge flight, ElytraOutput elytra) {
        if (this.flight != null) throw new IllegalStateException("flight bridges already connected");
        this.flight = Objects.requireNonNull(flight);
        this.elytra = Objects.requireNonNull(elytra);
    }

    public FlightBridge flight() { return Objects.requireNonNull(flight, "flight bridge not connected"); }
    public void elytra(String owner, BlockPos destination, boolean rockets) {
        Lease lease = requireLease(owner, Resource.BARITONE);
        if (rockets) requireLease(owner, Resource.ROCKETS);
        Objects.requireNonNull(elytra).apply(lease, destination, rockets);
    }
    public boolean ownElytra() { return elytra != null && elytra.owns(); }
    public boolean landingFlight() { return elytra != null && elytra.noRockets(); }
    public BlockPos lastGoal() { return baritoneOutput == null ? null : baritoneOutput.lastGoal(); }

    public void connectMinecraft(MiningBridge mining, PlacementBridge placement) {
        if (this.mining != null) throw new IllegalStateException("Minecraft bridges already connected");
        this.mining = Objects.requireNonNull(mining);
        this.placement = Objects.requireNonNull(placement);
    }

    public MiningBridge mining() {
        return Objects.requireNonNull(mining, "Mining bridge is not connected");
    }

    public PlacementBridge placement() {
        return Objects.requireNonNull(placement, "Placement bridge is not connected");
    }

    public long nativeTick() {
        return nativeTicks == null ? begunAt : nativeTicks.getAsLong();
    }

    public void baritone(String owner, BaritoneIntent intent, Consumer<BaritoneOutput.Signal> events) {
        baritoneLease = requireLease(owner, Resource.BARITONE);
        if (intent.allowBreak()) requireLease(owner, Resource.MINING);
        baritone = Objects.requireNonNull(intent);
        baritoneEvents = Objects.requireNonNull(events);
    }

    public boolean baritoneStarted(String owner, BaritoneIntent intent) {
        return baritoneOutput != null && leases.holder(Resource.BARITONE)
            .filter(lease -> lease.owner().equals(owner) && baritoneOutput.owns(lease, intent)).isPresent();
    }

    public boolean ownBaritone() {
        return ownElytra() || baritoneOutput != null && baritoneOutput.ownsControl();
    }

    public void advanceNative() {
        if (baritoneOutput != null) baritoneOutput.advance();
        if (elytra != null) elytra.advance();
        if (inventory != null) inventory.advance();
        if (leases instanceof ResourceArbiter arbiter) arbiter.advanceClosing();
    }

    public void suspend(boolean borrowed) {
        clear();
        long tick = nativeTick();
        if (movementOutput != null) movementOutput.release(tick);
        if (slotOutput != null) slotOutput.discard();
        if (mining != null) mining.releaseClick();
        if (inventory != null) inventory.suspend();
        if (baritoneOutput != null) baritoneOutput.suspend(borrowed && (elytra == null || !elytra.attached()));
        if (elytra != null) elytra.suspend(borrowed);
        if (flight != null) flight.cancel();
    }

    public boolean resume() {
        if (baritoneOutput != null && !baritoneOutput.resume()) return false;
        if (elytra != null && !elytra.resume()) return false;
        if (inventory != null) inventory.resume();
        return true;
    }

    public boolean borrowedBaritone() {
        return elytra != null && elytra.borrowed() || baritoneOutput != null && baritoneOutput.borrowed();
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
        if (!valid(baritoneLease, Resource.BARITONE)) baritone = null;
        try {
            movementOutput.apply(movement, tick);
            lookOutput.apply(look);
            slotOutput.apply(slot, slotLease, tick);
            if (baritoneOutput != null) baritoneOutput.apply(baritone, baritoneLease, baritoneEvents);
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
        return !ownBaritone() && !borrowedBaritone() && (flight == null || !flight.ownsMovement()) && movementOutput != null && movementOutput.foreignInput();
    }

    public void discardSlot() {
        if (slotOutput != null) slotOutput.discard();
    }

    public List<String> status() {
        List<String> lines = new ArrayList<>();
        if (inventory != null) lines.add(inventory.status());
        if (elytra != null && elytra.status() != null) lines.add(elytra.status());
        if (baritoneOutput != null && baritoneOutput.status() != null) lines.add(baritoneOutput.status());
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
        if (flight != null) flight.cancel();
        if (elytra != null) elytra.release();
        if (inventory != null) inventory.stop();
        if (nativeTicks != null) tick = nativeTicks.getAsLong();
        RuntimeException failure = null;
        try {
            if (baritoneOutput != null) baritoneOutput.release();
        } catch (RuntimeException ex) {
            failure = ex;
        }
        try {
            if (mining != null) mining.releaseClick();
        } catch (RuntimeException ex) {
            if (failure == null) failure = ex;
            else if (failure != ex) failure.addSuppressed(ex);
        }
        try {
            if (movementOutput != null) movementOutput.release(tick);
        } catch (RuntimeException ex) {
            if (failure == null) failure = ex;
            else if (failure != ex) failure.addSuppressed(ex);
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
        baritone = null;
        baritoneLease = null;
        baritoneEvents = null;
    }

    public void worldLost() {
        if (flight != null) flight.cancel();
        if (elytra != null) elytra.release();
        if (baritoneOutput != null) baritoneOutput.worldLost();
        discardSlot();
    }
}
