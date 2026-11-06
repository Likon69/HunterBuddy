package com.hunterbuddy.render;

import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class SkyboxState {
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f INV_VIEW_PROJ = new Matrix4f();
    private static final Vector4f FOG = new Vector4f();

    private SkyboxState() {}

    public static void capture(Matrix4f positionMatrix, Matrix4f projectionMatrix, Vector4f fogColor) {
        VIEW.set(positionMatrix).m30(0.0f).m31(0.0f).m32(0.0f);
        INV_VIEW_PROJ.set(projectionMatrix).mul(VIEW).invert();
        FOG.set(fogColor);
    }

    public static Matrix4f inverseViewProjection() {
        return INV_VIEW_PROJ;
    }

    public static Vector4f fog() {
        return FOG;
    }
}
