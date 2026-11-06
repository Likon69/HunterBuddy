package com.hunterbuddy.gui.theme.widgets;

import com.hunterbuddy.gui.theme.screens.HbModulesScreen;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.widgets.WKeybind;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WFavorite;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.NbtUtils;
import meteordevelopment.meteorclient.utils.render.prompts.OkPrompt;
import net.minecraft.nbt.NbtCompound;

import static meteordevelopment.meteorclient.utils.Utils.getWindowWidth;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class HbModuleSettings extends WVerticalList implements HbWidget {
    private final Module module;
    private WContainer settingsContainer;
    private WKeybind keybind;
    private WCheckbox active;
    private boolean binding;

    public HbModuleSettings(Module module) {
        this.module = module;
    }

    @Override
    public void init() {
        WHorizontalList heading = add(theme.horizontalList()).expandX().widget();
        WFavorite favorite = heading.add(theme.favorite(module.favorite)).widget();
        favorite.action = () -> {
            module.favorite = favorite.checked;
            if (mc.currentScreen instanceof HbModulesScreen screen) screen.refreshFavorites();
        };
        heading.add(theme.label(module.description, getWindowWidth() / 2.0)).expandX();

        if (module.addon != null && module.addon != MeteorClient.ADDON) {
            WHorizontalList addon = add(theme.horizontalList()).expandX().widget();
            addon.add(theme.label("From: ").color(theme.textSecondaryColor()));
            addon.add(theme.label(module.addon.name).color(module.addon.color));
        }

        if (!module.settings.groups.isEmpty()) {
            settingsContainer = add(theme.verticalList()).expandX().widget();
            settingsContainer.add(theme.settings(module.settings)).expandX();
        }

        WWidget widget = module.getWidget(theme);
        if (widget != null) {
            add(theme.horizontalSeparator()).expandX();
            add(new HbHorizontalViewport(widget)).expandX();
        }

        WSection section = add(theme.section("Bind", true)).expandX().widget();
        WHorizontalList bind = section.add(theme.horizontalList()).expandX().widget();
        bind.add(theme.label("Bind: "));
        keybind = bind.add(theme.keybind(module.keybind)).expandX().widget();
        keybind.actionOnSet = () -> {
            if (mc.currentScreen instanceof HbModulesScreen screen) screen.cancelBindings();
            binding = true;
            Modules.get().setModuleToBind(module);
        };
        WButton reset = bind.add(theme.button(GuiRenderer.RESET)).expandCellX().right().widget();
        reset.action = this::resetModuleBind;
        reset.tooltip = "Reset";

        WHorizontalList release = section.add(theme.horizontalList()).widget();
        release.add(theme.label("Toggle on bind release: "));
        WCheckbox releaseCheckbox = release.add(theme.checkbox(module.toggleOnBindRelease)).widget();
        releaseCheckbox.action = () -> module.toggleOnBindRelease = releaseCheckbox.checked;

        WHorizontalList feedback = section.add(theme.horizontalList()).widget();
        feedback.add(theme.label("Chat Feedback: "));
        WCheckbox feedbackCheckbox = feedback.add(theme.checkbox(module.chatFeedback)).widget();
        feedbackCheckbox.action = () -> module.chatFeedback = feedbackCheckbox.checked;

        add(theme.horizontalSeparator()).expandX();
        WHorizontalList bottom = add(theme.horizontalList()).expandX().widget();
        bottom.add(theme.label("Active: "));
        active = bottom.add(theme.checkbox(module.isActive())).expandCellX().widget();
        active.action = () -> {
            if (module.isActive() != active.checked) module.toggle();
        };

        WHorizontalList sharing = bottom.add(theme.horizontalList()).right().widget();
        WButton copy = sharing.add(theme.button(GuiRenderer.COPY)).widget();
        copy.action = () -> {
            if (toClipboard()) {
                OkPrompt.create()
                    .title("Module copied!")
                    .message("The settings for this module are now in your clipboard.")
                    .message("You can also copy settings using Ctrl+C.")
                    .message("Settings can be imported using Ctrl+V or the paste button.")
                    .id("config-sharing-guide")
                    .show();
            }
        };
        copy.tooltip = "Copy config";
        WButton paste = sharing.add(theme.button(GuiRenderer.PASTE)).widget();
        paste.action = this::fromClipboard;
        paste.tooltip = "Paste config";
    }

    @Override
    protected void onCalculateSize() {
        super.onCalculateSize();
        width += theme.scale(12);
        height += theme.scale(12);
    }

    @Override
    protected void onCalculateWidgetPositions() {
        double inset = theme.scale(6);
        x += inset;
        y += inset;
        width -= inset * 2;
        super.onCalculateWidgetPositions();
        width += inset * 2;
        x -= inset;
        y -= inset;
    }

    @Override
    public void calculateWidgetPositions() {
        super.calculateWidgetPositions();
        for (Cell<?> cell : cells) {
            HbWidthLayout.fit(cell.widget(), width - theme.scale(12) - cell.padLeft() - cell.padRight());
        }
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        style().drawModuleSettings(renderer, theme(), this);
    }

    public void tick() {
        module.settings.tick(settingsContainer, theme);
        syncActive();
    }

    public void syncActive() {
        active.checked = module.isActive();
    }

    public void resetBind() {
        keybind.reset();
        binding = false;
    }

    private void resetModuleBind() {
        keybind.resetBind();
        binding = false;
    }

    public void cancelBinding() {
        if (binding) resetBind();
    }

    public boolean toClipboard() {
        NbtCompound tag = new NbtCompound();
        tag.putString("name", module.name);
        NbtCompound settingsTag = module.settings.toTag();
        if (!settingsTag.isEmpty()) tag.put("settings", settingsTag);
        return NbtUtils.toClipboard(tag);
    }

    public boolean fromClipboard() {
        NbtCompound tag = NbtUtils.fromClipboard();
        if (tag == null || !tag.getString("name", "").equals(module.name)) return false;
        var settingsTag = tag.getCompound("settings");
        if (settingsTag.isPresent()) module.settings.fromTag(settingsTag.get());
        else module.settings.reset();
        cancelBinding();
        clear();
        settingsContainer = null;
        init();
        return true;
    }
}
