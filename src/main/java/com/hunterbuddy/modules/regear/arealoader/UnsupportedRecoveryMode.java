package com.hunterbuddy.modules.regear.arealoader;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

final class UnsupportedRecoveryMode implements RecoveryMode {
    private static final String MESSAGE = "Circle mode cannot resume from coordinates";

    @Override
    public void buildSection(GuiTheme theme, WContainer section) {
    }

    @Override
    public void validate() {
        ChatUtils.error(MESSAGE);
    }

    @Override
    public void snapToPath() {
        ChatUtils.error(MESSAGE);
    }

    @Override
    public boolean apply() {
        ChatUtils.error(MESSAGE);
        return false;
    }
}
