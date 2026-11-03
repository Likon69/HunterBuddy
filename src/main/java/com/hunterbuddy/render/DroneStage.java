package com.hunterbuddy.render;

import java.util.List;
import net.minecraft.util.math.Vec3d;

public record DroneStage(DroneScene scene, List<Vec3d> roomCorners, Vec3d interest, boolean combat, boolean climbing) {
    public static final DroneStage CRUISE = new DroneStage(DroneScene.CRUISE, List.of(), null, false, false);
}
