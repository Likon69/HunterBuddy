package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;

import java.util.List;

public class HbSettingTable extends WTable implements HbWidget {
    public HbSettingTable() {
        horizontalSpacing = 8;
        verticalSpacing = 0;
    }

    private double rowHeight(List<Cell<?>> row) {
        double height = theme.scale(22);
        for (int i = 0; i < row.size(); i++) height = Math.max(height, row.get(i).widget().height);
        return height;
    }

    private double leftInset() {
        return theme.scale(style().settingInset() - 12 + (style().hasCounter() ? 6 : 0));
    }

    private double rightInset() {
        return theme.scale(style().hasCounter() ? 10 : style().inverted() ? 4 : 2);
    }

    @Override
    protected void onCalculateSize() {
        super.onCalculateSize();
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
            for (int column = row.size() - 1; column >= 0; column--) {
                Cell<?> cell = row.get(column);
                cell.x = column == 0 ? x + leftInset() : right - cell.widget().width;
                cell.y = top;
                cell.width = cell.widget().width;
                cell.height = height;
                cell.centerY().alignWidget();
                right = cell.x - theme.scale(8);
            }
            top += height;
        }
    }
}
