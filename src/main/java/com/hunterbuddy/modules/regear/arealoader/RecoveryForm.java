package com.hunterbuddy.modules.regear.arealoader;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.MinecraftClient;

final class RecoveryForm {
    private static final int CHUNK_SIZE = 16;
    private static final int MIN_GAP_CHUNKS = 1;
    private static final int MAX_GAP_CHUNKS = 100;

    private final RecoveryCoordinateRow origin;
    private final RecoveryCoordinateRow resume;
    private final WIntEdit gapChunks;

    RecoveryForm(GuiTheme theme, WContainer parent, AreaLoader area) {
        WSection section = parent.add(theme.section("Position setup", true)).expandX().widget();
        section.add(theme.label("Enter where the run started and where you want to continue."));
        origin = new RecoveryCoordinateRow(theme, section, "Origin");
        WHorizontalList gapRow = section.add(theme.horizontalList()).expandX().widget();
        gapRow.add(theme.label("Gap between passes (chunks)"));
        gapChunks = gapRow.add(theme.intEdit(area.rowGap.get(), MIN_GAP_CHUNKS, MAX_GAP_CHUNKS, true)).widget();
        resume = new RecoveryCoordinateRow(theme, section, "Resume");
        WButton useHere = section.add(theme.button("Use my current position")).widget();
        useHere.action = this::useCurrentPosition;
    }

    RecoveryPoint origin() {
        return origin.point();
    }

    RecoveryPoint resume() {
        return resume.point();
    }

    void setResume(RecoveryPoint point) {
        resume.set(point);
    }

    int gapChunks() {
        return gapChunks.get();
    }

    int blockGap() {
        return CHUNK_SIZE * gapChunks.get();
    }

    private void useCurrentPosition() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        resume.set(new RecoveryPoint(mc.player.getBlockX(), mc.player.getBlockZ()));
        ChatUtils.info("Resume position set to your current position");
    }
}
