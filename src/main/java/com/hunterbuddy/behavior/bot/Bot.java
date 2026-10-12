package com.hunterbuddy.behavior.bot;

import com.hunterbuddy.behavior.hooks.HookEntry;

public interface Bot {
    String name();
    String description();
    HookEntry taskEntry(BotContext context);
    BotConfig config();
    BotStatus status();
}
