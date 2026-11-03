package com.hunterbuddy.modules.regear.arealoader;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;

final class RecoveryCoordinateRow {
    private static final String DEFAULT_TEXT = "0";

    private final WTextBox xBox;
    private final WTextBox zBox;

    RecoveryCoordinateRow(GuiTheme theme, WContainer parent, String label) {
        WHorizontalList row = parent.add(theme.horizontalList()).expandX().widget();
        row.add(theme.label(label + " X"));
        xBox = row.add(theme.textBox(DEFAULT_TEXT, RecoveryCoordinateRow::acceptsCharacter)).widget();
        row.add(theme.label("Z"));
        zBox = row.add(theme.textBox(DEFAULT_TEXT, RecoveryCoordinateRow::acceptsCharacter)).widget();
    }

    private static boolean acceptsCharacter(String text, char character) {
        return Character.isDigit(character) || (character == '-' && text.isEmpty());
    }

    RecoveryPoint point() {
        return new RecoveryPoint(parse(xBox.get()), parse(zBox.get()));
    }

    void set(RecoveryPoint point) {
        xBox.set(String.valueOf(point.x()));
        zBox.set(String.valueOf(point.z()));
    }

    private static int parse(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
