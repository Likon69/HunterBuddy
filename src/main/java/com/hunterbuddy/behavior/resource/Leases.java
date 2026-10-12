package com.hunterbuddy.behavior.resource;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface Leases {
    Optional<Lease> request(String owner, Set<Resource> resources);
    void revoke(Lease lease);
    void release(Lease lease);
    void releaseAll(String owner);
    void releaseAll();
    Optional<Lease> holder(Resource resource);
    List<Lease> snapshot();
}
