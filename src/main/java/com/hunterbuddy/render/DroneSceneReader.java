package com.hunterbuddy.render;

public final class DroneSceneReader {
    private DroneSceneReader() {}

    public static DroneScene read(String stateName) {
        if (stateName == null) return DroneScene.CRUISE;

        return switch (stateName) {
            case "DIG", "SIDE_DIG", "TUNNEL" -> DroneScene.DIG;
            case "ROOM", "ROOM_CLIMB" -> DroneScene.ROOM;
            case "EXIT", "SHAFT_ALIGN", "SHAFT_FLY", "SWIM_UP", "ASCEND", "SELF_ASCEND", "OBSIDIAN_PLATFORM" -> DroneScene.EXIT;
            case "TAKEOFF_SPOT", "TAKEOFF", "WATER_JUMP", "CLIMB" -> DroneScene.TAKEOFF;
            default -> DroneScene.CRUISE;
        };
    }
}
