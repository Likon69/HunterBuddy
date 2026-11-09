package com.hunterbuddy.chatdock;

import meteordevelopment.meteorclient.utils.render.color.Color;

public interface DockPreferences {
    double dockX();
    double dockY();
    void dockPosition(double x, double y);
    double dockScale();
    double dockTextScale();
    Color dockAccent();
    Color dockBackground();
}
