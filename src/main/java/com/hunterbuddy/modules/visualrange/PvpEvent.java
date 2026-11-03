package com.hunterbuddy.modules.visualrange;

public enum PvpEvent {
    CRYSTAL_PLACED("crystal placed", "§d", "placed", "an", "End Crystal", "End Crystal Placed"),
    CRYSTAL_POPPED("crystal popped", "§c", "popped", "an", "End Crystal", "End Crystal Popped"),
    ANCHOR_PLACED("anchor placed", "§d", "placed", "a", "Respawn Anchor", "Respawn Anchor Placed"),
    ANCHOR_POPPED("anchor popped", "§c", "popped", "a", "Respawn Anchor", "Respawn Anchor Exploded");

    private final String feedLabel;
    private final String color;
    private final String verb;
    private final String article;
    private final String object;
    private final String webhookTitle;

    PvpEvent(String feedLabel, String color, String verb, String article, String object, String webhookTitle) {
        this.feedLabel = feedLabel;
        this.color = color;
        this.verb = verb;
        this.article = article;
        this.object = object;
        this.webhookTitle = webhookTitle;
    }

    public String feedLabel() {
        return feedLabel;
    }

    public String webhookTitle() {
        return webhookTitle;
    }

    public String chatText(String who, String coordinates) {
        return color + who + " §7" + verb + " " + article + " " + color + object + " §7at §f" + coordinates;
    }

    public String webhookText(String who, String coordinates, String dimension) {
        return "**" + who + "** " + verb + " " + article + " " + object
            + "\nCoords: " + coordinates + "\nDimension: " + dimension;
    }
}
