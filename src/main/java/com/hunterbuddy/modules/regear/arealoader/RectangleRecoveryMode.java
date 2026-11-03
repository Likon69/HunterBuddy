package com.hunterbuddy.modules.regear.arealoader;

import com.hunterbuddy.modules.regear.arealoader.modes.Rectangle;
import com.hunterbuddy.modules.regear.arealoader.modes.RectangleYaw;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

final class RectangleRecoveryMode implements RecoveryMode {
    private static final String MODE_FILE = "Rectangle";

    private final AreaLoader area;
    private final RecoveryForm form;
    private RecoveryCoordinateRow end;

    RectangleRecoveryMode(AreaLoader area, RecoveryForm form) {
        this.area = area;
        this.form = form;
    }

    @Override
    public void buildSection(GuiTheme theme, WContainer section) {
        section.add(theme.label("Origin is the corner where the rectangle started."));
        section.add(theme.label("End is the opposite corner."));
        section.add(theme.label("Resume is your current position."));
        end = new RecoveryCoordinateRow(theme, section, "End");
    }

    private RectangleRecoveryPlan plan() {
        return new RectangleRecoveryPlan(form.origin(), end.point(), form.resume(), form.blockGap());
    }

    @Override
    public void validate() {
        RectangleRecoveryPlan plan = plan();
        if (!plan.isWithinExpandedBounds()) {
            ChatUtils.error("The position is outside the rectangle, X %d to %d, Z %d to %d", plan.minX(), plan.maxX(),
                plan.minZ(), plan.maxZ());
            return;
        }
        ChatUtils.info("The position is valid");
        reportRow(plan);
    }

    private void reportRow(RectangleRecoveryPlan plan) {
        int row = plan.floorRowIndex();
        String heading = plan.rowDirection(row) == RectangleRecoveryPlan.RowDirection.EAST ? "east (-90)" : "west (90)";
        ChatUtils.info("Row %d at Z %d heading %s, progress %d blocks", row, plan.rowZ(row), heading, plan.zProgress());
    }

    @Override
    public void snapToPath() {
        RectangleRecoveryPlan plan = plan();
        RecoveryPoint snapped = new RecoveryPoint(plan.resumeXWithinBounds(), plan.rowZ(plan.nearestRowIndex()));
        form.setResume(snapped);
        ChatUtils.info("Resume position aligned to %s", area.coords(snapped.x(), snapped.z()));
    }

    @Override
    public boolean apply() {
        RectangleRecoveryPlan plan = plan();
        int row = plan.nearestRowIndex();
        Rectangle.PathingDataRectangle data = describeProgress(plan, row);
        if (!RecoverySaveWriter.write(area, MODE_FILE, data)) return false;
        area.startPos.set(form.origin().atPathLevel());
        area.targetPos.set(end.point().atPathLevel());
        return true;
    }

    private Rectangle.PathingDataRectangle describeProgress(RectangleRecoveryPlan plan, int row) {
        RectangleRecoveryPlan.RowDirection direction = plan.rowDirection(row);
        boolean atRowEnd = plan.isAtRowEnd(direction);
        float yaw = atRowEnd ? sideYaw(plan) : rowYaw(direction);
        return new Rectangle.PathingDataRectangle(form.origin().atPathLevel(), end.point().atPathLevel(),
            form.resume().atPathLevel(), yaw, !atRowEnd, plan.rowZ(row));
    }

    private static float sideYaw(RectangleRecoveryPlan plan) {
        return plan.goingPositiveZ() ? RectangleYaw.SOUTH : RectangleYaw.NORTH;
    }

    private static float rowYaw(RectangleRecoveryPlan.RowDirection direction) {
        return direction == RectangleRecoveryPlan.RowDirection.EAST ? RectangleYaw.EAST : RectangleYaw.WEST;
    }
}
