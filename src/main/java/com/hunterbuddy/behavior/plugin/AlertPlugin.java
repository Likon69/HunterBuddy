package com.hunterbuddy.behavior.plugin;

import com.hunterbuddy.behavior.core.StopReport;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.hooks.HookEntry;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

public final class AlertPlugin implements Plugin {
    private final Consumer<String> chat;
    private final Consumer<StopReport> stops;
    private BooleanSupplier enabled = () -> true;
    private Setting<Boolean> chatEnabled;

    public AlertPlugin(Consumer<String> chat, Consumer<StopReport> stops) {
        this.chat = Objects.requireNonNull(chat);
        this.stops = Objects.requireNonNull(stops);
    }

    public void enabledWhen(BooleanSupplier enabled) { this.enabled = Objects.requireNonNull(enabled); }

    public void notify(String message) {
        if (enabled.getAsBoolean() && (chatEnabled == null || chatEnabled.get())) chat.accept(message);
    }

    public void stopped(StopReport report) {
        if (enabled.getAsBoolean() && (chatEnabled == null || chatEnabled.get())) stops.accept(report);
    }

    @Override
    public String name() { return "alerts"; }

    @Override
    public void onRegister(SettingGroup settings) {
        chatEnabled = settings.add(new BoolSetting.Builder().name("chat").description("Report death, SafeIdle, stops and gate deadlines in chat.").defaultValue(true).build());
    }

    @Override
    public void pulse(TickContext ctx) { Objects.requireNonNull(ctx); }

    @Override
    public List<HookEntry> entries(PluginContext context) { return List.of(); }
}
