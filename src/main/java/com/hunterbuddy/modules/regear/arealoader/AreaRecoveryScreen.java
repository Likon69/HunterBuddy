package com.hunterbuddy.modules.regear.arealoader;

import java.util.EnumMap;
import java.util.Map;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

public class AreaRecoveryScreen extends WindowScreen {
    private final AreaLoader area;
    private final Map<AreaLoaderModes, RecoveryMode> modes = new EnumMap<>(AreaLoaderModes.class);
    private final Map<AreaLoaderModes, WVerticalList> sections = new EnumMap<>(AreaLoaderModes.class);
    private AreaLoaderModes selected;
    private RecoveryForm form;

    public AreaRecoveryScreen(GuiTheme theme, AreaLoader area) {
        super(theme, "Area recovery setup");
        this.area = area;
        this.selected = area.chunkLoadMode.get();
    }

    @Override
    public void initWidgets() {
        WVerticalList root = add(theme.verticalList()).expandX().widget();
        addModeRow(root);
        form = new RecoveryForm(theme, root, area);
        createModes();
        buildSections(root);
        addActions(root);
        showSelectedSection();
    }

    private void addModeRow(WVerticalList root) {
        WHorizontalList row = root.add(theme.horizontalList()).expandX().widget();
        row.add(theme.label("Mode"));
        WDropdown<AreaLoaderModes> dropdown = row.add(theme.dropdown(selected)).widget();
        dropdown.action = () -> {
            selected = dropdown.get();
            showSelectedSection();
        };
    }

    private void createModes() {
        modes.put(AreaLoaderModes.Spiral, new SpiralRecoveryMode(area, form));
        modes.put(AreaLoaderModes.Rectangle, new RectangleRecoveryMode(area, form));
        modes.put(AreaLoaderModes.ZigZag, new ZigZagRecoveryMode(area, form));
        modes.put(AreaLoaderModes.Circle, new UnsupportedRecoveryMode());
    }

    private void buildSections(WVerticalList root) {
        for (Map.Entry<AreaLoaderModes, RecoveryMode> entry : modes.entrySet()) {
            WVerticalList section = root.add(theme.verticalList()).expandX().widget();
            entry.getValue().buildSection(theme, section);
            sections.put(entry.getKey(), section);
        }
    }

    private void showSelectedSection() {
        sections.forEach((mode, section) -> section.visible = mode == selected);
        invalidate();
    }

    private void addActions(WVerticalList root) {
        WSection actions = root.add(theme.section("Actions", true)).expandX().widget();
        actions.add(theme.label("Validate checks the position against the pattern, Snap moves it onto the path."));
        actions.add(theme.label("Apply and save writes the save file so the module resumes from there."));
        addButtonRow(actions, new ButtonSpec("Validate", () -> current().validate()),
            new ButtonSpec("Snap to path", () -> current().snapToPath()));
        addButtonRow(actions, new ButtonSpec("Go to next corner", () -> current().goToNextCorner()),
            new ButtonSpec("Apply from corner", this::applyFromCorner));
        addButtonRow(actions, new ButtonSpec("Apply and save", this::applyAndSave), new ButtonSpec("Cancel", this::close));
    }

    private record ButtonSpec(String label, Runnable action) {
    }

    private void addButtonRow(WSection actions, ButtonSpec first, ButtonSpec second) {
        WHorizontalList row = actions.add(theme.horizontalList()).expandX().widget();
        for (ButtonSpec spec : new ButtonSpec[]{first, second}) {
            row.add(theme.button(spec.label())).widget().action = spec.action();
        }
    }

    private RecoveryMode current() {
        return modes.get(selected);
    }

    private void applyFromCorner() {
        if (!current().applyFromCorner()) return;
        area.rowGap.set(form.gapChunks());
        ChatUtils.info("Recovery saved from the next corner, fly there and enable the module");
        close();
    }

    private void applyAndSave() {
        if (!current().apply()) return;
        area.rowGap.set(form.gapChunks());
        if (area.chunkLoadMode.get() != selected) {
            area.chunkLoadMode.set(selected);
        }
        ChatUtils.info("Recovery saved, enable the module to resume");
        close();
    }

    public enum CardinalDirection {
        NORTH(180),
        SOUTH(0),
        EAST(270),
        WEST(90);

        public final float yaw;

        CardinalDirection(float yaw) {
            this.yaw = yaw;
        }
    }
}
