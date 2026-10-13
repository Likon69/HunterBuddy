package com.hunterbuddy.behavior.bot;

import com.hunterbuddy.behavior.core.StopReport;
import com.hunterbuddy.behavior.hooks.HookEntry;
import java.util.List;
import meteordevelopment.meteorclient.settings.SettingGroup;

public interface Bot {
    String name();
    String description();
    void onRegister(SettingGroup settings);
    HookEntry taskEntry(BotContext context);
    List<HookEntry> extraEntries(BotContext context);
    BotConfig config();
    void onStart(BotContext context);
    void onStop(StopReport report);
    default void onResume(BotContext context) {
    }
    BotStatus status();
}
