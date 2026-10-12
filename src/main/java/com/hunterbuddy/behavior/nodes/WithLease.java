package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.resource.Implications;
import com.hunterbuddy.behavior.resource.Lease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.LeaseState;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.behavior.trace.Kind;
import java.util.Objects;
import java.util.Set;

public final class WithLease extends Decorator {
    private final Set<Resource> resources;
    private final LeasePolicy policy;
    private Lease lease;
    private int waiting;

    public WithLease(Set<Resource> resources, LeasePolicy policy, Node child) {
        super("lease", child);
        this.resources = Set.copyOf(resources);
        this.policy = Objects.requireNonNull(policy);
    }

    public Set<Resource> resources() {
        return Implications.expand(resources);
    }

    @Override
    protected void onStart(TickContext ctx) {
        lease = null;
        waiting = 0;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        if (resources().contains(Resource.MOVEMENT) && !ctx.sensors().baritoneAvailable()) {
            return fail(FailureCode.UNSUPPORTED, "Baritone is required for MOVEMENT");
        }
        if (lease == null) {
            lease = ctx.leases().request(ctx.owner(), resources).orElse(null);
            if (lease == null) {
                String holder = resources().stream().map(ctx.leases()::holder)
                    .flatMap(java.util.Optional::stream).map(Lease::owner).findFirst().orElse("unknown");
                ctx.event(this, Kind.LEASE, null, "blocked", "by=" + holder);
                if (policy == LeasePolicy.FAIL_FAST || ++waiting >= 100) return fail(FailureCode.LEASE_TIMEOUT, "blocked by=" + holder);
                return new Running("waiting lease by=" + holder);
            }
            Lease acquired = lease;
            activation().defer(() -> ctx.leases().release(acquired));
            ctx.event(this, Kind.LEASE, null, "granted", resources().toString());
        }
        if (lease.state() != LeaseState.GRANTED) {
            child().halt(ctx, ExitReason.PREEMPTED);
            return fail(FailureCode.PREEMPTED, "lease revoked");
        }
        return child().execute(ctx);
    }
}
