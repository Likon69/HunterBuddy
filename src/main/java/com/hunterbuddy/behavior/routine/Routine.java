package com.hunterbuddy.behavior.routine;

import com.hunterbuddy.behavior.core.StopReport;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import java.util.List;
import meteordevelopment.meteorclient.settings.SettingGroup;

public interface Routine {
    String name();
    Hook slot();
    List<HookEntry> entries(RoutineContext context);
    void onRegister(SettingGroup settings);
    void onInstall(RoutineContext context);
    void onUninstall(StopReport report);
    RoutineStatus status();
}
