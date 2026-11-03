package com.hunterbuddy.modules.mapwaypoint;

import net.minecraft.client.gui.screen.Screen;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

public final class MapWaypointOption extends RightClickOption {
    private final Runnable action;

    public MapWaypointOption(String label, int index, IRightClickableElement owner, Runnable action) {
        super(label, index, owner);
        this.action = action;
    }

    @Override
    public void onAction(Screen screen) {
        action.run();
    }
}
