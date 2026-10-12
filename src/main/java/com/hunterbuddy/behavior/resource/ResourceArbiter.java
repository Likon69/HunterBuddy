package com.hunterbuddy.behavior.resource;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public final class ResourceArbiter implements Leases {
    private final Map<Resource, Lease> holders = new EnumMap<>(Resource.class);
    private final List<Lease> parked = new ArrayList<>();
    private final Set<Lease> closing = new LinkedHashSet<>();
    private Predicate<Lease> releaseGuard = lease -> true;

    public void releaseGuard(Predicate<Lease> releaseGuard) {
        if (!holders.isEmpty()) throw new IllegalStateException("cannot configure an occupied arbiter");
        this.releaseGuard = java.util.Objects.requireNonNull(releaseGuard);
    }

    @Override
    public Optional<Lease> request(String owner, Set<Resource> requested) {
        Set<Resource> resources = Implications.expand(requested);
        if (resources.stream().anyMatch(holders::containsKey)
            || parked.stream().anyMatch(lease -> lease.resources().stream().anyMatch(resources::contains))) return Optional.empty();
        Lease lease = new Lease(owner, resources);
        resources.forEach(resource -> holders.put(resource, lease));
        return Optional.of(lease);
    }

    @Override
    public void revoke(Lease lease) {
        if (lease.state() == LeaseState.GRANTED) lease.state(LeaseState.REVOKING);
    }

    @Override
    public void release(Lease lease) {
        if (lease.state() == LeaseState.RELEASED) return;
        if (!releaseGuard.test(lease)) {
            revoke(lease);
            closing.add(lease);
            return;
        }
        lease.resources().forEach(resource -> holders.remove(resource, lease));
        parked.remove(lease);
        closing.remove(lease);
        lease.state(LeaseState.RELEASED);
    }

    public void advanceClosing() {
        List.copyOf(closing).forEach(this::release);
    }

    @Override
    public void releaseAll(String owner) {
        snapshot().stream().filter(lease -> lease.owner().equals(owner)).forEach(this::release);
    }

    @Override
    public void releaseAll() {
        snapshot().forEach(this::release);
    }

    @Override
    public Optional<Lease> holder(Resource resource) {
        return Optional.ofNullable(holders.get(resource));
    }

    @Override
    public List<Lease> snapshot() {
        Set<Lease> all = new LinkedHashSet<>(holders.values());
        all.addAll(parked);
        return List.copyOf(all);
    }

    @Override
    public void park() {
        for (Lease lease : snapshot()) {
            if (lease.owner().startsWith("gate:") || parked.contains(lease)) continue;
            revoke(lease);
            lease.resources().forEach(resource -> holders.remove(resource, lease));
            parked.add(lease);
        }
    }

    @Override
    public boolean restore() {
        if (!closing.isEmpty() || parked.stream().anyMatch(lease -> lease.resources().stream().anyMatch(holders::containsKey))) return false;
        for (Lease lease : List.copyOf(parked)) {
            if (lease.state() == LeaseState.RELEASED) continue;
            lease.state(LeaseState.GRANTED);
            lease.resources().forEach(resource -> holders.put(resource, lease));
        }
        parked.clear();
        return true;
    }

    @Override
    public void gates(List<GateOwner> owners) {
        holders.values().stream().filter(lease -> lease.owner().startsWith("gate:")).distinct().toList().forEach(this::release);
        for (GateOwner owner : owners) {
            Set<Resource> free = new LinkedHashSet<>(owner.resources());
            free.removeAll(holders.keySet());
            if (free.isEmpty()) continue;
            Lease lease = new Lease(owner.owner(), free);
            free.forEach(resource -> holders.put(resource, lease));
        }
    }
}
