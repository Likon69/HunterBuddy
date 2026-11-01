package com.hunterbuddy.render;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.util.math.Vec3d;

public enum DroneShot {
    CHASE_THREE_QUARTER(1.0, 2.5) {
        @Override
        public Vec3d position(DroneShotContext c) {
            double breath = 1.0 + 0.12 * Math.sin(c.age() * 0.5);
            return c.focus()
                .add(c.level().multiply(-c.distance() * 0.85 * breath))
                .add(c.right().multiply(c.side() * c.distance() * 0.5))
                .add(0.0, c.distance() * 0.3, 0.0);
        }
    },
    WIDE_CRANE(0.6, 2.5) {
        @Override
        public Vec3d position(DroneShotContext c) {
            double reach = c.distance() * 2.5;
            double drift = c.side() * reach * 0.15 + Math.sin(c.age() * 0.2) * reach * 0.3;
            return c.focus()
                .add(c.level().multiply(-reach * 0.7))
                .add(c.right().multiply(drift))
                .add(0.0, reach * 0.6, 0.0);
        }
    },
    ORBIT(0.5, 2.5) {
        @Override
        public Vec3d position(DroneShotContext c) {
            double radius = c.distance() * (1.0 + 0.15 * Math.sin(c.age() * 0.4));
            double height = c.distance() * (0.3 + 0.12 * Math.sin(c.age() * 0.3 + 1.0));
            return c.focus().add(Math.cos(c.orbitAngle()) * radius, height, Math.sin(c.orbitAngle()) * radius);
        }
    },
    FLYBY(0.3, 2.2, DroneShotTrait.ABSOLUTE, DroneShotTrait.ANCHORED, DroneShotTrait.LEVEL_FLIGHT) {
        @Override
        public Vec3d position(DroneShotContext c) {
            return c.anchor();
        }
    },
    FRONT_REVEAL(0.0, 2.5) {
        @Override
        public Vec3d position(DroneShotContext c) {
            return c.focus()
                .add(c.level().multiply(c.distance() * 1.1))
                .add(c.right().multiply(c.side() * c.distance() * 0.25))
                .add(0.0, c.distance() * 0.12, 0.0);
        }
    },
    SIDE_TRACK(1.3, 2.5) {
        @Override
        public Vec3d position(DroneShotContext c) {
            return c.focus()
                .add(c.right().multiply(c.side() * c.distance() * 0.95))
                .add(c.level().multiply(-c.distance() * 0.1))
                .add(0.0, -c.distance() * 0.1, 0.0);
        }
    },
    PULL_BACK(0.3, 2.5, DroneShotTrait.LEVEL_FLIGHT) {
        @Override
        public Vec3d position(DroneShotContext c) {
            double progress = DroneEasing.smoothstep(c.age() / Math.max(c.duration(), 1.0));
            double reach = c.distance() * (0.5 + 3.5 * progress);
            double height = c.distance() * (0.12 + 0.2 * progress);
            return c.focus().add(c.level().multiply(-reach)).add(0.0, height, 0.0);
        }
    },
    GROUND_SKIM(0.3, 2.5, DroneShotTrait.ABSOLUTE, DroneShotTrait.LEVEL_FLIGHT, DroneShotTrait.GROUND) {
        @Override
        public Vec3d position(DroneShotContext c) {
            Vec3d flat = c.focus()
                .add(c.right().multiply(c.side() * c.distance() * 0.6))
                .add(c.level().multiply(c.distance() * 0.3));
            return new Vec3d(flat.x, c.surface() + 1.5, flat.z);
        }
    },
    GROUND_WATCHER(0.3, 2.2, DroneShotTrait.ABSOLUTE, DroneShotTrait.ANCHORED, DroneShotTrait.LEVEL_FLIGHT, DroneShotTrait.GROUND) {
        @Override
        public Vec3d position(DroneShotContext c) {
            return c.anchor();
        }
    },
    UNDER_BELLY(0.3, 2.5, DroneShotTrait.LEVEL_FLIGHT) {
        @Override
        public Vec3d position(DroneShotContext c) {
            double reach = c.distance() * 0.7;
            return c.focus().add(c.level().multiply(-reach * 0.25)).add(0.0, -reach * 0.97, 0.0);
        }
    },
    WING_CAM(2.5, 2.5, DroneShotTrait.LEVEL_FLIGHT) {
        @Override
        public Vec3d position(DroneShotContext c) {
            return c.focus()
                .add(c.right().multiply(c.side() * 2.2))
                .add(c.level().multiply(-1.4))
                .add(0.0, 0.4, 0.0);
        }
    };

    private final double lead;
    private final double blendSeconds;
    private final Set<DroneShotTrait> traits;

    DroneShot(double lead, double blendSeconds, DroneShotTrait... traits) {
        this.lead = lead;
        this.blendSeconds = blendSeconds;
        this.traits = traits.length == 0 ? EnumSet.noneOf(DroneShotTrait.class) : EnumSet.of(traits[0], traits);
    }

    public double lead() {
        return lead;
    }

    public double blendSeconds() {
        return blendSeconds;
    }

    public boolean has(DroneShotTrait trait) {
        return traits.contains(trait);
    }

    public abstract Vec3d position(DroneShotContext c);
}
