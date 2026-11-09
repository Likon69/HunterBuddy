package com.hunterbuddy.chatdock;

import meteordevelopment.meteorclient.utils.render.color.Color;

public interface DockPreferences {
    double dockX();
    double dockY();
    void dockPosition(double x, double y);
    double dockScale();
    boolean dockInChat();
    Color dockAccent();
    Color dockBackground();
}
