package com.hunterbuddy.pet;

import org.joml.Matrix3x2f;

public final class PetSkeleton {
    private static final int[] OWNERS = {2, 3, 0, 0, 4};
    public final Matrix3x2f[] matrices = new Matrix3x2f[6];
    private final Matrix3x2f root = new Matrix3x2f();
    private final Matrix3x2f body = new Matrix3x2f();
    public float anchorX;
    public float anchorY;

    public PetSkeleton() {
        for (int i = 0; i < matrices.length; i++) matrices[i] = new Matrix3x2f();
    }

    public void update(PetResources.Rig rig, PetMotion motion, PetPose pose, float size, float delta) {
        float hipX = rig.anchors()[8];
        float hipY = rig.height() * 0.68f;
        for (PetResources.Part part : rig.parts()) {
            if (part.index() == 1) { hipX = part.pivotX(); hipY = part.pivotY(); }
        }
        float fold = mix(pose.previousFold, pose.fold, delta);
        float seated = mix(pose.previousSeated, pose.seated, delta);
        float legAngle = -fold * (float) Math.PI * 0.5f;
        float drop = (rig.anchors()[9] - hipY) * (seated + (1 - seated) * (1 - (float) Math.cos(legAngle)));
        float bodyAngle = mix(pose.previousAngles[1], pose.angles[1], delta);
        body.identity().translate(hipX, hipY + drop).rotate(bodyAngle).translate(-hipX, -hipY);
        for (PetResources.Part part : rig.parts()) {
            int index = part.index();
            float angle = mix(pose.previousAngles[index], pose.angles[index], delta);
            Matrix3x2f matrix = matrices[index];
            if (index >= 4) {
                matrix.identity().translate(0, drop);
                angle += legAngle;
            } else {
                matrix.set(body);
                if (index == 1) angle = 0;
            }
            matrix.translate(part.pivotX(), part.pivotY()).rotate(angle).translate(-part.pivotX(), -part.pivotY());
            if (index >= 4 && seated < 0.01f && motion.travel == PetMotion.Travel.GROUNDED) {
                matrix.m21 += rig.anchors()[9] - bottom(part, matrix);
            }
            if (index >= 4) matrix.m21 -= mix(pose.previousLegLift[index - 4], pose.legLift[index - 4], delta);
        }
        float lowest = -Float.MAX_VALUE;
        for (PetResources.Part part : rig.parts()) {
            if (part.index() == 0 || part.index() == 1) lowest = Math.max(lowest, bottom(part, matrices[part.index()]));
        }
        float correction = Math.max(0, lowest - rig.anchors()[9]);
        for (int i = 0; i < 4; i++) matrices[i].m21 -= correction;
        if (fold > 0.01f) {
            for (PetResources.Part part : rig.parts()) {
                if (part.index() == 2 || part.index() == 3) {
                    Matrix3x2f matrix = matrices[part.index()];
                    matrix.m21 -= Math.max(0, bottom(part, matrix) - rig.anchors()[9]);
                }
            }
        }
        float crouch = mix(pose.previousCrouch, pose.crouch, delta);
        float turn = mix(pose.previousTurn, pose.turn, delta);
        float scale = size / rig.height();
        root.identity().translate(mix(motion.previousX, motion.x, delta), mix(motion.previousY, motion.y, delta))
            .translate(mix(pose.previousShift, pose.shift, delta) * scale, mix(pose.previousBob, pose.bob, delta) * scale)
            .scale(motion.direction * scale * (1 + crouch * 0.2f) * (1 - turn * 0.72f), scale * (1 - crouch * 0.5f))
            .rotate(mix(pose.previousLean, pose.lean, delta))
            .translate(-rig.anchors()[8], -rig.anchors()[9]);
        for (Matrix3x2f matrix : matrices) root.mul(matrix, matrix);
    }

    public void anchor(PetResources.Rig rig, int anchor) {
        point(OWNERS[anchor], rig.anchors()[anchor * 2], rig.anchors()[anchor * 2 + 1]);
    }

    public void point(int owner, float x, float y) {
        Matrix3x2f matrix = matrices[owner];
        anchorX = matrix.m00() * x + matrix.m10() * y + matrix.m20();
        anchorY = matrix.m01() * x + matrix.m11() * y + matrix.m21();
    }

    private static float bottom(PetResources.Part part, Matrix3x2f matrix) {
        float maximum = -Float.MAX_VALUE;
        float[] outline = part.outline();
        for (int i = 0; i < outline.length; i += 2) {
            float x = part.x() + outline[i];
            float y = part.y() + outline[i + 1];
            maximum = Math.max(maximum, matrix.m01() * x + matrix.m11() * y + matrix.m21());
        }
        return maximum;
    }

    private static float mix(float from, float to, float delta) {
        return from + (to - from) * delta;
    }
}
