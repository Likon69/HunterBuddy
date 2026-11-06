package com.hunterbuddy.pet;

import java.util.Arrays;

public final class PetPose {
    public final float[] angles = new float[6];
    public final float[] previousAngles = new float[6];
    private final float[] target = new float[6];
    public float bob;
    public float lean;
    public float crouch;
    public float previousBob;
    public float previousLean;
    public float previousCrouch;

    public void reset() {
        Arrays.fill(angles, 0);
        Arrays.fill(previousAngles, 0);
        bob = 0;
        lean = 0;
        crouch = 0;
        previousBob = 0;
        previousLean = 0;
        previousCrouch = 0;
    }

    public void update(PetBrain brain, PetResources.Rig rig, float dt) {
        System.arraycopy(angles, 0, previousAngles, 0, angles.length);
        previousBob = bob;
        previousLean = lean;
        previousCrouch = crouch;
        Arrays.fill(target, 0);
        float phase = brain.age * (7 + brain.drunkenness * 2);
        float step = (float) Math.sin(phase);
        float breathing = (float) Math.sin(brain.age * 2);
        float nextBob = breathing * 0.8f;
        float nextCrouch = 0;
        switch (brain.state) {
            case WALK -> {
                target[4] = step * 0.4f;
                target[5] = -target[4];
                target[2] = -step * 0.23f;
                target[3] = -target[2];
                nextBob = -Math.abs(step) * 3;
            }
            case SMOKE -> {
                target[0] = 0.1f;
                target[2] = towardMouth(rig, 2, 0, target[0]) * envelope(brain.age / brain.duration);
            }
            case DRINK -> {
                float lift = envelope(brain.age / brain.duration);
                target[0] = -0.28f * lift;
                target[3] = towardMouth(rig, 3, 1, target[0]) * lift;
            }
            case THROW -> target[3] = brain.age < brain.duration * 0.55f ? 1.1f : -1.65f;
            case PANIC -> {
                target[2] = 2.5f + step * 0.15f;
                target[3] = -2.5f - step * 0.15f;
                nextBob = step * 2;
            }
            case HIDE -> {
                if (brain.age < 2) {
                    target[4] = step * 0.6f;
                    target[5] = -target[4];
                } else {
                    target[4] = 0.7f;
                    target[5] = -0.7f;
                    target[0] = 0.25f * breathing;
                    nextCrouch = 0.3f;
                }
            }
            case SLEEP -> { target[0] = 0.65f; nextCrouch = 0.12f; }
            case CELEBRATE -> {
                target[2] = 2.7f;
                target[3] = -2.7f;
                nextBob = -Math.abs(step) * 14;
            }
            case REST -> target[1] = breathing * 0.015f;
        }
        float blend = 1 - (float) Math.exp(-dt * 9);
        for (int i = 0; i < angles.length; i++) angles[i] += (target[i] - angles[i]) * blend;
        bob += (nextBob - bob) * blend;
        crouch += (nextCrouch - crouch) * blend;
        float nextLean = (float) Math.sin(brain.age * 2.7) * brain.drunkenness * 0.16f;
        lean += (nextLean - lean) * blend;
    }

    private static float envelope(float t) {
        float value = Math.min(1, Math.min(t * 4, (1 - t) * 4));
        return value * value * (3 - 2 * value);
    }

    private static float towardMouth(PetResources.Rig rig, int owner, int anchor, float headAngle) {
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
}
