package com.hunterbuddy.behavior.bot;

import com.hunterbuddy.behavior.core.StopReport;
import com.hunterbuddy.behavior.hooks.HookEntry;
import java.util.List;

public interface Bot {
    String name();
    String description();
    HookEntry taskEntry(BotContext context);
    List<HookEntry> extraEntries(BotContext context);
    BotConfig config();
    void onStart(BotContext context);
    void onStop(StopReport report);
    BotStatus status();
}
