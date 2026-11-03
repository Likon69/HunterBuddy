package com.hunterbuddy.modules.regear.arealoader;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

interface RecoveryMode {
    void buildSection(GuiTheme theme, WContainer section);

    void validate();

    void snapToPath();

    boolean apply();

    default void goToNextCorner() {
        ChatUtils.info("Going to the next corner exists only in spiral mode");
    }

    default boolean applyFromCorner() {
        ChatUtils.info("Applying from the next corner exists only in spiral mode");
        return false;
    }
}
