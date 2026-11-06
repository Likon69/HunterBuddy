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
            double available = Math.max(0, width - leftInset() - rightInset());
            double gap = theme.scale(8);
            double fixed = gap * Math.max(0, row.size() - 1);
            for (int column = 2; column < row.size(); column++) fixed += row.get(column).widget().width;
            double labelWidth = Math.min(row.getFirst().widget().width, Math.max(0, Math.min(available * 0.35, available - fixed - theme.scale(86))));
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
