package com.hunterbuddy.pet;

import java.util.function.BooleanSupplier;

public final class PetLoot {
    private static final int WINDOW_TICKS = 100;
    private Object screen;
    private int ticks;
    private boolean found;

    public void reset() {
        screen = null;
        ticks = 0;
        found = false;
    }

    public boolean update(Object current, BooleanSupplier valuables) {
        boolean closed = current != screen && found;
        if (current != screen) {
            screen = current;
            ticks = 0;
            found = false;
        }
        if (current != null && !found && ticks < WINDOW_TICKS) {
            ticks++;
            found = valuables.getAsBoolean();
        }
        return closed;
    }

    public static boolean valuable(String itemPath) {
        return itemPath.endsWith("shulker_box") || itemPath.contains("netherite") || itemPath.equals("elytra")
            || itemPath.equals("enchanted_golden_apple") || itemPath.equals("totem_of_undying");
    }
}
