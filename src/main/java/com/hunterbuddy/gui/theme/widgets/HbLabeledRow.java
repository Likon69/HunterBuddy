package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;

public final class HbLabeledRow extends WVerticalList implements HbWidget {
    private final String text;
    private HbMultiLabel label;
    private WHorizontalList controls;

    public HbLabeledRow(String text) {
        this.text = text;
    }

    @Override
    public void init() {
        spacing = theme().layoutUnits(3);
        label = add(new HbMultiLabel(text, false, 202)).expandX().widget();
        controls = add(theme.horizontalList()).expandX().widget();
    }

    public WHorizontalList controls() { return controls; }

    public boolean rewrap(double width) { return label.rewrap(width); }
}
