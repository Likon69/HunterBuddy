package com.hunterbuddy.modules.regear.arealoader;

import com.hunterbuddy.modules.regear.arealoader.modes.Spiral;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

final class SpiralRecoveryMode implements RecoveryMode {
    private final AreaLoader area;
    private final RecoveryForm form;

    SpiralRecoveryMode(AreaLoader area, RecoveryForm form) {
        this.area = area;
        this.form = form;
    }

    @Override
    public void buildSection(GuiTheme theme, WContainer section) {
        section.add(theme.label("Origin is where the spiral started, at its first corner."));
        section.add(theme.label("Resume is your current position or where the run should continue."));
        section.add(theme.label("The gap must be the one used by the original run."));
        section.add(theme.label("Quick procedure: fill origin and resume with the right gap, press Go to next corner,"));
        section.add(theme.label("then Apply from corner, fly to that corner and enable the module."));
        section.add(theme.label("Small offsets from the path are normal."));
    }

    @Override
    public void validate() {
        boolean onPath = spiral().validateManualCoordinates(origin().x(), origin().z(), resume().x(), resume().z(), form.blockGap(), false);
        ChatUtils.info(onPath ? "The position is on the spiral path" : "The position is not on the spiral path");
    }

    @Override
    public void snapToPath() {
        int[] snapped = spiral().snapToNearestCorner(origin().x(), origin().z(), resume().x(), resume().z(), form.blockGap());
        if (snapped == null) return;
        form.setResume(new RecoveryPoint(snapped[0], snapped[1]));
        ChatUtils.info("Resume position aligned to %s", area.coords(snapped[0], snapped[1]));
    }

    @Override
    public void goToNextCorner() {
        int[] corner = spiral().snapToNextCorner(origin().x(), origin().z(), resume().x(), resume().z(), form.blockGap());
        if (corner == null) return;
        form.setResume(new RecoveryPoint(corner[0], corner[1]));
        ChatUtils.info("Next corner is at %s", area.coords(corner[0], corner[1]));
    }

    @Override
    public boolean applyFromCorner() {
        return spiral().applyFromNextCorner(origin().x(), origin().z(), resume().x(), resume().z(), form.blockGap());
    }

    @Override
    public boolean apply() {
        return spiral().validateManualCoordinates(origin().x(), origin().z(), resume().x(), resume().z(), form.blockGap(), true);
    }

    private RecoveryPoint origin() {
        return form.origin();
    }

    private RecoveryPoint resume() {
        return form.resume();
    }

    private Spiral spiral() {
        return new Spiral();
    }
}
