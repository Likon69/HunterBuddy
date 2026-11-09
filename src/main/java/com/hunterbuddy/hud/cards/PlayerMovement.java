package com.hunterbuddy.hud.cards;

public enum PlayerMovement {
    APPROACH("approach"),
    HOLD("hold"),
    RECEDE("recede"),
    GONE("left");

    private final String label;

    PlayerMovement(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
