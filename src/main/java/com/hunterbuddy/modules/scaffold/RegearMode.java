package com.hunterbuddy.modules.scaffold;

public final class RegearMode {
    private static final int ANY_SLOT = -1;
    private static final int FIRST_HOTBAR_SLOT = 0;
    private static final int LAST_HOTBAR_SLOT = 8;

    private boolean enabled;
    private int hotbarSlot = ANY_SLOT;

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) hotbarSlot = ANY_SLOT;
    }

    public void setHotbarSlot(int slot) {
        hotbarSlot = slot;
    }

    public boolean hasFixedSlot() {
        return hotbarSlot >= FIRST_HOTBAR_SLOT && hotbarSlot <= LAST_HOTBAR_SLOT;
    }

    public int hotbarSlot() {
        return hotbarSlot;
    }

    public void reset() {
        setEnabled(false);
    }
}
