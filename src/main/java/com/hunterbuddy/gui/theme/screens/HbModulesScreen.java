package com.hunterbuddy.gui.theme.screens;

import com.hunterbuddy.gui.theme.HbGuiTheme;
import com.hunterbuddy.gui.theme.widgets.HbModuleEntry;
import meteordevelopment.meteorclient.events.meteor.ActiveModulesChangedEvent;
import meteordevelopment.meteorclient.events.meteor.ModuleBindChangedEvent;
import meteordevelopment.meteorclient.gui.screens.ModulesScreen;
import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.Click;

import java.util.ArrayList;
import java.util.List;

public class HbModulesScreen extends ModulesScreen {
    private final List<HbModuleEntry> expanded = new ArrayList<>();
    private HbModuleEntry clipboardTarget;
    private WCategoryController categoryController;
    private Cell<WWindow> favorites;
    private boolean favoritesDirty;

    public HbModulesScreen(HbGuiTheme theme) {
        super(theme);
    }

    @Override
    protected Cell<WWindow> createFavorites(WContainer container) {
        categoryController = (WCategoryController) container;
        favorites = super.createFavorites(container);
        return favorites;
    }

    @Override
    protected void init() {
        super.init();
        if (favorites != null && !categoryController.cells.contains(favorites)) favorites = null;
        prune();
    }

    public void refreshFavorites() {
        favoritesDirty = true;
    }

    public void cancelBindings() {
        for (HbModuleEntry entry : expanded) entry.settings().cancelBinding();
    }

    private void updateFavorites() {
        if (!favoritesDirty) return;
        favoritesDirty = false;
        if (favorites == null) {
            favorites = createFavorites(categoryController);
            if (favorites != null) categoryController.windows.add(favorites.widget());
        } else {
            WWindow window = favorites.widget();
            window.clear();
            if (!createFavoritesW(window)) {
                categoryController.remove(favorites);
                categoryController.windows.remove(window);
                favorites = null;
            }
        }
    }

    public void toggle(HbModuleEntry entry) {
        prune();
        if (entry.isExpanded()) {
            entry.collapse();
            expanded.remove(entry);
            if (clipboardTarget == entry) clipboardTarget = null;
            return;
        }

        expanded.removeIf(open -> {
            if (open.window() != entry.window() && open.module() != entry.module()) return false;
            open.collapse();
            return true;
        });

        entry.expand();
        expanded.add(entry);
        clipboardTarget = entry;
    }

    private void prune() {
        expanded.removeIf(entry -> {
            if (entry.isAttached()) return false;
            entry.collapse();
            return true;
        });
        if (!expanded.contains(clipboardTarget)) clipboardTarget = null;
    }

    @Override
    public void tick() {
        super.tick();
        updateFavorites();
        prune();
        for (HbModuleEntry entry : List.copyOf(expanded)) entry.settings().tick();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (!locked) {
            prune();
            for (HbModuleEntry entry : expanded) {
                if (entry.mouseOver && entry.getView().mouseOver) clipboardTarget = entry;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !Modules.get().isBinding();
    }

    @Override
    public void removed() {
        cancelBindings();
        super.removed();
    }

    @EventHandler
    private void onModuleBindChanged(ModuleBindChangedEvent event) {
        for (HbModuleEntry entry : expanded) entry.settings().resetBind();
    }

    @EventHandler
    private void onActiveModulesChanged(ActiveModulesChangedEvent event) {
        for (HbModuleEntry entry : expanded) entry.settings().syncActive();
    }

    @Override
    public boolean toClipboard() {
        prune();
        return clipboardTarget == null ? super.toClipboard() : clipboardTarget.settings().toClipboard();
    }

    @Override
    public boolean fromClipboard() {
        prune();
        return clipboardTarget == null ? super.fromClipboard() : clipboardTarget.settings().fromClipboard();
    }
}
