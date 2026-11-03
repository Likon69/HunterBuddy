package com.hunterbuddy.render;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.Set;
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
    private static final double ANCHOR_LEAD_SECONDS = 2.0;
    private static final double ANCHOR_MAX_AHEAD = 170.0;
    private static final double ANCHOR_SIDE = 0.7;
    private static final double ANCHOR_DROP = 0.12;
    private static final double ANCHOR_CLEARANCE = 0.5;
    private static final double ANCHOR_PASSED_FACTOR = 3.0;
    private static final double ANCHORED_MAX_SECONDS = 12.0;
    private static final double ANCHORED_PASSED_AFTER_BLEND_SECONDS = 3.0;
    private static final double CHANGE_MIN_HORIZONTAL = 0.3;
    private static final double LEVEL_FLIGHT_MIN_HORIZONTAL = 0.7;
    private static final double LEVEL_FLIGHT_STABLE_SECONDS = 2.0;
    private static final double GROUND_SEARCH_DEPTH = 12.0;
    private static final double ABSOLUTE_BLEND_SPEED = 15.0;
    private static final double MIN_ABSOLUTE_BLEND_SECONDS = 4.0;
    private static final double MAX_ABSOLUTE_BLEND_SECONDS = 8.0;
    private static final double OPENING_SHOT_MAX_SECONDS = 6.0;
    private static final double VELOCITY_TAU = 0.2;
    private static final double MIN_CAMERA_SPEED = 0.5;
    private static final double CHASE_SIDE = 0.5;
    private static final double CHASE_SIDE_ON_FOOT = 0.9;
    private static final Set<DroneShot> OPENING_SHOTS = EnumSet.of(
        DroneShot.CHASE_THREE_QUARTER, DroneShot.WIDE_CRANE, DroneShot.ORBIT, DroneShot.SIDE_TRACK, DroneShot.TOP_DOWN
    );
    private static final double GROUND_HEIGHT = 1.5;
    private static final double SURFACE_TAU = 0.4;
    private static final double SURFACE_PROBE_HEIGHT = 6.0;
    private static final double SURFACE_PROBE_DEPTH = 20.0;
    private static final double SURFACE_LOST_SECONDS = 0.5;
    private static final double SKIM_SIDE = 0.6;
    private static final double SKIM_AHEAD = 0.3;
    private static final double INTEREST_TAU = 0.5;
    private static final double TRAIL_BEHIND = 5.0;
    private static final List<DroneShot> TAKEOFF_NORMAL_SHOTS = List.of(
        DroneShot.ORBIT, DroneShot.CHASE_THREE_QUARTER, DroneShot.WIDE_CRANE, DroneShot.TOP_DOWN, DroneShot.SIDE_TRACK
    );
    private static final double TRAIL_TAU = 0.5;
    private static final double TRAIL_ENTRY_TAU = 2.0;
    private static final double TRAIL_ENTRY_SECONDS = 6.0;
    private static final double WELL_TAU = 0.8;
    private static final double FOOT_SWEEP_SPEED = 6.0;
    private static final double TAKEOFF_SWEEP_SPEED = 5.0;
    private static final double FLIGHT_SWEEP_SPEED = 40.0;
    private static final double SWEEP_RISE_TAU = 3.0;
    private static final double SWEEP_FALL_TAU = 1.0;
    private static final double FOOT_OBSTACLE_SECONDS = 0.5;
    private static final double CHASE_SCALE_ON_FOOT = 0.55;
    private static final double MAX_CAMERA_DISTANCE = 80.0;
    private static final double MAX_INTEREST_DISTANCE = 32.0;
    private static final double WELL_ABOVE = 5.0;
    private static final double VIEW_BLOCKED_SECONDS = 1.0;
    private static final double INTEREST_SNAP_WEIGHT = 0.05;
    private static final double INTEREST_MIN_WEIGHT = 0.001;
    private static final double DRIFT_SPEED = 0.4;
    private static final double DRIFT_POSITION_TAU = 0.5;
    private static final double DRIFT_LOOK_TAU = 0.8;
    private static final double DRIFT_MIN_DISTANCE = 5.0;
    private static final double WATCHER_SIDE = 10.0;
    private static final double WATCHER_HEIGHT = 3.0;
    private static final double WATCHER_MIN_ANGLE = 40.0;
    private static final double WATCHER_MAX_ANGLE = 140.0;
    private static final double WATCHER_MIN_REACH_WANTED = 8.0;
    private static final double WATCHER_MAX_REACH_WANTED = 14.0;
    private static final double WATCHER_MIN_HEIGHT = 2.0;
    private static final double WATCHER_MAX_HEIGHT = 7.0;
    private static final double RISE_MIN_REACH = 2.0;
    private static final double RISE_MAX_REACH = 6.0;
    private static final double RISE_MIN_HEIGHT = 8.0;
    private static final double RISE_MAX_HEIGHT = 14.0;
    private static final double TAKEOFF_HOLD_SECONDS = 8.0;
    private static final double TAKEOFF_MIN_BLEND_SECONDS = 6.0;
    private static final double TAKEOFF_FOLLOW_TAU = 0.9;
    private static final double TICKS_PER_SECOND = 20.0;
    private static final double ANCHOR_LEASH = 64.0;
    private static final double SCENE_LEFT_BEHIND = 16.0;
    private static final double PREVIOUS_ANCHOR_DRAG_RATE = 3.0;
    private static final double LEAVE_LOOK_TAU = 0.3;
    private static final double RELEASED_CLIP_TAU = 0.5;
    private static final double RELEASED_CLIP_SECONDS = 1.0;
    private static final double WATCHER_MIN_REACH = 4.0;
    private static final double TAKEOFF_BEHIND = 8.0;
    private static final double TAKEOFF_BEHIND_HEIGHT = 4.0;
    private static final double TAKEOFF_ABOVE = 8.0;
    private static final double TAKEOFF_ROOM_FRACTION = 0.6;
    private static final double CHEST_HEIGHT = 0.4;
    private static final double MIN_SHOWN_DISTANCE = 3.5;
    private static final double COLLAPSE_SECONDS = 0.5;
    private static final double RESCUE_GAP_SECONDS = 3.0;
    private static final double RESCUE_BLEND_SECONDS = 4.0;
    private static final double TRAIL_HOLD_MIN = 5.0;
    private static final double TRAIL_HOLD_MAX = 14.0;
    private static final double TRAIL_ABOVE = 6.0;
    private static final double TRAIL_ABOVE_MIN = 4.0;
    private static final double RESCUE_TRAIL_SECONDS = 5.0;

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
    private Vec3d previousAnchor = Vec3d.ZERO;
    private double surfaceY;
    private double previousSurfaceY;
    private double surfaceLost;
    private Vec3d direction;
    private double stableSeconds;
    private double levelAngle;
    private Vec3d level = new Vec3d(0.0, 0.0, 1.0);
    private Vec3d lead;
    private Vec3d offset;
    private Vec3d rawPosition;
    private Vec3d carry = Vec3d.ZERO;
    private Vec3d bypass = Vec3d.ZERO;
    private DroneStage stage = DroneStage.CRUISE;
    private DroneScene enteredScene = DroneScene.CRUISE;
    private boolean enteredDuel;
    private double sinceScene;
    private Vec3d interestPoint;
    private final DroneTrail trail = new DroneTrail();
    private final DroneClimb climb = new DroneClimb();
    private boolean enteredClimb;
    private boolean floored;
    private Vec3d trailCam;
    private Vec3d takeoffOffset = Vec3d.ZERO;
    private double driftAlong;
    private double driftDirection = 1.0;
    private Vec3d driftPosition;
    private Vec3d driftLook;
    private double interestWeight;
    private DroneShot lastTakeoff;
    private DroneShot beforeLastTakeoff;
    private double clipFraction = 1.0;
    private double lift;
    private double chaseSide = CHASE_SIDE;
    private boolean opening;
    private Set<DroneShot> allowed = EnumSet.allOf(DroneShot.class);
    private Vec3d cameraPosition;
    private Vec3d cameraVelocity = Vec3d.ZERO;
    private Vec3d lastFocus;
    private double sinceUnclipped = RELEASED_CLIP_SECONDS;
    private double collapsedSeconds;
    private double sinceRescue = RESCUE_GAP_SECONDS;
    private boolean trailRescue;
    private double viewBlockedSeconds;
    private double footBlockedSeconds;
    private double chaseScale = 1.0;
    private Vec3d lastRelative;
    private double sweepSpeed = FOOT_SWEEP_SPEED;

    public void reset() {
        lastFocus = null;
        sinceUnclipped = RELEASED_CLIP_SECONDS;
        collapsedSeconds = 0.0;
        sinceRescue = RESCUE_GAP_SECONDS;
        trailRescue = false;
        viewBlockedSeconds = 0.0;
        footBlockedSeconds = 0.0;
        chaseScale = 1.0;
        lastRelative = null;
        sweepSpeed = FOOT_SWEEP_SPEED;
        trail.clear();
        climb.reset();
        enteredClimb = false;
        floored = false;
        shot = null;
        previousShot = null;
        direction = null;
        lead = null;
        offset = null;
        rawPosition = null;
        carry = Vec3d.ZERO;
        enteredScene = DroneScene.CRUISE;
        enteredDuel = false;
        interestPoint = null;
        interestWeight = 0.0;
        lastTakeoff = null;
        beforeLastTakeoff = null;
        clipFraction = 1.0;
        lift = 0.0;
        blend = 1.0;
        chaseSide = CHASE_SIDE;
        cameraPosition = null;
        cameraVelocity = Vec3d.ZERO;
    }

    public Vec3d flightDirection() {
        return direction;
    }

    public boolean watchesTakeoff() {
        return shot == DroneShot.LAUNCH_WATCHER || shot == DroneShot.RISE_ABOVE
            || (blend < 1.0 && (previousShot == DroneShot.LAUNCH_WATCHER || previousShot == DroneShot.RISE_ABOVE));
    }

    public boolean watchesSlowly() {
        return shot == DroneShot.ROOM_DRIFT || shot == DroneShot.TRAIL_CAM || shot == DroneShot.CLIMB_SIDE
            || (blend < 1.0 && previousShot == DroneShot.TRAIL_CAM);
    }

    public boolean holdsThirdPerson() {
        return shot != null && (shot.has(DroneShotTrait.SCENE)
            || (blend < 1.0 && previousShot != null && previousShot.has(DroneShotTrait.SCENE)));
    }

    public DronePlacement update(World world, Entity entity, Vec3d focus, Vec3d motion, double dt,
                                 double distance, double shotSeconds, boolean gliding, Set<DroneShot> allowed,
                                 DroneStage stage, double minSceneSeconds) {
        Vec3d wanted;
        if (motion.lengthSquared() > MIN_MOTION_SQUARED) {
            wanted = motion.normalize();
        } else if (!gliding && direction != null) {
            wanted = direction;
        } else {
            wanted = entity.getRotationVec(1.0f);
        }
        this.allowed = allowed;
        this.stage = stage;
        double speed = motion.length() * TICKS_PER_SECOND;
        this.shotSeconds = shotSeconds;

        if (shot == null) {
            direction = wanted;
            startLevel(entity);
            stableSeconds = 0.0;
            surfaceLost = 0.0;
            lead = direction.multiply(distance * LEAD_FACTOR);
            side = random.nextBoolean() ? 1.0 : -1.0;
            shot = draw(world, entity, focus, true);
            opening = true;
            previousShot = null;
            shotAge = 0.0;
            blend = 1.0;
            sinceScene = minSceneSeconds;
            enter(world, entity, focus, speed, distance);
        } else {
            Vec3d blended = direction.lerp(wanted, DroneEasing.follow(dt, DIRECTION_TAU));
            direction = blended.lengthSquared() < 1.0e-6 ? wanted : blended.normalize();
            trackLevel(dt);
            stableSeconds = horizontal() > LEVEL_FLIGHT_MIN_HORIZONTAL ? stableSeconds + dt : 0.0;
            lead = lead.lerp(gliding ? direction.multiply(distance * LEAD_FACTOR) : Vec3d.ZERO, DroneEasing.follow(dt, LEAD_TAU));
        }

        double chaseSideTarget = !gliding && shot == DroneShot.CHASE_THREE_QUARTER ? CHASE_SIDE_ON_FOOT : CHASE_SIDE;
        chaseSide += (chaseSideTarget - chaseSide) * DroneEasing.follow(dt, OFFSET_TAU);
        double chaseScaleTarget = !gliding && shot == DroneShot.CHASE_THREE_QUARTER ? CHASE_SCALE_ON_FOOT : 1.0;
        chaseScale += (chaseScaleTarget - chaseScale) * DroneEasing.follow(dt, OFFSET_TAU);

        shotAge += dt;
        previousAge += dt;
        sinceScene += dt;
        sinceRescue += dt;
        trackInterest(dt);
        trail.record(focus);
        if (shot == DroneShot.TRAIL_CAM) followTrail(world, entity, focus, dt);
        if (shot == DroneShot.WELL_TOP) followWell(world, entity, focus, dt);
        if (shot == DroneShot.CLIMB_SIDE || (blend < 1.0 && previousShot == DroneShot.CLIMB_SIDE)) climb.follow(focus, dt);
        if (shot == DroneShot.CLIMB_SIDE) anchor = climb.anchor(dt);
        if (shot == DroneShot.ROOM_DRIFT) drift(focus, dt);
        if (followsHim(shot)) followTakeoff(world, entity, focus, dt);

        if (shot == DroneShot.GROUND_SKIM) trackSurface(world, entity, focus, distance, dt);

        boolean duel = duelWanted();
        boolean climbing = stage.climbing() && allowed.contains(DroneShot.CLIMB_SIDE);
        boolean climbChanged = climbing != enteredClimb;
        boolean sceneChanged = stage.scene() != enteredScene || duel != enteredDuel || climbChanged;

        boolean takeoffHeld = followsHim(shot) && shotAge < TAKEOFF_HOLD_SECONDS && !climbChanged && !duel;
        boolean sceneDue = sceneChanged && !takeoffHeld && (sinceScene >= minSceneSeconds || leftBehind(focus, SCENE_LEFT_BEHIND) || groundTakesOver() || climbChanged);

        if (cameraPosition != null && sceneDue) {
            enteredScene = stage.scene();
            enteredDuel = duel;
            enteredClimb = climbing;
            DroneShot next = climbing ? DroneShot.CLIMB_SIDE : sceneShot(world, entity, focus, distance, duel);
            boolean leaving = next == null && shot.has(DroneShotTrait.SCENE)
                && (shot == DroneShot.CLIMB_SIDE || !shot.has(DroneShotTrait.UNDERGROUND) || stage.scene() == DroneScene.CRUISE || stage.scene() == DroneScene.TAKEOFF);

            if ((next != null && next != shot) || leaving) {
                sinceScene = 0.0;
                change(world, entity, focus, speed, distance, next);
            }
        } else if (collapsedSeconds >= COLLAPSE_SECONDS && sinceRescue >= RESCUE_GAP_SECONDS && !shot.has(DroneShotTrait.SCENE)) {
            rescue(world, entity, focus, speed, distance);
        } else if (trailRescue && shot == DroneShot.TRAIL_CAM && shotAge >= RESCUE_TRAIL_SECONDS
            && (stage.scene() == DroneScene.CRUISE || stage.scene() == DroneScene.TAKEOFF)) {
            DroneShot roomy = roomyShot(world, entity, focus, distance);
            if (roomy != null) change(world, entity, focus, speed, distance, roomy);
        } else if (!gliding && viewBlockedSeconds >= FOOT_OBSTACLE_SECONDS && sinceRescue >= RESCUE_GAP_SECONDS
            && !shot.has(DroneShotTrait.SCENE) && !shot.has(DroneShotTrait.ABSOLUTE)) {
            dodgeObstacle(world, entity, focus, speed, distance);
        } else if (viewBlockedSeconds >= VIEW_BLOCKED_SECONDS && sinceRescue >= RESCUE_GAP_SECONDS) {
            sinceRescue = 0.0;
            DroneShot roomy = roomyShot(world, entity, focus, distance);
            if (roomy != null) change(world, entity, focus, speed, distance, roomy);
        } else if (uncheckedShot() || leftBehind(focus, ANCHOR_LEASH) || (due(focus, distance) && horizontal() >= CHANGE_MIN_HORIZONTAL)) {
            change(world, entity, focus, speed, distance, null);
        }

        blend = Math.min(1.0, blend + dt / blendSeconds);
        Vec3d moved = lastFocus == null ? Vec3d.ZERO : focus.subtract(lastFocus);
        lastFocus = focus;

        if (blend < 1.0 && previousShot != null && previousShot.has(DroneShotTrait.ABSOLUTE)) {
            double drag = DroneEasing.smoothstep(Math.min(1.0, blend * PREVIOUS_ANCHOR_DRAG_RATE));
            previousAnchor = previousAnchor.add(moved.multiply(drag));
        }

        if (blend < 1.0 && previousShot == DroneShot.ROOM_DRIFT && shot != DroneShot.ROOM_DRIFT) {
            driftLook = driftLook.lerp(focus, DroneEasing.follow(dt, LEAVE_LOOK_TAU));
        }

        if (shot == DroneShot.ORBIT || (previousShot == DroneShot.ORBIT && blend < 1.0)) {
            orbitAngle += ORBIT_RATE * dt;
        }

        boolean blending = blend < 1.0 && previousShot != null;
        Vec3d wantedOffset = relative(shot, focus, shotAge, side, anchor, surfaceY, distance);
        Vec3d look = lookFor(shot, focus);
        boolean keepsOffset = shot.has(DroneShotTrait.ABSOLUTE) || (blending && absoluteBlend);

        if (blending) {
            Vec3d previousOffset = relative(previousShot, focus, previousAge, previousSide, previousAnchor, previousSurfaceY, distance);
            Vec3d previousLook = lookFor(previousShot, focus);

            if (absoluteBlend) {
                double eased = DroneEasing.smootherstep(blend);
                wantedOffset = keepAway(previousOffset, wantedOffset, eased, distance).add(carry.multiply(1.0 - eased));
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
        rawPosition = position;
        boolean free = shot.has(DroneShotTrait.UNDERGROUND) && (!blending || previousShot.has(DroneShotTrait.UNDERGROUND));
        boolean unclipped = free || followsHim(shot) || (blending && followsHim(previousShot));
        if (stage.scene() == DroneScene.DIG && !DroneTerrain.insideBlock(world, position)) unclipped = true;
        sinceUnclipped = unclipped ? 0.0 : sinceUnclipped + dt;
        position = position.add(0.0, liftFor(world, entity, position, dt, free), 0.0);

        Vec3d placed = limitSweep(focus, withinReach(focus, clip(world, entity, focus, position, dt, unclipped, gliding)), dt, gliding);
        boolean collapsed = !unclipped && floored;
        collapsedSeconds = collapsed ? collapsedSeconds + dt : 0.0;
        boolean surface = stage.scene() == DroneScene.CRUISE || stage.scene() == DroneScene.TAKEOFF;
        boolean viewBlocked = surface && shot != DroneShot.CLIMB_SIDE && DroneTerrain.hitDistance(world, entity, placed, focus) >= 0.0;
        viewBlockedSeconds = viewBlocked ? viewBlockedSeconds + dt : 0.0;
        if (cameraPosition != null && dt > 0.0) {
            Vec3d measured = placed.subtract(cameraPosition).multiply(1.0 / dt);
            cameraVelocity = cameraVelocity.lerp(measured, DroneEasing.follow(dt, VELOCITY_TAU));
        }
        cameraPosition = placed;

        Vec3d filmed = focus.add(look).add(0.0, gliding ? 0.0 : CHEST_HEIGHT, 0.0);
        return new DronePlacement(placed, lookTarget(focus, filmed));
    }

    private void dodgeObstacle(World world, Entity entity, Vec3d focus, double speed, double distance) {
        sinceRescue = 0.0;
        side = -side;
        if (hasRoom(world, entity, focus, shot, distance)) return;

        side = -side;
        DroneShot roomy = roomyShot(world, entity, focus, distance);
        if (roomy != null) change(world, entity, focus, speed, distance, roomy);
    }

    private void rescue(World world, Entity entity, Vec3d focus, double speed, double distance) {
        sinceRescue = 0.0;
        collapsedSeconds = 0.0;
        DroneShot next = roomyShot(world, entity, focus, distance);
        boolean onTrail = next == null && allowed.contains(DroneShot.TRAIL_CAM) && trail.usable();
        if (onTrail) next = DroneShot.TRAIL_CAM;
        if (next == null) return;

        change(world, entity, focus, speed, distance, next);
        trailRescue = onTrail;
        blendSeconds = Math.min(blendSeconds, RESCUE_BLEND_SECONDS);
    }

    private DroneShot roomyShot(World world, Entity entity, Vec3d focus, double distance) {
        List<DroneShot> options = new ArrayList<>();
        for (DroneShot candidate : DroneShot.values()) {
            if (candidate == shot || !allowed.contains(candidate)) continue;
            if (candidate.has(DroneShotTrait.SCENE) || candidate.has(DroneShotTrait.LEVEL_FLIGHT) || candidate.has(DroneShotTrait.GROUND)) continue;
            if (hasRoom(world, entity, focus, candidate, distance)) options.add(candidate);
        }
        return options.isEmpty() ? null : options.get(random.nextInt(options.size()));
    }

    private boolean uncheckedShot() {
        return !allowed.contains(shot) && firstNormalAllowed() != null;
    }

    private DroneShot firstNormalAllowed() {
        for (DroneShot candidate : DroneShot.values()) {
            if (allowed.contains(candidate) && !candidate.has(DroneShotTrait.SCENE)) return candidate;
        }
        return null;
    }

    private void change(World world, Entity entity, Vec3d focus, double speed, double distance, DroneShot forced) {
        trailRescue = false;
        previousShot = shot;
        previousAge = shotAge;
        previousSide = side;
        previousAnchor = anchor;
        previousSurfaceY = surfaceY;
        shot = forced != null ? forced : draw(world, entity, focus, false);
        opening = false;
        shotAge = 0.0;
        blend = 0.0;
        glideArc = 0.0;
        side = random.nextBoolean() ? 1.0 : -1.0;
        enter(world, entity, focus, speed, distance);
        absoluteBlend = shot.has(DroneShotTrait.ABSOLUTE) || previousShot.has(DroneShotTrait.ABSOLUTE);
        blendSeconds = absoluteBlend ? travelSeconds(focus, distance) : arcSeconds(focus, distance);

        if (!absoluteBlend) {
            carry = Vec3d.ZERO;
            return;
        }

        Vec3d from = relative(previousShot, focus, previousAge, previousSide, previousAnchor, previousSurfaceY, distance);
        Vec3d to = relative(shot, focus, shotAge, side, anchor, surfaceY, distance);
        carry = rawPosition.subtract(focus).subtract(from);
        bypass = DroneBypass.axis(from, to, new Vec3d(-level.z, 0.0, level.x));

        if (shot == DroneShot.TRAIL_CAM || shot == DroneShot.WELL_TOP || shot == DroneShot.CLIMB_SIDE) {
            blend = 1.0;
            clipFraction = 1.0;
            lift = 0.0;
        }
    }

    private boolean groundTakesOver() {
        return !shot.has(DroneShotTrait.SCENE) && (stage.scene() == DroneScene.DIG || stage.scene() == DroneScene.EXIT);
    }

    private boolean duelWanted() {
        return stage.combat() && stage.scene() != DroneScene.ROOM && stage.scene() != DroneScene.DIG;
    }

    private DroneShot sceneShot(World world, Entity entity, Vec3d focus, double distance, boolean duel) {
        if (duel && allowed.contains(DroneShot.DUEL)) return DroneShot.DUEL;

        return switch (stage.scene()) {
            case DIG -> trailIfAllowed();
            case EXIT -> climbShot();
            case ROOM -> stage.roomCorners().isEmpty() || !allowed.contains(DroneShot.ROOM_DRIFT) ? null : DroneShot.ROOM_DRIFT;
            case TAKEOFF -> takeoffShot(world, entity, focus, distance);
            case CRUISE -> null;
        };
    }

    private DroneShot climbShot() {
        return allowed.contains(DroneShot.WELL_TOP) ? DroneShot.WELL_TOP : trailIfAllowed();
    }

    private boolean roomToWatch(World world, Entity entity, Vec3d focus) {
        Vec3d right = new Vec3d(-level.z, 0.0, level.x).multiply(WATCHER_SIDE);
        List<Vec3d> ways = List.of(right.add(0.0, WATCHER_HEIGHT, 0.0), right.multiply(-1.0).add(0.0, WATCHER_HEIGHT, 0.0), new Vec3d(0.0, TAKEOFF_ABOVE, 0.0));

        for (Vec3d way : ways) {
            if (DroneTerrain.clipped(world, entity, focus, focus.add(way), ANCHOR_CLEARANCE).distanceTo(focus) >= WATCHER_MIN_REACH) return true;
        }
        return false;
    }

    private DroneShot trailIfAllowed() {
        return trail.usable() && allowed.contains(DroneShot.TRAIL_CAM) ? DroneShot.TRAIL_CAM : null;
    }

    private void followTrail(World world, Entity entity, Vec3d focus, double dt) {
        double entry = Math.max(0.0, 1.0 - shotAge / TRAIL_ENTRY_SECONDS);
        boolean tooClose = trailCam.distanceTo(focus) < MIN_SHOWN_DISTANCE;
        double tau = tooClose ? TRAIL_TAU : TRAIL_TAU + (TRAIL_ENTRY_TAU - TRAIL_TAU) * entry;
        trailCam = trailCam.lerp(trailPlace(world, entity, focus), DroneEasing.follow(dt, tau));
        anchor = trailCam;
    }

    private void followWell(World world, Entity entity, Vec3d focus, double dt) {
        Vec3d above = DroneTerrain.clipped(world, entity, focus, focus.add(0.0, WELL_ABOVE, 0.0), ANCHOR_CLEARANCE);
        Vec3d place = above.distanceTo(focus) >= MIN_SHOWN_DISTANCE ? above : trailPlace(world, entity, focus);
        anchor = anchor.lerp(place, DroneEasing.follow(dt, WELL_TAU));
        trailCam = anchor;
    }

    private Vec3d trailPlace(World world, Entity entity, Vec3d focus) {
        Vec3d visible = trail.atLeast(focus, TRAIL_BEHIND, point -> DroneTerrain.hitDistance(world, entity, point, focus) < 0.0);
        if (visible != null) return visible;

        Vec3d away = trail.atLeast(focus, TRAIL_BEHIND, point -> true);
        if (away != null) return away;
        if (holdsView(world, entity, focus, trailCam)) return trailCam;

        Vec3d above = DroneTerrain.clipped(world, entity, focus, focus.add(0.0, TRAIL_ABOVE, 0.0), ANCHOR_CLEARANCE);
        if (above.distanceTo(focus) >= TRAIL_ABOVE_MIN) return above;

        Vec3d last = trailCam.distanceTo(focus) >= MIN_SHOWN_DISTANCE ? trailCam : trail.farthest(focus);
        return last.distanceTo(focus) >= MIN_SHOWN_DISTANCE ? last : focus.add(0.0, TRAIL_ABOVE, 0.0);
    }

    private boolean holdsView(World world, Entity entity, Vec3d focus, Vec3d place) {
        double away = place.distanceTo(focus);
        return away >= TRAIL_HOLD_MIN && away <= TRAIL_HOLD_MAX
            && !DroneTerrain.insideBlock(world, place)
            && DroneTerrain.hitDistance(world, entity, place, focus) < 0.0;
    }

    private DroneShot takeoffShot(World world, Entity entity, Vec3d focus, double distance) {
        List<DroneShot> options = new ArrayList<>();
        if (roomToWatch(world, entity, focus)) {
            if (allowed.contains(DroneShot.LAUNCH_WATCHER)) options.add(DroneShot.LAUNCH_WATCHER);
            if (allowed.contains(DroneShot.RISE_ABOVE)) options.add(DroneShot.RISE_ABOVE);
        }
        for (DroneShot candidate : TAKEOFF_NORMAL_SHOTS) {
            if (allowed.contains(candidate) && hasRoom(world, entity, focus, candidate, distance)) options.add(candidate);
        }

        if (options.isEmpty()) return climbShot();

        List<DroneShot> fresh = new ArrayList<>(options);
        fresh.remove(lastTakeoff);
        fresh.remove(beforeLastTakeoff);
        List<DroneShot> pool = fresh.isEmpty() ? options : fresh;
        DroneShot picked = pool.get(random.nextInt(pool.size()));
        beforeLastTakeoff = lastTakeoff;
        lastTakeoff = picked;
        return picked;
    }

    private boolean hasRoom(World world, Entity entity, Vec3d focus, DroneShot candidate, double distance) {
        Vec3d way = relative(candidate, focus, 0.0, side, anchor, surfaceY, distance);
        double hit = DroneTerrain.hitDistance(world, entity, focus, focus.add(way));
        return hit < 0.0 || hit >= way.length() * TAKEOFF_ROOM_FRACTION;
    }

    private static boolean followsHim(DroneShot which) {
        return which == DroneShot.LAUNCH_WATCHER || which == DroneShot.RISE_ABOVE;
    }

    private void followTakeoff(World world, Entity entity, Vec3d focus, double dt) {
        anchor = anchor.lerp(takeoffPlace(world, entity, focus), DroneEasing.follow(dt, TAKEOFF_FOLLOW_TAU));
    }

    private Vec3d takeoffPlace(World world, Entity entity, Vec3d focus) {
        Vec3d place = DroneTerrain.clipped(world, entity, focus, focus.add(takeoffOffset), ANCHOR_CLEARANCE);
        if (place.distanceTo(focus) >= WATCHER_MIN_REACH) return place;

        List<Vec3d> others = List.of(
            new Vec3d(-takeoffOffset.x, takeoffOffset.y, -takeoffOffset.z),
            level.multiply(-TAKEOFF_BEHIND).add(0.0, TAKEOFF_BEHIND_HEIGHT, 0.0),
            new Vec3d(0.0, TAKEOFF_ABOVE, 0.0)
        );

        for (Vec3d other : others) {
            Vec3d candidate = DroneTerrain.clipped(world, entity, focus, focus.add(other), ANCHOR_CLEARANCE);
            if (candidate.distanceTo(focus) > place.distanceTo(focus)) {
                place = candidate;
                takeoffOffset = other;
            }
        }

        return place.distanceTo(focus) >= MIN_SHOWN_DISTANCE ? place : focus.add(takeoffOffset);
    }

    private static boolean staysPut(DroneShot which) {
        return which.has(DroneShotTrait.ANCHORED) || which == DroneShot.ROOM_DRIFT;
    }

    private boolean leftBehind(Vec3d focus, double limit) {
        return staysPut(shot)
            && focus.distanceTo(anchor) > limit
            && focus.subtract(anchor).dotProduct(direction) > 0.0;
    }

    private void trackInterest(double dt) {
        Vec3d raw = stage.interest();
        double follow = DroneEasing.follow(dt, INTEREST_TAU);

        if (raw != null) {
            interestPoint = interestPoint == null || interestWeight < INTEREST_SNAP_WEIGHT ? raw : interestPoint.lerp(raw, follow);
        }

        interestWeight += ((stage.combat() ? 1.0 : 0.0) - interestWeight) * follow;
        if (interestWeight < INTEREST_MIN_WEIGHT) interestWeight = 0.0;
    }

    private Vec3d lookTarget(Vec3d focus, Vec3d filmed) {
        if (interestPoint == null || interestWeight <= 0.0 || interestPoint.distanceTo(focus) > MAX_INTEREST_DISTANCE) return filmed;

        return filmed.lerp(focus.lerp(interestPoint, 0.5), DroneEasing.smoothstep(interestWeight));
    }

    private double between(double low, double high) {
        return low + random.nextDouble() * (high - low);
    }

    private Vec3d aroundLevel(double degrees, double reach) {
        double radians = Math.toRadians(degrees);
        Vec3d right = new Vec3d(-level.z, 0.0, level.x);
        return level.multiply(Math.cos(radians)).add(right.multiply(Math.sin(radians))).multiply(reach);
    }

    private double travelSeconds(Vec3d focus, double distance) {
        Vec3d target = focus.add(relative(shot, focus, shotAge, side, anchor, surfaceY, distance));
        double travel = target.distanceTo(cameraPosition);
        double minimum = followsHim(shot) ? TAKEOFF_MIN_BLEND_SECONDS : MIN_ABSOLUTE_BLEND_SECONDS;
        return MathHelper.clamp(travel / ABSOLUTE_BLEND_SPEED, minimum, MAX_ABSOLUTE_BLEND_SECONDS);
    }

    private Vec3d relative(DroneShot which, Vec3d focus, double age, double sideSign, Vec3d anchorPoint, double surface, double distance) {
        double reach = which == DroneShot.CHASE_THREE_QUARTER ? distance * chaseScale : distance;
        return which.position(new DroneShotContext(focus, level, sideSign, age, orbitAngle, reach, anchorPoint, surface, shotSeconds, chaseSide, interestPoint))
            .subtract(focus);
    }

    private double horizontal() {
        return Math.hypot(direction.x, direction.z);
    }

    private boolean due(Vec3d focus, double distance) {
        if (shot.has(DroneShotTrait.SCENE)) return false;
        if (shotAge >= limit()) return true;
        if (shot == DroneShot.GROUND_SKIM && surfaceLost > SURFACE_LOST_SECONDS) return true;

        return shot.has(DroneShotTrait.ANCHORED)
            && shotAge >= blendSeconds + ANCHORED_PASSED_AFTER_BLEND_SECONDS
            && focus.subtract(anchor).dotProduct(direction) > distance * ANCHOR_PASSED_FACTOR;
    }

    private double limit() {
        double base = shot.has(DroneShotTrait.ANCHORED) ? Math.min(shotSeconds, ANCHORED_MAX_SECONDS) : shotSeconds;
        return opening ? Math.min(base, OPENING_SHOT_MAX_SECONDS) : base;
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
            orbitAngle = stage.scene() == DroneScene.TAKEOFF ? random.nextDouble() * 2.0 * Math.PI : Math.atan2(-level.z, -level.x);
        }

        if (shot == DroneShot.TRAIL_CAM) {
            trailCam = cameraPosition != null ? cameraPosition : trailPlace(world, entity, focus);
            anchor = trailCam;
            return;
        }

        if (shot == DroneShot.CLIMB_SIDE) {
            climb.begin(world, entity, focus, cameraPosition);
            anchor = cameraPosition != null ? cameraPosition : focus;
            return;
        }

        if (shot == DroneShot.WELL_TOP) {
            trailCam = cameraPosition != null ? cameraPosition : focus;
            anchor = trailCam;
            return;
        }

        if (shot == DroneShot.ROOM_DRIFT) {
            DroneRoomLoop loop = new DroneRoomLoop(stage.roomCorners());
            driftAlong = loop.farthestFrom(focus);
            driftDirection = random.nextBoolean() ? 1.0 : -1.0;
            driftPosition = loop.at(driftAlong);
            driftLook = focus;
            anchor = driftPosition;
            return;
        }

        if (shot == DroneShot.LAUNCH_WATCHER) {
            takeoffOffset = aroundLevel(side * between(WATCHER_MIN_ANGLE, WATCHER_MAX_ANGLE), between(WATCHER_MIN_REACH_WANTED, WATCHER_MAX_REACH_WANTED))
                .add(0.0, between(WATCHER_MIN_HEIGHT, WATCHER_MAX_HEIGHT), 0.0);
            anchor = takeoffPlace(world, entity, focus);
            return;
        }

        if (shot == DroneShot.RISE_ABOVE) {
            takeoffOffset = aroundLevel(between(0.0, 360.0), between(RISE_MIN_REACH, RISE_MAX_REACH))
                .add(0.0, between(RISE_MIN_HEIGHT, RISE_MAX_HEIGHT), 0.0);
            anchor = takeoffPlace(world, entity, focus);
            return;
        }

        if (!shot.has(DroneShotTrait.ANCHORED)) return;

        double ahead = Math.min(ANCHOR_MAX_AHEAD, speed * ANCHOR_LEAD_SECONDS);
        Vec3d origin = cameraPosition != null ? cameraPosition : focus;
        Vec3d heading = cameraVelocity.length() > MIN_CAMERA_SPEED ? cameraVelocity.normalize() : direction;
        Vec3d path = DroneTerrain.clipped(world, entity, origin, origin.add(heading.multiply(ahead)), ANCHOR_CLEARANCE);
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
        Vec3d from = relative(previousShot, focus, previousAge, previousSide, previousAnchor, previousSurfaceY, distance);
        Vec3d to = relative(shot, focus, shotAge, side, anchor, surfaceY, distance);
        double arc = Math.abs(MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(to.z, to.x) - Math.atan2(from.z, from.x))));
        return MIN_ARC_BLEND_SECONDS + (MAX_ARC_BLEND_SECONDS - MIN_ARC_BLEND_SECONDS) * Math.min(1.0, arc / 180.0);
    }

    private void drift(Vec3d focus, double dt) {
        if (stage.roomCorners().isEmpty()) {
            driftLook = driftLook.lerp(focus, DroneEasing.follow(dt, LEAVE_LOOK_TAU));
            return;
        }

        DroneRoomLoop loop = new DroneRoomLoop(stage.roomCorners());
        double here = loop.at(driftAlong).distanceTo(focus);
        if (here < DRIFT_MIN_DISTANCE && loop.at(driftAlong + driftDirection).distanceTo(focus) < here) driftDirection = -driftDirection;
        driftAlong += driftDirection * DRIFT_SPEED * dt;
        driftPosition = driftPosition.lerp(loop.at(driftAlong), DroneEasing.follow(dt, DRIFT_POSITION_TAU));
        driftLook = driftLook.lerp(focus, DroneEasing.follow(dt, DRIFT_LOOK_TAU));
        anchor = driftPosition;
    }

    private Vec3d lookFor(DroneShot which, Vec3d focus) {
        if (which == DroneShot.CLIMB_SIDE) return climb.pivot().subtract(focus);

        return which == DroneShot.ROOM_DRIFT && driftLook != null ? driftLook.subtract(focus) : lead.multiply(which.lead());
    }

    private Vec3d keepAway(Vec3d from, Vec3d to, double eased, double distance) {
        double minimum = Math.min(
            Math.max(MIN_BLEND_DISTANCE, distance * MIN_BLEND_DISTANCE_FACTOR),
            Math.min(from.length(), to.length())
        );
        return DroneBypass.apply(from.lerp(to, eased), bypass, minimum);
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

    private double liftFor(World world, Entity entity, Vec3d position, double dt, boolean free) {
        double ground = free ? -1.0 : DroneTerrain.hitDistance(world, entity, position, position.add(0.0, -GROUND_CLEARANCE, 0.0));
        double wanted = ground < 0.0 ? 0.0 : GROUND_CLEARANCE - ground;

        if (wanted > 0.0) {
            double ceiling = DroneTerrain.hitDistance(world, entity, position, position.add(0.0, wanted + CLEARANCE, 0.0));
            if (ceiling >= 0.0) wanted = Math.max(0.0, ceiling - CLEARANCE);
        }

        lift += (wanted - lift) * DroneEasing.follow(dt, LIFT_TAU);
        return lift;
    }

    private Vec3d limitSweep(Vec3d focus, Vec3d position, double dt, boolean gliding) {
        double target = stage.scene() == DroneScene.TAKEOFF ? TAKEOFF_SWEEP_SPEED : gliding ? FLIGHT_SWEEP_SPEED : FOOT_SWEEP_SPEED;
        double tau = target > sweepSpeed ? SWEEP_RISE_TAU : SWEEP_FALL_TAU;
        sweepSpeed += (target - sweepSpeed) * DroneEasing.follow(dt, tau);

        Vec3d relative = position.subtract(focus);
        if (lastRelative != null) {
            Vec3d step = relative.subtract(lastRelative);
            double most = sweepSpeed * dt;
            if (step.length() > most) relative = lastRelative.add(step.normalize().multiply(most));
        }

        lastRelative = relative;
        return focus.add(relative);
    }

    private static Vec3d withinReach(Vec3d focus, Vec3d position) {
        Vec3d way = position.subtract(focus);
        double length = way.length();
        return length <= MAX_CAMERA_DISTANCE ? position : focus.add(way.multiply(MAX_CAMERA_DISTANCE / length));
    }

    private Vec3d clip(World world, Entity entity, Vec3d focus, Vec3d position, double dt, boolean free, boolean gliding) {
        Vec3d way = position.subtract(focus);
        double length = way.length();
        double reachable = 1.0;
        boolean held = false;
        boolean blocked = false;
        floored = false;

        if (!free && length > 1.0e-4) {
            double hit = DroneTerrain.hitDistance(world, entity, focus, position);
            blocked = hit >= 0.0;
            if (blocked) reachable = Math.max(0.0, Math.min(1.0, (hit - CLEARANCE) / length));

            double floor = shot == DroneShot.WING_CAM || length <= MIN_SHOWN_DISTANCE ? 0.0 : MIN_SHOWN_DISTANCE / length;
            floored = blocked && reachable < floor;
            if (floored) reachable = floor;

            if (blocked && !gliding && length > MIN_SHOWN_DISTANCE) {
                double hold = footBlockedSeconds < FOOT_OBSTACLE_SECONDS ? Math.max(clipFraction, floor) : floor;
                held = !DroneTerrain.insideBlock(world, focus.add(way.multiply(hold)));
                if (held) reachable = Math.max(reachable, hold);
            }
        }

        footBlockedSeconds = blocked && !gliding ? footBlockedSeconds + dt : 0.0;
        double shorten = held || sinceUnclipped < RELEASED_CLIP_SECONDS ? RELEASED_CLIP_TAU : CLIP_SHORTEN_TAU;
        double tau = reachable < clipFraction ? shorten : CLIP_RECOVER_TAU;
        clipFraction += (reachable - clipFraction) * DroneEasing.follow(dt, tau);

        return focus.add(way.multiply(clipFraction));
    }

    private DroneShot draw(World world, Entity entity, Vec3d focus, boolean atStart) {
        boolean levelFlight = stableSeconds >= LEVEL_FLIGHT_STABLE_SECONDS && horizontal() > LEVEL_FLIGHT_MIN_HORIZONTAL;
        boolean groundChecked = false;
        OptionalDouble ground = OptionalDouble.empty();
        DroneShot chosen = null;

        for (int pass = 0; pass < 2 && chosen == null; pass++) {
            if (pass == 1 || bag.isEmpty()) refill();
            bag.removeIf(candidate -> !allowed.contains(candidate));

            for (DroneShot candidate : bag) {
                if (candidate == lastDrawn) continue;
                if (atStart && !OPENING_SHOTS.contains(candidate)) continue;
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
            DroneShot fallback = firstNormalAllowed();
            chosen = fallback != null ? fallback : DroneShot.CHASE_THREE_QUARTER;
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
        List<DroneShot> shuffled = new ArrayList<>();
        for (DroneShot candidate : DroneShot.values()) {
            if (!candidate.has(DroneShotTrait.SCENE)) shuffled.add(candidate);
        }
        shuffled.add(DroneShot.TOP_DOWN);
        shuffled.add(DroneShot.WIDE_CRANE);
        shuffled.add(DroneShot.CHASE_THREE_QUARTER);
        Collections.shuffle(shuffled, random);
        bag.addAll(shuffled);
    }
}
