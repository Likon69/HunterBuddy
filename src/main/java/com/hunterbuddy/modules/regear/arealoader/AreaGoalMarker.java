package com.hunterbuddy.modules.regear.arealoader;

import com.hunterbuddy.modules.regear.util.MapUtil;
import com.hunterbuddy.modules.regear.util.XaeroWaypointManager;
import net.minecraft.util.math.BlockPos;

final class AreaGoalMarker {
    private static final String CATEGORY = "AreaLoader";
    private static final String NAME = "Goal";
    private static final String INITIALS = "→";

    private final AreaLoader area;
    private BlockPos current;

    AreaGoalMarker(AreaLoader area) {
        this.area = area;
    }

    void update(BlockPos target) {
        if (!area.showGoalWaypoint.get() || target == null) {
            clear();
            return;
        }
        if (isSameColumn(target)) return;
        clear();
        current = target;
        if (XaeroWaypointManager.isAvailable()) {
            XaeroWaypointManager.addWaypoint(CATEGORY, target, NAME, INITIALS, MapUtil.WpColor.Aqua, true);
        }
    }

    void clear() {
        if (current == null) return;
        XaeroWaypointManager.removeWaypointXZ(CATEGORY, current.getX(), current.getZ());
        current = null;
    }

    private boolean isSameColumn(BlockPos target) {
        return current != null && current.getX() == target.getX() && current.getZ() == target.getZ();
    }
}
