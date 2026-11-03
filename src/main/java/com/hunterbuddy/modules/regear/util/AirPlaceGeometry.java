package com.hunterbuddy.modules.regear.util;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

final class AirPlaceGeometry {
    private static final int AXES = 3;

    private AirPlaceGeometry() {
    }

    static BlockHitResult nearestFaceHit(BlockPos pos, Vec3d eye, double reach) {
        double reachSquared = reach * reach;
        BlockHitResult best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Direction face : Direction.values()) {
            Vec3d point = pointOnFace(pos, face, eye);
            if (point == null) continue;
            double distance = point.squaredDistanceTo(eye);
            if (distance <= reachSquared && distance < bestDistance) {
                bestDistance = distance;
                best = new BlockHitResult(point, face, pos, true);
            }
        }
        return best;
    }

    private static Vec3d pointOnFace(BlockPos pos, Direction face, Vec3d eye) {
        double[] low = {pos.getX(), pos.getY(), pos.getZ()};
        double[] eyeAxes = {eye.x, eye.y, eye.z};
        int faceAxis = face.getAxis().ordinal();
        boolean positive = face.getDirection() == Direction.AxisDirection.POSITIVE;
        double plane = low[faceAxis] + (positive ? 1.0 : 0.0);
        if (!isOutside(eyeAxes[faceAxis], plane, positive)) return null;
        double[] point = new double[AXES];
        for (int axis = 0; axis < AXES; axis++) {
            point[axis] = axis == faceAxis ? plane : MathHelper.clamp(eyeAxes[axis], low[axis], low[axis] + 1.0);
        }
        return new Vec3d(point[0], point[1], point[2]);
    }

    private static boolean isOutside(double eyeCoordinate, double plane, boolean positiveFace) {
        return positiveFace ? eyeCoordinate > plane : eyeCoordinate < plane;
    }
}
