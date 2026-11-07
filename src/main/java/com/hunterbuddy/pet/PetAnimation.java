package com.hunterbuddy.pet;

public record PetAnimation(String name, float duration, int loops, float weight, boolean smoke, Key[] keys) {
    public static final String[] JOINTS = {"head", "torso", "left_arm", "right_arm", "left_leg", "right_leg",
        "left_leg_lift", "right_leg_lift", "lean", "crouch", "bob", "x"};
    public static final int LEFT_LIFT = 6;
    public static final int LEAN = 8;
    public static final int CROUCH = 9;
    public static final int BOB = 10;
    public static final int SHIFT = 11;
    static final float[] LOW = {-180, -180, -180, -180, -180, -180, 0, 0, -45, 0, -80, -40};
    static final float[] HIGH = {180, 180, 180, 180, 180, 180, 60, 60, 45, 0.5f, 40, 40};
    private static final float DEGREES = (float) Math.PI / 180;

    public record Key(float t, boolean smooth, float[] joints) {
    }

    static float toInternal(int joint, float value) {
        boolean angle = joint < LEFT_LIFT || joint == LEAN;
        return angle ? value * DEGREES : value;
    }

    public float length() {
        return duration * loops;
    }

    public void sample(float age, float[] out) {
        float u = age / duration;
        boolean wrap = loops > 1;
        u = wrap ? u - (float) Math.floor(u) : Math.max(0, Math.min(1, u));
        Key first = keys[0];
        Key last = keys[keys.length - 1];
        Key from;
        Key to;
        float start;
        float end;
        if (u < first.t) {
            if (!wrap) {
                System.arraycopy(first.joints, 0, out, 0, out.length);
                return;
            }
            from = last;
            to = first;
            start = last.t - 1;
            end = first.t;
        } else if (u >= last.t) {
            if (!wrap) {
                System.arraycopy(last.joints, 0, out, 0, out.length);
                return;
            }
            from = last;
            to = first;
            start = last.t;
            end = first.t + 1;
        } else {
            int i = 0;
            while (keys[i + 1].t <= u) i++;
            from = keys[i];
            to = keys[i + 1];
            start = from.t;
            end = to.t;
        }
        float f = end > start ? (u - start) / (end - start) : 1;
        if (from.smooth) f = f * f * (3 - 2 * f);
        for (int j = 0; j < out.length; j++) out[j] = from.joints[j] + (to.joints[j] - from.joints[j]) * f;
    }
}
