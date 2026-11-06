package com.hunterbuddy.pet;

import java.util.Arrays;

public final class PetPose {
    public final float[] angles = new float[6];
    public final float[] previousAngles = new float[6];
    private final float[] target = new float[6];
    public final PetDrink drink = new PetDrink();
    public float fold;
    public float previousFold;
    public float recline;
    public float previousRecline;
    public float seated;
    public float previousSeated;
    public float crouch;
    public float previousCrouch;
    public float wall;
    public float previousWall;
    public float turn;
    public float previousTurn;
    public boolean turned;
    private int direction = 1;
    private float clock;
    private PetResources.Rig loaded;

    public void reset() {
        Arrays.fill(angles, 0);
        Arrays.fill(previousAngles, 0);
        fold = previousFold = 0;
        recline = previousRecline = 0;
        seated = previousSeated = 0;
        crouch = previousCrouch = 0;
        wall = previousWall = 0;
        turn = previousTurn = 0;
        clock = 0;
        loaded = null;
        turned = false;
        direction = 1;
    }

    public boolean calm() {
        if (fold > 0.01f || recline > 0.01f || seated > 0.01f || Math.abs(wall) > 0.02f) return false;
        for (float angle : angles) if (Math.abs(angle) > 0.035f) return false;
        return true;
    }

    public void update(PetBrain brain, PetResources.Rig rig, PetMotion motion, float dt) {
        if (loaded != rig) {
            drink.solve(rig);
            loaded = rig;
        }
        System.arraycopy(angles, 0, previousAngles, 0, angles.length);
        previousFold = fold;
        previousRecline = recline;
        previousSeated = seated;
        previousCrouch = crouch;
        previousWall = wall;
        previousTurn = turn;
        turned = direction != motion.direction;
        direction = motion.direction;
        clock += dt;
        Arrays.fill(target, 0);
        float step = (float) Math.sin(clock * 6);
        float nextFold = 0;
        float nextRecline = 0;
        float nextSeat = 0;
        float nextCrouch = motion.landing * 0.12f;
        float nextTurn = brain.poseTurn;
        PetState state = brain.preparingPose || brain.imagePose ? PetState.REST : brain.state;
        switch (state) {
            case WALK, STAGGER, HIDE -> {
                target[4] = step * 0.28f;
                target[5] = -target[4];
                target[2] = step * 0.07f;
                target[3] = -step * 0.05f;
                target[1] = state == PetState.STAGGER ? step * brain.drunkenness * 0.12f : 0;
                if (state == PetState.HIDE) nextCrouch = 0.18f;
            }
            case SMOKE -> target[0] = 0.04f;
            case DRINK -> {
                if (drink.available) {
                    float t = brain.age / brain.duration;
                    float head = smooth(Math.min(t / 0.22f, (1 - t) / 0.10f));
                    float arm = smooth(Math.min((t - 0.22f) / 0.18f, (0.88f - t) / 0.13f));
                    target[0] = drink.headAngle * head;
                    target[3] = drink.armAngle * arm;
                }
            }
            case SIT -> {
                nextSeat = motion.atEdge ? 1 : 0;
                target[4] = step * 0.10f;
                target[5] = -target[4];
            }
            case SETTLE -> nextFold = smooth(brain.age / brain.duration);
            case LIE_DOWN -> {
                nextFold = 1;
                nextRecline = smooth(brain.age / brain.duration);
            }
            case SLEEP -> { nextFold = 1; nextRecline = 1; }
            case RISE -> {
                float t = brain.age / brain.duration;
                nextFold = 1 - smooth((t - 0.60f) / 0.40f);
                nextRecline = 1 - smooth(t / 0.60f);
            }
            case DANCE -> {
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
                nextCrouch = Math.abs(rhythm) * (brain.danceStyle == 1 ? 0.12f : 0.08f);
            }
            case WAVE -> {
                target[2] = 0.5f + step * 0.1f;
                target[0] = 0.04f;
            }
            case CELEBRATE, PANIC -> target[0] = step * 0.08f;
            case TURN -> nextTurn = (float) Math.sin(Math.PI * Math.min(1, brain.age / brain.duration));
            case STUMBLE -> nextFold = 0.9f * (float) Math.sin(Math.PI * Math.min(1, brain.age / brain.duration));
            case REST, THROW -> { }
        }
        if (motion.travel != PetMotion.Travel.GROUNDED) {
            nextFold = 0;
            nextRecline = 0;
            nextSeat = 0;
            target[2] = step * 0.09f;
            target[3] = -step * 0.07f;
            target[4] = step * 0.2f;
            target[5] = -target[4];
        }
        float blend = 1 - (float) Math.exp(-dt * 12);
        for (int i = 0; i < angles.length; i++) angles[i] += (target[i] - angles[i]) * blend;
        if (state == PetState.DRINK && brain.age / brain.duration >= 0.42f && brain.age / brain.duration <= 0.73f) {
            angles[0] = target[0];
            angles[3] = target[3];
        }
        fold += (nextFold - fold) * blend;
        recline += (nextRecline - recline) * blend;
        seated += (nextSeat - seated) * blend;
        crouch += (nextCrouch - crouch) * blend;
        wall += (motion.wallAngle() - wall) * blend;
        turn = nextTurn;
    }

    private static float smooth(float value) {
        float t = Math.max(0, Math.min(1, value));
        return t * t * (3 - 2 * t);
    }
}
