package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;

import java.util.List;

public class HbSettingTable extends WTable implements HbWidget {
    private static final double LABEL_MIN_SHARE = 0.35;

    public HbSettingTable() {
        horizontalSpacing = 8;
        verticalSpacing = 0;
    }

    private double rowHeight(List<Cell<?>> row) {
        double height = theme().hbScale(22);
        for (int i = 0; i < row.size(); i++) height = Math.max(height, row.get(i).widget().height);
        return height;
    }

    private double leftInset() {
        return theme().hbScale(style().settingInset() - 12 + (style().hasCounter() ? 6 : 0));
    }

    private double rightInset() {
        return theme().hbScale(style().hasCounter() ? 10 : style().inverted() ? 4 : 2);
    }

    @Override
    protected void onCalculateSize() {
        double spacing = horizontalSpacing;
        horizontalSpacing = theme().layoutUnits(spacing);
        try {
            super.onCalculateSize();
        } finally {
            horizontalSpacing = spacing;
        }
        width += leftInset() + rightInset();
        height = 0;
        for (int index = 0; getRow(index) != null; index++) height += rowHeight(getRow(index));
    }

    @Override
    protected void onCalculateWidgetPositions() {
        double top = y;
        for (int index = 0; getRow(index) != null; index++) {
            List<Cell<?>> row = getRow(index);
            double height = rowHeight(row);
            double right = x + width - rightInset();
            double available = Math.max(0, width - leftInset() - rightInset());
            double gap = theme().hbScale(8);
            double fixed = gap * Math.max(0, row.size() - 1);
            for (int column = 2; column < row.size(); column++) fixed += row.get(column).widget().width;
            double controlNatural = row.size() > 1 ? row.get(1).widget().width : 0;
            double labelWidth = Math.min(row.getFirst().widget().width, Math.max(available * LABEL_MIN_SHARE, available - fixed - controlNatural));
            labelWidth = Math.max(0, Math.min(labelWidth, available - fixed));
            double controlWidth = Math.max(0, available - labelWidth - fixed);
            for (int column = row.size() - 1; column >= 0; column--) {
                Cell<?> cell = row.get(column);
                if (column == 0) cell.widget().width = labelWidth;
                else if (column == 1) cell.widget().width = Math.min(cell.widget().width, controlWidth);
                cell.x = column == 0 ? x + leftInset() : right - cell.widget().width;
                cell.y = top;
                cell.width = cell.widget().width;
                cell.height = height;
                cell.centerY().alignWidget();
                right = cell.x - gap;
            }
            top += height;
        }
    }

    @Override
    public void calculateWidgetPositions() {
        super.calculateWidgetPositions();
        for (Cell<?> cell : cells) HbWidthLayout.fit(cell.widget(), cell.width);
    }
}
