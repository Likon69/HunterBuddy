package com.hunterbuddy.gui.theme.widgets;

import java.util.Arrays;

final class HbItemClip {
    private double[] boxes = new double[64];
    private int count;

    void reset(double x, double y, double width, double height) {
        count = 0;
        add(x, y, x + width, y + height);
    }

    int size() { return count; }

    double edge(int index, int edge) { return boxes[index * 4 + edge]; }

    void exclude(double x, double y, double width, double height) {
        double right = x + width;
        double bottom = y + height;
        for (int i = count - 1; i >= 0; i--) {
            double left = edge(i, 0);
            double top = edge(i, 1);
            double end = edge(i, 2);
            double down = edge(i, 3);
            double ix = Math.max(left, x);
            double iy = Math.max(top, y);
            double ir = Math.min(end, right);
            double ib = Math.min(down, bottom);
            if (ix >= ir || iy >= ib) continue;
            count--;
            System.arraycopy(boxes, count * 4, boxes, i * 4, 4);
            add(left, top, end, iy);
            add(left, ib, end, down);
            add(left, iy, ix, ib);
            add(ir, iy, end, ib);
        }
    }

    private void add(double left, double top, double right, double bottom) {
        if (right <= left || bottom <= top) return;
        if ((count + 1) * 4 > boxes.length) boxes = Arrays.copyOf(boxes, boxes.length * 2);
        int offset = count++ * 4;
        boxes[offset] = left;
        boxes[offset + 1] = top;
        boxes[offset + 2] = right;
        boxes[offset + 3] = bottom;
    }
}
