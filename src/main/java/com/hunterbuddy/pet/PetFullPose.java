package com.hunterbuddy.pet;

import java.util.Random;

public final class PetFullPose {
    private static final int IDLE = 0;
    private static final int CALM = 1;
    private static final int TURN = 2;
    private static final int ENTER = 3;
    private static final int HOLD = 4;
    private static final int EXIT = 5;
    private final Random random = new Random();
    private final PetDanceSequence dance = new PetDanceSequence();
    public PetResources.FullPose current;
    public PetResources.FullPose alternate;
    public float blend;
    public float previousBlend;
    public float mix;
    public float previousMix;
    public float age;
    private PetResources.FullPose selection;
    private PetResources.FullPose spontaneous;
    private PetResources.FullPose lastSpontaneous;
    private int phase;
    private float phaseAge;
    private float cooldown;
    private float spontaneousDelay = 60;
    private float spontaneousRemaining;
    private boolean turned;
    private boolean waiting;
    private boolean sleeping;
    private boolean dancing;
    private int turnDirection;

    public void reset() {
        current = alternate = selection = spontaneous = lastSpontaneous = null;
        blend = previousBlend = mix = previousMix = 0;
        phase = IDLE;
        age = phaseAge = cooldown = spontaneousRemaining = 0;
        spontaneousDelay = 60;
        turned = waiting = sleeping = dancing = false;
    }

    public boolean blocksMotion() {
        return phase != IDLE || waiting;
    }

    public boolean ready(String name) {
        return phase == HOLD && selection != null && selection.name().equals(name);
    }

    public static PetResources.FullPose find(PetResources.Rig rig, String name) {
        if (rig == null || name == null) return null;
        for (PetResources.FullPose pose : rig.poses()) if (pose.name().equals(name)) return pose;
        return null;
    }

    public void update(float dt, PetResources.Rig rig, String linePose, PetBrain brain, PetMotion motion, PetPose puppet, double frequency) {
        previousBlend = blend;
        previousMix = mix;
        age += dt;
        phaseAge += dt;
        cooldown = Math.max(0, cooldown - dt);
        spontaneousDelay -= dt;
        PetResources.FullPose desired = find(rig, linePose);
        if (brain.resting()) {
            desired = brain.state == PetState.SLEEP || brain.state == PetState.LIE_DOWN && puppet.recline >= 0.85f
                ? find(rig, "sleep") : null;
        } else if (desired == null) desired = activity(rig, brain);
        if (desired != null && desired.name().equals("sit") && (!motion.elevated || !motion.atEdge)) desired = null;
        if (desired != null && desired.name().equals("sleep") && !brain.resting()) desired = null;
        if (phase == HOLD && spontaneous != null) spontaneousRemaining -= dt;
        if (desired == null && spontaneousRemaining > 0 && !brain.resting()) desired = spontaneous;
        if (desired == null && phase == IDLE && cooldown == 0 && spontaneousDelay <= 0 && frequency > 0
            && brain.state == PetState.REST && !brain.danger() && motion.travel == PetMotion.Travel.GROUNDED) {
            PetResources.FullPose first = find(rig, "moon");
            PetResources.FullPose second = find(rig, "swagger");
            spontaneous = first == lastSpontaneous && second != null ? second : first != null ? first : second;
            lastSpontaneous = spontaneous;
            spontaneousRemaining = 3 + random.nextFloat() * 3;
            spontaneousDelay = (60 + random.nextFloat() * 90) / (float) frequency;
            desired = spontaneous;
        }
        boolean grounded = motion.travel == PetMotion.Travel.GROUNDED;
        boolean sleepRequest = desired != null && desired.name().equals("sleep") && brain.resting();
        waiting = phase == IDLE && desired != null && cooldown > 0 && grounded && !sleepRequest;
        if (phase == IDLE && desired != null && cooldown == 0 && grounded) {
            current = selection = desired;
            sleeping = sleepRequest;
            dancing = PetResources.danceNumber(desired.name()) > 0 && rig.dances().length > 1;
            if (dancing) dance.start(rig.dances(), rig.danceOrder(), rig.previousDanceOrder(), desired);
            phase = sleeping ? ENTER : CALM;
            phaseAge = 0;
            turnDirection = -motion.direction;
            if (desired.name().equals("sit")) turnDirection = motion.edgeDirection * (desired.hangingX() < 0 ? -1 : 1);
        }
        if (phase != IDLE && phase != EXIT && (desired != selection || !grounded)) {
            phase = EXIT;
            phaseAge = 0;
        }
        if (phase == CALM && grounded && phaseAge >= 0.30f && puppet.calm()) {
            phase = TURN;
            phaseAge = 0;
            turned = false;
        }
        if (phase == TURN) {
            if (!turned && phaseAge >= 0.25f) {
                motion.direction = turnDirection;
                turned = true;
            }
            if (phaseAge >= 0.5f) {
                phase = ENTER;
                phaseAge = age = 0;
            }
        }
        if (phase == ENTER) {
            blend = Math.min(1, blend + dt / 0.25f);
            if (blend == 1) { phase = HOLD; phaseAge = 0; }
        } else if (phase == EXIT) {
            blend = Math.max(0, blend - dt / 0.25f);
            if (blend == 0 && previousBlend == 0) {
                phase = IDLE;
                current = alternate = selection = spontaneous = null;
                spontaneousRemaining = 0;
                cooldown = 8;
                mix = previousMix = 0;
                sleeping = dancing = false;
            }
        }
        if (phase == HOLD && dancing) {
            dance.update(dt);
            current = dance.current;
            alternate = dance.next;
            mix = dance.mix;
            previousMix = dance.previousMix;
        }
        brain.preparingPose = !sleeping && (waiting || phase == CALM || phase == TURN || phase == ENTER || phase == EXIT);
        brain.holdingOverlay = sleeping && phase == EXIT || phase == HOLD && (selection == spontaneous
            || selection == find(rig, linePose) && selection != activity(rig, brain) && !sleeping);
        brain.imagePose = !sleeping && current != null && (phase == ENTER || phase == HOLD || phase == EXIT);
        brain.poseTurn = phase == TURN ? (float) Math.sin(Math.PI * Math.min(1, phaseAge / 0.5f)) : 0;
    }

    private static PetResources.FullPose activity(PetResources.Rig rig, PetBrain brain) {
        String name = switch (brain.state) {
            case SIT -> "sit";
            case DRINK -> "drink";
            case WAVE -> "wave";
            case PANIC, HIDE -> "scared";
            default -> null;
        };
        if (brain.state == PetState.DANCE && brain.imageDance && rig.dances().length > 0) {
            return rig.dances()[Math.floorMod(brain.danceImageStart, rig.dances().length)];
        }
        return find(rig, name);
    }
}
