package com.hunterbuddy.gui.theme.widgets;

import com.hunterbuddy.gui.theme.HbGuiTheme;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.WMeteorWindow;
import meteordevelopment.meteorclient.gui.widgets.WWidget;

public final class HbMeteorWindow extends WMeteorWindow {
    public HbMeteorWindow(WWidget icon, String title) {
        super(icon, title);
    }

    @Override
    public void init() {
        HbGuiTheme owner = (HbGuiTheme) theme;
        owner.beginNativeWidgets();
        try {
            super.init();
        } finally {
            owner.endNativeWidgets();
        }
    }
}
