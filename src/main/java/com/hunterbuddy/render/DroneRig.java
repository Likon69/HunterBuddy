package com.hunterbuddy.render;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Random;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class DroneRig {
    private static final double DIRECTION_TAU = 0.45;
    private static final double LEVEL_TAU = 2.5;
    private static final double LEVEL_DEAD_FRACTION = 0.3;
    private static final double LEAD_TAU = 0.7;
    private static final double LEAD_FACTOR = 0.3;
    private static final double OFFSET_TAU = 0.6;
    private static final double CLIP_SHORTEN_TAU = 0.1;
    private static final double CLIP_RECOVER_TAU = 0.7;
    private static final double LIFT_TAU = 0.25;
    private static final double CLEARANCE = 0.3;
    private static final double GROUND_CLEARANCE = 1.0;
    private static final double ORBIT_RATE = 0.25;
    private static final double MIN_MOTION_SQUARED = 0.0025;
    private static final double MIN_ARC_BLEND_SECONDS = 4.0;
    private static final double MAX_ARC_BLEND_SECONDS = 7.0;
    private static final double MIN_BLEND_DISTANCE_FACTOR = 0.5;
    private static final double MIN_BLEND_DISTANCE = 2.5;
    private static final double ANCHOR_LEAD_SECONDS = 3.7;
    private static final double ANCHOR_MAX_AHEAD = 170.0;
    private static final double ANCHOR_SIDE = 0.7;
    private static final double ANCHOR_DROP = 0.12;
    private static final double ANCHOR_CLEARANCE = 0.5;
    private static final double ANCHOR_PASSED_FACTOR = 3.0;
    private static final double ANCHORED_MAX_SECONDS = 12.0;
    private static final double ANCHORED_MIN_SECONDS = 0.5;
    private static final double CHANGE_MIN_HORIZONTAL = 0.3;
    private static final double LEVEL_FLIGHT_MIN_HORIZONTAL = 0.7;
    private static final double LEVEL_FLIGHT_STABLE_SECONDS = 2.0;
    private static final double GROUND_SEARCH_DEPTH = 30.0;
    private static final double GROUND_HEIGHT = 1.5;
    private static final double SURFACE_TAU = 0.4;
    private static final double SURFACE_PROBE_HEIGHT = 6.0;
    private static final double SURFACE_PROBE_DEPTH = 20.0;
    private static final double SURFACE_LOST_SECONDS = 0.5;
    private static final double SKIM_SIDE = 0.6;
    private static final double SKIM_AHEAD = 0.3;
    private static final double TICKS_PER_SECOND = 20.0;

    private final ArrayDeque<DroneShot> bag = new ArrayDeque<>();
    private final Random random = new Random();

    private DroneShot shot;
    private DroneShot previousShot;
    private DroneShot lastDrawn;
    private double shotAge;
    private double previousAge;
    private double shotSeconds;
    private double blend = 1.0;
    private double blendSeconds = MIN_ARC_BLEND_SECONDS;
    private boolean absoluteBlend;
    private double glideArc;
    private double orbitAngle;
    private double side = 1.0;
    private double previousSide = 1.0;
    private Vec3d anchor = Vec3d.ZERO;
    private double surfaceY;
    private double surfaceLost;
    private Vec3d direction;
    private double stableSeconds;
    private double levelAngle;
    private Vec3d level = new Vec3d(0.0, 0.0, 1.0);
    private Vec3d lead;
    private Vec3d offset;
    private double clipFraction = 1.0;
    private double lift;

    public void reset() {
        shot = null;
        previousShot = null;
        direction = null;
        lead = null;
        offset = null;
        clipFraction = 1.0;
        lift = 0.0;
        blend = 1.0;
    }

    public Vec3d flightDirection() {
        return direction;
    }

    public DronePlacement update(World world, Entity entity, Vec3d focus, Vec3d motion, double dt,
                                 double distance, double shotSeconds) {
        Vec3d wanted = motion.lengthSquared() > MIN_MOTION_SQUARED ? motion.normalize() : entity.getRotationVec(1.0f);
        double speed = motion.length() * TICKS_PER_SECOND;
        this.shotSeconds = shotSeconds;

        if (shot == null) {
            direction = wanted;
            startLevel(entity);
            stableSeconds = 0.0;
            surfaceLost = 0.0;
            lead = direction.multiply(distance * LEAD_FACTOR);
            bag.clear();
            shot = DroneShot.CHASE_THREE_QUARTER;
            lastDrawn = shot;
            previousShot = null;
            shotAge = 0.0;
            blend = 1.0;
            enter(world, entity, focus, speed, distance);
        } else {
            Vec3d blended = direction.lerp(wanted, DroneEasing.follow(dt, DIRECTION_TAU));
            direction = blended.lengthSquared() < 1.0e-6 ? wanted : blended.normalize();
            trackLevel(dt);
            stableSeconds = horizontal() > LEVEL_FLIGHT_MIN_HORIZONTAL ? stableSeconds + dt : 0.0;
            lead = lead.lerp(direction.multiply(distance * LEAD_FACTOR), DroneEasing.follow(dt, LEAD_TAU));
        }

        shotAge += dt;
        previousAge += dt;

        if (shot == DroneShot.GROUND_SKIM) trackSurface(world, entity, focus, distance, dt);

        if (due(focus, distance) && horizontal() >= CHANGE_MIN_HORIZONTAL) {
            previousShot = shot;
            previousAge = shotAge;
            previousSide = side;
            shot = draw(world, entity, focus);
            shotAge = 0.0;
            blend = 0.0;
            glideArc = 0.0;
            side = random.nextBoolean() ? 1.0 : -1.0;
            enter(world, entity, focus, speed, distance);
            absoluteBlend = shot.has(DroneShotTrait.ABSOLUTE) || previousShot.has(DroneShotTrait.ABSOLUTE);
            blendSeconds = absoluteBlend ? shot.blendSeconds() : arcSeconds(focus, distance);
        }

        blend = Math.min(1.0, blend + dt / blendSeconds);

        if (shot == DroneShot.ORBIT || (previousShot == DroneShot.ORBIT && blend < 1.0)) {
            orbitAngle += ORBIT_RATE * dt;
        }

        boolean blending = blend < 1.0 && previousShot != null;
        Vec3d wantedOffset = relative(shot, focus, shotAge, side, distance);
        Vec3d look = lead.multiply(shot.lead());
        boolean keepsOffset = shot.has(DroneShotTrait.ABSOLUTE) || (blending && absoluteBlend);

        if (blending) {
            Vec3d previousOffset = relative(previousShot, focus, previousAge, previousSide, distance);
            Vec3d previousLook = lead.multiply(previousShot.lead());

            if (absoluteBlend) {
                double eased = DroneEasing.smootherstep(blend);
                wantedOffset = keepAway(previousOffset.lerp(wantedOffset, eased), distance);
                look = previousLook.lerp(look, eased);
            } else {
                double eased = DroneEasing.smoothstep(blend);
                wantedOffset = glide(previousOffset, wantedOffset, eased);
                look = previousLook.lerp(look, eased);
            }
        }

        if (keepsOffset || offset == null) {
            offset = wantedOffset;
        } else {
            offset = offset.lerp(wantedOffset, DroneEasing.follow(dt, OFFSET_TAU));
        }

        Vec3d position = focus.add(offset);
        position = position.add(0.0, liftFor(world, entity, position, dt), 0.0);

        return new DronePlacement(clip(world, entity, focus, position, dt), focus.add(look));
    }

    private Vec3d relative(DroneShot which, Vec3d focus, double age, double sideSign, double distance) {
        return which.position(new DroneShotContext(focus, level, sideSign, age, orbitAngle, distance, anchor, surfaceY, shotSeconds))
            .subtract(focus);
    }

    private double horizontal() {
        return Math.hypot(direction.x, direction.z);
    }

    private boolean due(Vec3d focus, double distance) {
        if (shotAge >= limit()) return true;
        if (shot == DroneShot.GROUND_SKIM && surfaceLost > SURFACE_LOST_SECONDS) return true;

        return shot.has(DroneShotTrait.ANCHORED)
            && shotAge > ANCHORED_MIN_SECONDS
            && focus.subtract(anchor).dotProduct(direction) > distance * ANCHOR_PASSED_FACTOR;
    }

    private double limit() {
        return shot.has(DroneShotTrait.ANCHORED) ? Math.min(shotSeconds, ANCHORED_MAX_SECONDS) : shotSeconds;
    }

    private void startLevel(Entity entity) {
        double yaw = Math.toRadians(entity.getYaw());
        levelAngle = Math.atan2(Math.cos(yaw), -Math.sin(yaw));
        level = new Vec3d(Math.cos(levelAngle), 0.0, Math.sin(levelAngle));
    }

    private void trackLevel(double dt) {
        double horizontal = horizontal();
        if (horizontal <= LEVEL_DEAD_FRACTION) return;

        double weight = (horizontal - LEVEL_DEAD_FRACTION) / (1.0 - LEVEL_DEAD_FRACTION);
        double target = Math.atan2(direction.z, direction.x);
        double shortest = Math.toRadians(MathHelper.wrapDegrees((float) Math.toDegrees(target - levelAngle)));
        levelAngle += shortest * DroneEasing.follow(dt, LEVEL_TAU) * weight;
        level = new Vec3d(Math.cos(levelAngle), 0.0, Math.sin(levelAngle));
    }

    private void trackSurface(World world, Entity entity, Vec3d focus, double distance, double dt) {
        Vec3d right = new Vec3d(-level.z, 0.0, level.x);
        Vec3d flat = focus.add(right.multiply(side * distance * SKIM_SIDE)).add(level.multiply(distance * SKIM_AHEAD));
        OptionalDouble hit = DroneTerrain.surfaceBelow(
            world, entity, new Vec3d(flat.x, surfaceY + SURFACE_PROBE_HEIGHT, flat.z), SURFACE_PROBE_DEPTH
        );

        if (hit.isEmpty()) {
            surfaceLost += dt;
            return;
        }

        surfaceLost = 0.0;
        surfaceY += (hit.getAsDouble() - surfaceY) * DroneEasing.follow(dt, SURFACE_TAU);
    }

    private void enter(World world, Entity entity, Vec3d focus, double speed, double distance) {
        if (shot == DroneShot.ORBIT) {
            orbitAngle = Math.atan2(-level.z, -level.x);
        }

        if (!shot.has(DroneShotTrait.ANCHORED)) return;

        double ahead = Math.min(ANCHOR_MAX_AHEAD, Math.max(distance * ANCHOR_PASSED_FACTOR, speed * ANCHOR_LEAD_SECONDS));
        Vec3d path = DroneTerrain.clipped(world, entity, focus, focus.add(direction.multiply(ahead)), ANCHOR_CLEARANCE);
        Vec3d right = new Vec3d(-level.z, 0.0, level.x);
        Vec3d beside = path.add(right.multiply(side * distance * ANCHOR_SIDE));

        if (shot == DroneShot.FLYBY) {
            anchor = DroneTerrain.clipped(world, entity, path, beside.add(0.0, -distance * ANCHOR_DROP, 0.0), ANCHOR_CLEARANCE);
            return;
        }

        Vec3d sideways = DroneTerrain.clipped(world, entity, path, beside, ANCHOR_CLEARANCE);
        OptionalDouble ground = DroneTerrain.surfaceBelow(world, entity, sideways, GROUND_SEARCH_DEPTH * 2.0);
        anchor = new Vec3d(sideways.x, (ground.isPresent() ? ground.getAsDouble() : surfaceY) + GROUND_HEIGHT, sideways.z);
    }

    private double arcSeconds(Vec3d focus, double distance) {
        Vec3d from = relative(previousShot, focus, previousAge, previousSide, distance);
        Vec3d to = relative(shot, focus, shotAge, side, distance);
        double arc = Math.abs(MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(to.z, to.x) - Math.atan2(from.z, from.x))));
        return MIN_ARC_BLEND_SECONDS + (MAX_ARC_BLEND_SECONDS - MIN_ARC_BLEND_SECONDS) * Math.min(1.0, arc / 180.0);
    }

    private Vec3d keepAway(Vec3d relativePosition, double distance) {
        double minimum = Math.max(MIN_BLEND_DISTANCE, distance * MIN_BLEND_DISTANCE_FACTOR);
        double length = relativePosition.length();
        if (length >= minimum) return relativePosition;

        Vec3d outward = length > 1.0e-3 ? relativePosition.multiply(1.0 / length) : new Vec3d(-level.z, 0.0, level.x);
        return outward.multiply(minimum);
    }

    private Vec3d glide(Vec3d from, Vec3d to, double t) {
        double fromAzimuth = Math.atan2(from.z, from.x);
        double toAzimuth = Math.atan2(to.z, to.x);
        glideArc += Math.toRadians(MathHelper.wrapDegrees((float) Math.toDegrees(toAzimuth - fromAzimuth - glideArc)));
        double azimuth = fromAzimuth + glideArc * t;

        double fromElevation = Math.atan2(from.y, Math.hypot(from.x, from.z));
        double toElevation = Math.atan2(to.y, Math.hypot(to.x, to.z));
        double elevation = fromElevation + (toElevation - fromElevation) * t;

        double radius = from.length() + (to.length() - from.length()) * t;

        double flat = Math.cos(elevation) * radius;
        return new Vec3d(Math.cos(azimuth) * flat, Math.sin(elevation) * radius, Math.sin(azimuth) * flat);
    }

    private double liftFor(World world, Entity entity, Vec3d position, double dt) {
        double ground = DroneTerrain.hitDistance(world, entity, position, position.add(0.0, -GROUND_CLEARANCE, 0.0));
        double wanted = ground < 0.0 ? 0.0 : GROUND_CLEARANCE - ground;

        if (wanted > 0.0) {
            double ceiling = DroneTerrain.hitDistance(world, entity, position, position.add(0.0, wanted + CLEARANCE, 0.0));
            if (ceiling >= 0.0) wanted = Math.max(0.0, ceiling - CLEARANCE);
        }

        lift += (wanted - lift) * DroneEasing.follow(dt, LIFT_TAU);
        return lift;
    }

    private Vec3d clip(World world, Entity entity, Vec3d focus, Vec3d position, double dt) {
        Vec3d way = position.subtract(focus);
        double length = way.length();
        double reachable = 1.0;

        if (length > 1.0e-4) {
            double hit = DroneTerrain.hitDistance(world, entity, focus, position);
            if (hit >= 0.0) reachable = Math.max(0.0, Math.min(1.0, (hit - CLEARANCE) / length));
        }

        double tau = reachable < clipFraction ? CLIP_SHORTEN_TAU : CLIP_RECOVER_TAU;
        clipFraction += (reachable - clipFraction) * DroneEasing.follow(dt, tau);

        return focus.add(way.multiply(clipFraction));
    }

    private DroneShot draw(World world, Entity entity, Vec3d focus) {
        boolean levelFlight = stableSeconds >= LEVEL_FLIGHT_STABLE_SECONDS && horizontal() > LEVEL_FLIGHT_MIN_HORIZONTAL;
        boolean groundChecked = false;
        OptionalDouble ground = OptionalDouble.empty();
        DroneShot chosen = null;

        for (int pass = 0; pass < 2 && chosen == null; pass++) {
            if (pass == 1 || bag.isEmpty()) refill();

            for (DroneShot candidate : bag) {
                if (candidate == lastDrawn) continue;
                if (candidate.has(DroneShotTrait.LEVEL_FLIGHT) && !levelFlight) continue;

                if (candidate.has(DroneShotTrait.GROUND)) {
                    if (!groundChecked) {
                        ground = DroneTerrain.surfaceBelow(world, entity, focus, GROUND_SEARCH_DEPTH);
                        groundChecked = true;
                    }
                    if (ground.isEmpty()) continue;
                }

                chosen = candidate;
                break;
            }
        }

        if (chosen == null) {
            chosen = DroneShot.CHASE_THREE_QUARTER == lastDrawn ? DroneShot.ORBIT : DroneShot.CHASE_THREE_QUARTER;
        } else {
            bag.remove(chosen);
        }

        if (chosen.has(DroneShotTrait.GROUND) && ground.isPresent()) {
            surfaceY = ground.getAsDouble();
            surfaceLost = 0.0;
        }

        lastDrawn = chosen;
        return chosen;
    }

    private void refill() {
        List<DroneShot> shuffled = new ArrayList<>(List.of(DroneShot.values()));
        Collections.shuffle(shuffled, random);
        bag.addAll(shuffled);
    }
}
