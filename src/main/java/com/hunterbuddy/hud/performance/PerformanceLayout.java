package com.hunterbuddy.hud.performance;

public final class PerformanceLayout {
    public final double[] widths = new double[3];
    public double width;
    public double height;
    public double padding;
    public double gap;
    public double rowY;
    public double rowHeight;
    public double inlineTitle;
    public double labelInset;
    public double valueInset;

    public void update(double scale, boolean compact, boolean title, double labelHeight, double valueHeight,
                       double titleHeight, double titleWidth, double[] labels, double[] values) {
        padding = Math.max(5, (compact ? 8 : 11) * scale);
        gap = Math.max(5, (compact ? 9 : 11) * scale);
        labelInset = (compact ? 9 : 12) * scale;
        valueInset = (compact ? 10 : 12) * scale;
        rowHeight = Math.max((compact ? 26 : 34) * scale,
            Math.max(labelHeight, valueHeight) + (compact ? 8 : 14) * scale);
        inlineTitle = compact && title ? titleWidth + 12 * scale : 0;
        rowY = padding + (title && !compact ? titleHeight + 6 * scale : 0);
        width = padding * 2 + gap * (widths.length - 1) + inlineTitle;
        for (int i = 0; i < widths.length; i++) {
            widths[i] = labelInset + labels[i] + (compact ? 12 : 14) * scale + values[i] + valueInset;
            width += widths[i];
        }
        if (title) width = Math.max(width, titleWidth + padding * 2);
        height = rowY + rowHeight + padding;
    }
}
