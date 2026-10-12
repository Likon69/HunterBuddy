package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.behavior.core.BehaviorEngine;
import com.hunterbuddy.behavior.core.Clock;
import com.hunterbuddy.behavior.core.EngineSnapshot;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.RootState;
import com.hunterbuddy.behavior.lab.LabBot;
import com.hunterbuddy.behavior.lab.LabScenario;
import com.hunterbuddy.behavior.mc.BehaviorEvents;
import com.hunterbuddy.behavior.mc.BehaviorMessages;
import com.hunterbuddy.behavior.trace.LogSink;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

public final class TreeJava extends Module {
    private final SettingGroup general = settings.getDefaultGroup();
    private final Setting<LabScenario> scenario = general.add(new EnumSetting.Builder<LabScenario>()
        .name("lab-scenario").description("Simulated LabBot scenario; never controls Minecraft actions.")
        .defaultValue(LabScenario.WORK).onChanged(this::scenarioChanged).build());
    private final Setting<Boolean> hud = general.add(new BoolSetting.Builder()
        .name("hud").description("Show the registered TreeJava behavior HUD.").defaultValue(true).build());
    private final BehaviorMessages messages = new BehaviorMessages(text -> info("%s", text), HunterBuddyAddon.LOG::info,
        () -> mc.world != null);
    private final BehaviorEngine engine = new BehaviorEngine(new Clock(), messages::stopped,
        messages::alert, new LogSink(HunterBuddyAddon.LOG::info));

    public TreeJava() {
        super(HunterBuddyAddon.HUNT_CATEGORY, "TreeJava", "Central behavior tree: simulated LabBot in phase 1.");
        runInMainMenu = true;
        autoSubscribe = false;
        MeteorClient.EVENT_BUS.subscribe(new BehaviorEvents(this));
    }

    @Override
    public void onActivate() {
        try {
            engine.startBot(new LabBot(scenario.get()));
        } catch (RuntimeException ex) {
            error("%s", ex.getMessage());
        }
    }

    @Override
    public void onDeactivate() {
        engine.stopBot(ExitReason.STOPPED, "TreeJava disabled");
    }

    public void startLab() {
        if (isActive()) throw new IllegalStateException("TreeJava is already active; use .bt switch lab");
        enable();
        if (engine.snapshot().state() == RootState.STOPPED) {
            disable();
            throw new IllegalStateException("TreeJava failed to start; see stop report");
        }
    }

    public void switchLab() {
        if (!isActive()) throw new IllegalStateException("TreeJava is not active");
        engine.switchBot(new LabBot(scenario.get()));
    }

    public void selectScenario(LabScenario next) {
        scenario.set(next);
        if (!isActive()) enable();
    }

    private void scenarioChanged(LabScenario next) {
        if (isActive()) engine.switchBot(new LabBot(next));
    }

    public void stopEngine(ExitReason reason, String detail) {
        engine.stopBot(reason, detail);
        if (isActive()) disable();
    }

    public void tickEngine() {
        messages.flush();
        if (!isActive()) return;
        try {
            engine.tick();
        } finally {
            if (engine.snapshot().state() == RootState.STOPPED) disable();
        }
    }

    public EngineSnapshot snapshot() {
        return engine.snapshot();
    }

    public boolean hudEnabled() {
        return hud.get();
    }

    @Override
    public String getInfoString() {
        EngineSnapshot snapshot = engine.snapshot();
        return snapshot.bot() + " " + snapshot.state();
    }
}
