package com.hunterbuddy.modules.regear.arealoader;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;

final class AreaLoaderWidget {
    private AreaLoaderWidget() {
    }

    static WWidget build(GuiTheme theme, AreaLoader area) {
        WVerticalList list = theme.verticalList();
        WButton clearSelected = list.add(theme.button("Clear Currently Selected")).expandX().widget();
        clearSelected.action = area::clearSelectedMode;
        WButton clearAll = list.add(theme.button("Clear All")).expandX().widget();
        clearAll.action = area::clearAllModes;
        list.add(theme.horizontalSeparator()).expandX();
        WButton recovery = list.add(theme.button("Recovery Configuration")).expandX().widget();
        recovery.action = () -> MeteorClient.mc.setScreen(new AreaRecoveryScreen(theme, area));
        return list;
    }
}
