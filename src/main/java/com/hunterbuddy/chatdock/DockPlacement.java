package com.hunterbuddy.chatdock;

import com.hunterbuddy.modules.messenger.ui.Rect;

public final class DockPlacement {
    private DockPlacement() {
    }

    public static Rect initial(double sw, double sh, double width, double height, double chatRight, double chatTop, double gap) {
        double x = (sw - width) / 2;
        double y = Math.max(8, sh / 2 - height - gap);
        if (x < chatRight && y + height > chatTop) {
            if (chatTop - height - 12 >= 8) y = chatTop - height - 12;
            else if (chatRight + width + 12 <= sw) x = chatRight + 8;
            else y = 8;
        }
        return new Rect(x, y, width, height);
    }

    public static double coordinate(double fraction, double screen, double extent, double fallback) {
        return Math.max(0, Math.min(Math.max(0, screen - extent), fraction < 0 ? fallback : fraction * Math.max(0, screen - extent)));
    }

    public static double fraction(double position, double screen, double extent) {
        return screen <= extent ? 0 : Math.max(0, Math.min(1, position / (screen - extent)));
    }
}
