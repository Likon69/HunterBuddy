package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;

final class HbWidthLayout {
    private HbWidthLayout() {}

    static void fit(WWidget widget, double available) {
        widget.width = Math.max(0, Math.min(widget.width, available));
        widget.calculateWidgetPositions();
        if (!(widget instanceof WContainer container) || widget instanceof HbNumericSetting
            || widget instanceof HbSettingTable || widget instanceof HbHorizontalViewport) return;
        if (container instanceof WHorizontalList list) {
            fitRow(list);
            return;
        }
        for (Cell<?> cell : container.cells) {
            WWidget child = cell.widget();
            double availableWidth = Math.max(0, widget.width - cell.padLeft() - cell.padRight());
            child.width = Math.min(child.width, availableWidth);
            child.x = Math.max(widget.x + cell.padLeft(), Math.min(child.x, widget.x + widget.width - cell.padRight() - child.width));
            fit(child, availableWidth);
        }
    }

    private static void fitRow(WHorizontalList list) {
        double spacing = list.theme.scale(list.spacing);
        double fixed = Math.max(0, list.cells.size() - 1) * spacing;
        double natural = 0;
        for (Cell<?> cell : list.cells) {
            fixed += cell.padLeft() + cell.padRight();
            natural += cell.widget().width;
        }
        double available = Math.max(0, list.width - fixed);
        double ratio = natural == 0 ? 1 : Math.min(1, available / natural);
        double left = list.x;
        for (Cell<?> cell : list.cells) {
            WWidget child = cell.widget();
            double width = child.width * ratio;
            if (child instanceof WLabel) width = Math.min(width, available * 0.45);
            cell.x = left + cell.padLeft();
            cell.y = list.y + cell.padTop();
            cell.width = width;
            cell.height = Math.max(0, list.height - cell.padTop() - cell.padBottom());
            child.width = width;
            cell.alignWidget();
            fit(child, width);
            left = cell.x + width + cell.padRight() + spacing;
        }
    }
}
