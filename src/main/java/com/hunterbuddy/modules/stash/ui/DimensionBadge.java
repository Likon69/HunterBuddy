package com.hunterbuddy.modules.stash.ui;

import com.hunterbuddy.modules.stash.StashDimensions;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class DimensionBadge {
    private static final Color OVERWORLD_COLOR = new Color(85, 255, 85);
    private static final Color NETHER_COLOR = new Color(255, 85, 85);
    private static final Color END_COLOR = new Color(255, 85, 255);
    private static final Color UNKNOWN_COLOR = new Color(170, 170, 170);

    private DimensionBadge() {
    }

    public static String shortLabel(String dimension) {
        return switch (dimension) {
            case StashDimensions.OVERWORLD -> "OW";
            case StashDimensions.NETHER -> "Neth";
            case StashDimensions.END -> "End";
            default -> "?";
        };
    }

    public static Color color(String dimension) {
        return switch (dimension) {
            case StashDimensions.OVERWORLD -> OVERWORLD_COLOR;
            case StashDimensions.NETHER -> NETHER_COLOR;
            case StashDimensions.END -> END_COLOR;
            default -> UNKNOWN_COLOR;
        };
    }
}
