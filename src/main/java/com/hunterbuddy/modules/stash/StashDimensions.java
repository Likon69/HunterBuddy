package com.hunterbuddy.modules.stash;

import meteordevelopment.meteorclient.MeteorClient;

public final class StashDimensions {
    public static final String OVERWORLD = "overworld";
    public static final String NETHER = "the_nether";
    public static final String END = "the_end";
    private static final String UNKNOWN = "unknown";

    private StashDimensions() {
    }

    public static String current() {
        if (MeteorClient.mc.world == null) return UNKNOWN;
        return MeteorClient.mc.world.getRegistryKey().getValue().getPath();
    }
}
