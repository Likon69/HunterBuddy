/*
 * Port of com.lambda.config.blocks.ActionConfig.SortMode (lambda 1.21.11)
 */
package com.hunterbuddy.lambda;

public enum SortMode {
    Closest("Closest", "Breaks blocks closest to the player eye position"),
    Farthest("Farthest", "Breaks blocks farthest from the player eye position"),
    Tool("Tool", "Breaks blocks with priority given to those with tools matching the current selected"),
    Rotation("Rotation", "Breaks blocks closest to the player rotation"),
    Random("Random", "Breaks blocks in a random order");

    private final String displayName;
    private final String description;

    SortMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}
