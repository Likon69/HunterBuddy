package com.hunterbuddy.pet;

import org.joml.Matrix3x2f;

public final class PetImageTransform {
    public final Matrix3x2f matrix = new Matrix3x2f();
    public float eyesX;
    public float eyesY;

    public void update(PetResources.FullPose pose, float sourceHeight, float size, float age, int direction, float opacity, float x, float y) {
        float scale = pose.scale() * size / sourceHeight;
        float breath = 1 + (float) Math.sin(age * 2) * 0.006f * opacity;
        float sway = pose.name().equals("moon") ? (float) Math.sin(age * 4) * 0.012f : 0;
        matrix.identity().translate(x, y).scale(direction * scale, scale * breath).rotate(sway).translate(-pose.feetX(), -pose.feetY());
        eyesX = matrix.m00() * pose.eyesX() + matrix.m10() * pose.eyesY() + matrix.m20();
        eyesY = matrix.m01() * pose.eyesX() + matrix.m11() * pose.eyesY() + matrix.m21();
    }
}
