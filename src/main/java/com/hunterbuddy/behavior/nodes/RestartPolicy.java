package com.hunterbuddy.behavior.nodes;

public record RestartPolicy(int maxRestarts) {
    public static final RestartPolicy ONCE = new RestartPolicy(0);

    public RestartPolicy {
        if (maxRestarts < 0) throw new IllegalArgumentException("negative restart limit");
    }

    public static RestartPolicy restart(int maxRestarts) {
        return new RestartPolicy(maxRestarts);
    }
}
