package com.hunterbuddy.modules.visualrange;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.world.World;

public final class DimensionNames {
    private static final String UNKNOWN = "Unknown";

    private DimensionNames() {
    }

    public static String current() {
        World world = MeteorClient.mc.world;
        if (world == null) return UNKNOWN;
        String path = world.getRegistryKey().getValue().getPath();
        return switch (path) {
            case "overworld" -> "Overworld";
            case "the_nether" -> "Nether";
            case "the_end" -> "The End";
            default -> path;
        };
    }
}
