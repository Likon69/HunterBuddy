package com.hunterbuddy.chatdock.entries;

import com.hunterbuddy.chatdock.DockEntry;
import com.hunterbuddy.journal.JournalCapture;
import com.hunterbuddy.journal.JournalOverlay;
import com.hunterbuddy.journal.JournalSettings;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gui.DrawContext;

public final class LogDockEntry implements DockEntry {
    private JournalSettings config;
    private JournalOverlay overlay;
    private boolean active;
    private boolean capturing;

    @Override
    public void configure(Settings settings) {
        config = new JournalSettings(settings.createGroup("Log"), this::changed);
        overlay = new JournalOverlay(config);
    }

    private void changed() {
        boolean capture = active && config.enabled.get();
        if (capture == capturing) return;
        capturing = capture;
        if (capture) {
            config.prepare();
            JournalCapture.start(config.store);
            overlay.reset();
        } else {
            JournalCapture.stop();
            overlay.chatClosed();
        }
    }

    @Override
    public void activate() { active = true; changed(); }

    @Override
    public void deactivate() { active = false; changed(); }

    @Override
    public void shutdown() { deactivate(); overlay.shutdown(); }

    @Override
    public void fontChanged() { overlay.fontChanged(); }

    @Override
    public void widgets(GuiTheme theme, WVerticalList controls) {
        controls.add(theme.button("Save log performance report")).expandX().widget().action = overlay::savePerformance;
    }

    @Override
    public String id() { return "log"; }

    @Override
    public String label() { return "Log"; }

    @Override
    public boolean visible() { return capturing; }

    @Override
    public boolean selected() { return overlay.isOpen(); }

    @Override
    public void toggle() { overlay.toggle(); }

    @Override
    public void icon(HudRenderer renderer, double x, double y, double size, Color color) {
        for (int i = 0; i < 3; i++) renderer.quad(x, y + size * (0.18 + i * 0.28),
            size * (i == 2 ? 0.6 : 1), Math.max(1, size * 0.1), color);
    }

    @Override
    public void render(DrawContext context, boolean chat) { overlay.render(context, chat); }

    @Override
    public boolean click(double x, double y, int button) { return overlay.click(x, y, button); }

    @Override
    public boolean scroll(double x, double y, double amount) { return overlay.scroll(x, y, amount); }

    @Override
    public boolean key(int key, int modifiers) { return overlay.key(key, modifiers); }

    @Override
    public boolean inputFocused() { return overlay.inputFocused(); }

    @Override
    public void closed() { overlay.chatClosed(); }
}
