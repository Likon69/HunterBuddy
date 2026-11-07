package com.hunterbuddy.gui.theme;

import meteordevelopment.meteorclient.utils.render.color.Color;

@FunctionalInterface
public interface HbQuadSink {
    void quad(double x, double y, double width, double height, Color color);
}
