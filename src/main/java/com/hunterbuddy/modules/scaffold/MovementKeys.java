package com.hunterbuddy.modules.scaffold;

import com.hunterbuddy.modules.regear.util.Utils;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.math.Vec3d;

final class MovementKeys {
    private static final double QUARTER_TURN = 90.0;
    private static final double HALF_TURN = 180.0;

    private MovementKeys() {
    }

    static boolean anyPressed(GameOptions options) {
        return options.forwardKey.isPressed() || options.backKey.isPressed()
            || options.leftKey.isPressed() || options.rightKey.isPressed();
    }

    static Vec3d direction(GameOptions options, double yaw) {
        Vec3d sum = Vec3d.ZERO;
        if (options.forwardKey.isPressed()) sum = sum.add(Utils.yawToDirection(yaw));
        if (options.backKey.isPressed()) sum = sum.add(Utils.yawToDirection(yaw + HALF_TURN));
        if (options.leftKey.isPressed()) sum = sum.add(Utils.yawToDirection(yaw - QUARTER_TURN));
        if (options.rightKey.isPressed()) sum = sum.add(Utils.yawToDirection(yaw + QUARTER_TURN));
        return sum.normalize();
    }
}
