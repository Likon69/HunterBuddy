package com.hunterbuddy.behavior.resource;

public record SlotIntent(int slot, boolean restore) {
    public SlotIntent {
        if (slot < 0 || slot > 8) throw new IllegalArgumentException("slot must be in the hotbar");
    }
}
