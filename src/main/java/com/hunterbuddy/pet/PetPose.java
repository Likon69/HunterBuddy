package com.hunterbuddy.pet;

import java.util.Arrays;

public final class PetPose {
    private static final float LIE_CROUCH = 0.35f;
    private static final float SMOKE_REACH = 0.55f;
    private static final float THROW_WIND_UP = 0.9f;
    private static final float THROW_RELEASE = -0.3f;
    public final float[] angles = new float[6];
    public final float[] previousAngles = new float[6];
    private final float[] target = new float[6];
    private final float[] custom = new float[PetAnimation.JOINTS.length];
    private final float[] liftTarget = new float[2];
    public final float[] legLift = new float[2];
    public final float[] previousLegLift = new float[2];
    public final PetDrink drink = new PetDrink();
    public float fold;
    public float previousFold;
    public float seated;
    public float previousSeated;
    public float crouch;
    public float previousCrouch;
    public float turn;
    public float previousTurn;
    public float shift;
    public float previousShift;
    public float bob;
    public float previousBob;
    public float lean;
    public float previousLean;
    public boolean turned;
    private int direction = 1;
    private float clock;
    private PetResources.Rig loaded;

    public void reset() {
        Arrays.fill(angles, 0);
        Arrays.fill(previousAngles, 0);
        Arrays.fill(legLift, 0);
        Arrays.fill(previousLegLift, 0);
        fold = previousFold = 0;
        seated = previousSeated = 0;
        crouch = previousCrouch = 0;
        turn = previousTurn = 0;
        bob = previousBob = 0;
        shift = previousShift = 0;
        lean = previousLean = 0;
        clock = 0;
        loaded = null;
        turned = false;
        direction = 1;
    }

    public boolean calm() {
        if (fold > 0.01f || seated > 0.01f) return false;
        if (Math.abs(bob) > 0.3f || Math.abs(shift) > 0.3f || Math.abs(lean) > 0.01f) return false;
        for (float angle : angles) if (Math.abs(angle) > 0.035f) return false;
        for (float lift : legLift) if (Math.abs(lift) > 0.5f) return false;
        return true;
    }

    public void update(PetBrain brain, PetResources.Rig rig, PetMotion motion, float dt) {
        if (loaded != rig) {
            drink.solve(rig);
            loaded = rig;
        }
        System.arraycopy(angles, 0, previousAngles, 0, angles.length);
        System.arraycopy(legLift, 0, previousLegLift, 0, legLift.length);
        previousFold = fold;
        previousSeated = seated;
        previousCrouch = crouch;
        previousTurn = turn;
        previousBob = bob;
        previousShift = shift;
        previousLean = lean;
        turned = direction != motion.direction;
        direction = motion.direction;
        clock += dt;
        Arrays.fill(target, 0);
        Arrays.fill(liftTarget, 0);
        float step = (float) Math.sin(clock * 6);
        float breathing = (float) Math.sin(brain.age * 2);
        float nextFold = 0;
        float nextSeat = 0;
        float nextCrouch = motion.landing * 0.12f;
        float nextTurn = 0;
        float nextBob = 0;
        float nextShift = 0;
        boolean still = (brain.preparingPose || brain.imagePose) && !brain.sitDrink && !brain.keepSeated;
        PetState state = still ? PetState.REST : brain.state;
        float nextLean = still || brain.resting() || state == PetState.SIT ? 0
            : (float) Math.sin(brain.age * 2.7) * brain.drunkenness * 0.16f;
        PetAnimation playing = state == PetState.CUSTOM ? brain.animation : null;
        switch (state) {
            case WALK, STAGGER, HIDE -> {
                target[4] = step * 0.4f;
                target[5] = -target[4];
                target[2] = -step * 0.23f;
                target[3] = -target[2];
                if (state == PetState.WALK) nextBob = -Math.abs(step) * 3;
                target[1] = state == PetState.STAGGER ? step * brain.drunkenness * 0.12f : 0;
                if (state == PetState.HIDE) nextCrouch = 0.18f;
            }
            case SMOKE -> {
                float t = brain.age / brain.duration;
                target[0] = 0.1f;
                target[2] = towardMouth(rig, 2, 0, target[0]) * SMOKE_REACH * smooth(Math.min(t / 0.2f, (1 - t) / 0.2f));
            }
            case DRINK -> drinkMotion(rig, brain.drinkProgress());
            case SIT -> {
                nextSeat = motion.atEdge ? 1 : 0;
                target[4] = step * 0.10f;
                target[5] = -target[4];
                if (brain.sitDrink) drinkMotion(rig, brain.drinkProgress());
            }
            case SETTLE -> nextCrouch = LIE_CROUCH * smooth(brain.age / brain.duration);
            case LIE_DOWN, SLEEP -> nextCrouch = LIE_CROUCH;
            case RISE -> {
                float t = brain.age / brain.duration;
                nextCrouch = LIE_CROUCH * (1 - smooth((t - 0.45f) / 0.55f));
            }
            case DANCE -> {
                if (playing != null) break;
                float rhythm = switch (brain.danceStyle) {
                    case 1 -> (float) Math.sin(clock * 9);
                    case 2 -> (float) Math.sin(clock * 3.5);
                    case 3 -> (float) Math.sin(clock * 5 + Math.sin(clock * 2));
                    default -> step;
                };
                target[1] = rhythm * (brain.danceStyle == 2 ? 0.12f : 0.08f);
                target[4] = rhythm * (brain.danceStyle == 1 ? 0.22f : brain.danceStyle == 3 ? 0.18f : 0.12f);
                target[5] = brain.danceStyle == 2 ? (float) Math.sin(clock * 3.5f + 1.2f) * 0.15f : -target[4];
                target[0] = brain.danceStyle == 3 ? -rhythm * 0.05f : 0;
                target[2] = rhythm * 0.45f;
                target[3] = -target[2];
                nextCrouch = Math.abs(rhythm) * (brain.danceStyle == 1 ? 0.12f : 0.08f);
            }
            case WAVE -> {
                target[2] = 0.5f + step * 0.1f;
                target[0] = 0.04f;
            }
            case PANIC -> {
                target[0] = step * 0.08f;
                target[2] = 0.5f + step * 0.15f;
                target[3] = -0.55f - step * 0.15f;
                nextBob = step * 2;
            }
            case CELEBRATE -> {
                target[0] = step * 0.08f;
                target[2] = 0.5f;
                target[3] = -0.55f;
                nextBob = -Math.abs(step) * 14;
            }
            case TURN -> nextTurn = (float) Math.sin(Math.PI * Math.min(1, brain.age / brain.duration));
            case STUMBLE -> nextFold = 0.9f * (float) Math.sin(Math.PI * Math.min(1, brain.age / brain.duration));
            case THROW -> target[3] = brain.age < brain.duration * 0.55f ? THROW_WIND_UP : THROW_RELEASE;
            case REST -> {
                if (!still) nextBob = breathing * 0.8f;
            }
        }
        if (playing != null) {
            playing.sample(brain.age, custom);
            System.arraycopy(custom, 0, target, 0, target.length);
            liftTarget[0] = custom[PetAnimation.LEFT_LIFT];
            liftTarget[1] = custom[PetAnimation.LEFT_LIFT + 1];
            nextLean = custom[PetAnimation.LEAN];
            nextCrouch += custom[PetAnimation.CROUCH];
            nextBob = custom[PetAnimation.BOB];
            nextShift = custom[PetAnimation.SHIFT];
        }
        if (motion.travel != PetMotion.Travel.GROUNDED) {
            nextBob = 0;
            nextFold = 0;
            nextSeat = 0;
            target[2] = step * 0.09f;
            target[3] = -step * 0.07f;
            target[4] = step * 0.2f;
            target[5] = -target[4];
        }
        float blend = 1 - (float) Math.exp(-dt * 12);
        for (int i = 0; i < angles.length; i++) angles[i] += (target[i] - angles[i]) * blend;
        float drinking = brain.drinkProgress();
        if ((state == PetState.DRINK || state == PetState.SIT && brain.sitDrink) && drinking >= 0.42f && drinking <= 0.73f) {
            angles[0] = target[0];
            angles[3] = target[3];
        }
        for (int i = 0; i < legLift.length; i++) legLift[i] += (liftTarget[i] - legLift[i]) * blend;
        fold += (nextFold - fold) * blend;
        seated += (nextSeat - seated) * blend;
        crouch += (nextCrouch - crouch) * blend;
        bob += (nextBob - bob) * blend;
        shift += (nextShift - shift) * blend;
        lean += (nextLean - lean) * blend;
        turn = nextTurn;
    }

    private void drinkMotion(PetResources.Rig rig, float t) {
        float head = smooth(Math.min(t / 0.22f, (1 - t) / 0.10f));
        float arm = smooth(Math.min((t - 0.22f) / 0.18f, (0.88f - t) / 0.13f));
        if (drink.available) {
            target[0] = drink.headAngle * head;
            target[3] = drink.armAngle * arm;
        } else {
            target[0] = -0.28f * head;
            target[3] = towardMouth(rig, 3, 1, target[0]) * arm;
        }
    }

    static float towardMouth(PetResources.Rig rig, int owner, int anchor, float headAngle) {
        float x = rig.anchors()[4];
        float y = rig.anchors()[5];
        for (PetResources.Part part : rig.parts()) {
            if (part.index() != 0) continue;
            float dx = x - part.pivotX();
            float dy = y - part.pivotY();
            float sin = (float) Math.sin(headAngle);
            float cos = (float) Math.cos(headAngle);
            x = part.pivotX() + dx * cos - dy * sin;
            y = part.pivotY() + dx * sin + dy * cos;
            break;
        }
        for (PetResources.Part part : rig.parts()) {
            if (part.index() != owner) continue;
            return (float) (Math.atan2(y - part.pivotY(), x - part.pivotX())
                - Math.atan2(rig.anchors()[anchor * 2 + 1] - part.pivotY(), rig.anchors()[anchor * 2] - part.pivotX()));
        }
        return 0;
    }

    private static float smooth(float value) {
        float t = Math.max(0, Math.min(1, value));
        return t * t * (3 - 2 * t);
    }
}
