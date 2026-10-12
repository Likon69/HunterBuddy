package com.hunterbuddy.behavior.plugin;

import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.trace.TraceEvent;
import java.util.List;
import meteordevelopment.meteorclient.settings.SettingGroup;

public interface Plugin {
    String name();
    void onRegister(SettingGroup settings);
    void pulse(TickContext ctx);
    List<HookEntry> entries(PluginContext context);
    default void observe(TraceEvent event) {
    }
}
