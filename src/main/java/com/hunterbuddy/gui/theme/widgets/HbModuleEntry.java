package com.hunterbuddy.gui.theme.widgets;

import com.hunterbuddy.gui.theme.screens.HbModulesScreen;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.systems.modules.Module;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class HbModuleEntry extends WVerticalList {
    private final Module module;
    private final String title;
    private HbModuleSettings settings;

    public HbModuleEntry(Module module, String title) {
        this.module = module;
        this.title = title;
        spacing = 0;
    }

    @Override
    public void init() {
        add(new HbModule(module, title, this)).expandX();
    }

    @Override
    protected void onCalculateSize() {
        super.onCalculateSize();
        width = cells.getFirst().widget().width;
    }

    public Module module() {
        return module;
    }

    public HbModuleSettings settings() {
        return settings;
    }

    public boolean isExpanded() {
        return settings != null;
    }

    public WWindow window() {
        for (WWidget widget = parent; widget != null; widget = widget.parent) {
            if (widget instanceof WWindow window) return window;
        }
        return null;
    }

    public boolean isAttached() {
        WWidget widget = this;
        while (widget.parent instanceof WContainer container) {
            WWidget child = widget;
            if (container.cells.stream().noneMatch(cell -> cell.widget() == child)) return false;
            widget = container;
        }
        return widget.parent == null && widget != this;
    }

    public void toggle() {
        if (mc.currentScreen instanceof HbModulesScreen screen) screen.toggle(this);
        else mc.setScreen(theme.moduleScreen(module));
    }

    public void expand() {
        if (settings != null) return;
        settings = new HbModuleSettings(module);
        add(settings).expandX().padLeft(6).padBottom(4);
    }

    public void collapse() {
        if (settings == null) return;
        settings.cancelBinding();
        remove(cells.getLast());
        settings = null;
    }
}
