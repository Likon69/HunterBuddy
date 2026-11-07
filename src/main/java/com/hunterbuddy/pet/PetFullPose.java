package com.hunterbuddy.pet;

import com.mojang.logging.LogUtils;

public final class PetFullPose {
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();
    private static final int IDLE = 0;
    private static final int CALM = 1;
    private static final int TURN = 2;
    private static final int ENTER = 3;
    private static final int HOLD = 4;
    private static final int EXIT = 5;
    private static final float HALF = 0.25f;
    private static final float QUICK = 0.1f;
    private final PetDanceSequence dance = new PetDanceSequence();
    public PetResources.FullPose current;
    public PetResources.FullPose alternate;
    public float blend;
    public float previousBlend;
    public float squash = 1;
    public float previousSquash = 1;
    public float mix;
    public float previousMix;
    public float age;
    public float previousAge;
    private PetResources.FullPose selection;
    private int phase;
    private float phaseAge;
    private float cooldown;
    private boolean turned;
    private boolean sleeping;
    private boolean dancing;
    private boolean resuming;
    private int turnDirection;
    private int seenInstance;

    public void reset() {
        current = alternate = selection = null;
        blend = previousBlend = mix = previousMix = 0;
        squash = previousSquash = 1;
        phase = IDLE;
        age = previousAge = phaseAge = cooldown = 0;
        turned = sleeping = dancing = resuming = false;
    }

    public float age(float delta) {
        return previousAge + (age - previousAge) * delta;
    }

    public boolean blocksMotion() {
        return phase != IDLE;
    }

    public boolean ready(String name) {
        return (phase == ENTER || phase == HOLD) && selection != null && selection.name().equals(name);
    }

    public static PetResources.FullPose find(PetResources.Rig rig, String name) {
        if (rig == null || name == null) return null;
        for (PetResources.FullPose pose : rig.poses()) if (pose.name().equals(name)) return pose;
        return null;
    }

    public void update(float dt, PetResources.Rig rig, String linePose, PetBrain brain, PetMotion motion, PetPose puppet) {
        previousBlend = blend;
        previousSquash = squash;
        previousMix = mix;
        previousAge = age;
        age += dt;
        phaseAge += dt;
        cooldown = Math.max(0, cooldown - dt);
        if (brain.instance != seenInstance) {
            seenInstance = brain.instance;
            if (phase != IDLE || cooldown > 0) brain.denyImage();
        }
        PetResources.FullPose desired = find(rig, linePose);
        if (brain.resting()) {
            desired = brain.state == PetState.SLEEP || brain.state == PetState.RISE && brain.age < brain.duration * 0.45f
                || brain.state == PetState.LIE_DOWN
                ? find(rig, "sleep") : null;
        } else if (desired == null) desired = activity(rig, brain);
        boolean held = phase != IDLE && phase != EXIT && selection != null && selection.name().equals("sit") && brain.seatHeld();
        if (desired != null && desired.name().equals("sit") && (!(motion.elevated && motion.atEdge || held) || brain.state == PetState.SIT && !brain.imageSit)) desired = null;
        if (desired != null && desired.name().equals("sleep") && !brain.resting()) desired = null;
        boolean grounded = motion.travel == PetMotion.Travel.GROUNDED;
        boolean sleepRequest = desired != null && desired.name().equals("sleep") && brain.resting();
        if (phase == IDLE && desired != null && (cooldown == 0 || sleepRequest) && grounded) {
            current = selection = desired;
            sleeping = sleepRequest;
            dancing = PetResources.danceNumber(desired.name()) > 0 && rig.dances().length > 1;
            if (dancing) dance.start(rig.dances(), rig.danceOrder(), rig.previousDanceOrder(), desired);
            resuming = desired.name().equals("sit") && puppet.seated > 0.5f;
            phase = sleeping || resuming ? ENTER : CALM;
            phaseAge = 0;
            turnDirection = -motion.direction;
            if (desired.name().equals("sit")) turnDirection = motion.edgeDirection * (desired.hangingX() < 0 ? -1 : 1);
        }
        if (phase != IDLE && phase != EXIT && (desired != selection || !grounded)) {
            if (selection.name().equals("sit")) brain.dropSitImage();
            phase = EXIT;
            phaseAge = 0;
        }
        if (phase == CALM && grounded && phaseAge >= 0.30f && puppet.calm()) {
            phase = TURN;
            phaseAge = 0;
            turned = false;
        }
        if (phase == TURN) {
            if (!turned && phaseAge >= HALF) {
                motion.direction = turnDirection;
                turned = true;
            }
            swap(phaseAge, HALF);
            if (phaseAge >= 2 * HALF) {
                phase = HOLD;
                phaseAge = age = previousAge = 0;
                LOG.info("[pet] pose " + selection.name());
            }
        }
        if (phase == ENTER) {
            swap(phaseAge, QUICK);
            if (phaseAge >= 2 * QUICK) {
                phase = HOLD;
                phaseAge = age = previousAge = 0;
            }
        } else if (phase == EXIT) {
            if (blend > 0) blend = blend - dt / HALF < 0.0001f ? 0 : blend - dt / HALF;
            else squash = Math.min(1, squash + dt / HALF);
            if (squash == 1 && previousSquash == 1 && blend == 0) {
                phase = IDLE;
                current = alternate = selection = null;
                cooldown = 8;
                mix = previousMix = 0;
                sleeping = dancing = false;
            }
        }
        if (dancing && (phase == HOLD || phase == EXIT)) {
            if (phase == HOLD) dance.update(dt);
            else dance.finish(dt);
            current = dance.current;
            alternate = dance.next;
            mix = dance.mix;
            previousMix = dance.previousMix;
        }
        brain.keepSeated = resuming && phase == ENTER;
        brain.preparingPose = !sleeping && (phase == CALM || phase == TURN || phase == ENTER || phase == EXIT);
        brain.holdingOverlay = sleeping && phase == EXIT || phase == HOLD
            && selection == find(rig, linePose) && selection != activity(rig, brain) && !sleeping;
        brain.imagePose = !sleeping && current != null && (phase == ENTER || phase == HOLD || phase == EXIT);
    }

    private void swap(float elapsed, float half) {
        squash = Math.max(0, 1 - elapsed / half);
        blend = Math.max(0, Math.min(1, (elapsed - half) / half));
    }

    private static PetResources.FullPose activity(PetResources.Rig rig, PetBrain brain) {
        String name = switch (brain.state) {
            case SIT -> brain.imageSit ? "sit" : null;
            case DRINK -> brain.imageDrink ? "drink" : null;
            case WAVE -> "wave";
            case PANIC -> "scared";
            default -> null;
        };
        if (brain.state == PetState.DANCE && brain.imageDance && rig.dances().length > 0) {
            return rig.dances()[Math.floorMod(brain.danceImageStart, rig.dances().length)];
        }
        return find(rig, name);
    }
}
