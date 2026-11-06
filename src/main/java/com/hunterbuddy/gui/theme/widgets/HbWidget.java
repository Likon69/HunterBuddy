package com.hunterbuddy.gui.theme.widgets;

import com.hunterbuddy.gui.theme.HbGuiTheme;
import com.hunterbuddy.gui.theme.HbStyle;
import meteordevelopment.meteorclient.gui.utils.BaseWidget;

public interface HbWidget extends BaseWidget {
    default HbGuiTheme theme() {
        return (HbGuiTheme) getTheme();
    }

    default HbStyle style() {
        return theme().style();
    }
}
