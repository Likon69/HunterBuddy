package com.hunterbuddy.modules.mapwaypoint;

import com.hunterbuddy.modules.WaypointFollower;
import xaero.hud.minimap.waypoint.WaypointColor;

public enum WaypointKind {
    HUNT("Add Hunt Waypoint", "HW", WaypointColor.AQUA),
    ROUTE_MARK("Add Route Mark", "RT", WaypointColor.YELLOW);

    private final String menuLabel;
    private final String initials;
    private final WaypointColor color;

    WaypointKind(String menuLabel, String initials, WaypointColor color) {
        this.menuLabel = menuLabel;
        this.initials = initials;
        this.color = color;
    }

    public String menuLabel() {
        return menuLabel;
    }

    public String initials() {
        return initials;
    }

    public WaypointColor color() {
        return color;
    }

    public boolean isRouteMark() {
        return this == ROUTE_MARK;
    }

    public String nameFor(WaypointFollower follower) {
        if (isRouteMark()) return follower.getRoutePrefix() + follower.getNextRouteNumber();
        return follower.getWaypointPrefix() + follower.getWaypointCount();
    }
}
