package com.hunterbuddy.behavior.resource;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ResourceArbiter implements Leases {
    private final Map<Resource, Lease> holders = new EnumMap<>(Resource.class);

    @Override
    public Optional<Lease> request(String owner, Set<Resource> requested) {
        Set<Resource> resources = Implications.expand(requested);
        if (resources.stream().anyMatch(holders::containsKey)) return Optional.empty();
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
        lease.resources().forEach(resource -> holders.remove(resource, lease));
        lease.state(LeaseState.RELEASED);
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
        return List.copyOf(new LinkedHashSet<>(holders.values()));
    }
}
