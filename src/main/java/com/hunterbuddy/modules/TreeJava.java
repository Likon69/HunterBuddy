package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.behavior.core.BehaviorEngine;
import com.hunterbuddy.behavior.core.Clock;
import com.hunterbuddy.behavior.core.EngineSnapshot;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.RootState;
import com.hunterbuddy.behavior.core.TargetPoint;
import com.hunterbuddy.behavior.lab.LabBot;
import com.hunterbuddy.behavior.lab.LabScenario;
import com.hunterbuddy.behavior.lab.LabTask;
import com.hunterbuddy.behavior.lab.LiveLabBot;
import com.hunterbuddy.behavior.lab.LookTask;
import com.hunterbuddy.behavior.lab.WalkTask;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.BehaviorEvents;
import com.hunterbuddy.behavior.mc.BehaviorMessages;
import com.hunterbuddy.behavior.mc.LabSettings;
import com.hunterbuddy.behavior.mc.McSensors;
import com.hunterbuddy.behavior.mc.ServerFacts;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.resource.LookIntent;
import com.hunterbuddy.behavior.resource.LookOutput;
import com.hunterbuddy.behavior.resource.MovementOutput;
import com.hunterbuddy.behavior.resource.SlotOutput;
import com.hunterbuddy.behavior.trace.LogSink;
import com.hunterbuddy.util.ServerSlot;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

public final class TreeJava extends Module {
    private static final double LAB_WALK_RADIUS = 1;
    private static final double LAB_TURN_RATE = 90;
    private final SettingGroup general = settings.getDefaultGroup();
    private final WorldReader world = new WorldReader(mc);
    private final ServerFacts serverFacts = new ServerFacts();
    private final BaritoneBridge baritone = new BaritoneBridge();
    private final LabSettings labSettings = new LabSettings(LabScenario.WORK, ignored -> scenarioChanged());
    private final Setting<Boolean> hud = general.add(new BoolSetting.Builder()
        .name("hud").description("Show the registered TreeJava behavior HUD.").defaultValue(true).build());
    private final Setting<Boolean> fullTraceLog = general.add(new BoolSetting.Builder()
        .name("lab-full-trace-log").description("Write every LabBot trace event to the game log during laboratory tests.")
        .defaultValue(false).build());
    private final BehaviorMessages messages = new BehaviorMessages(text -> info("%s", text), HunterBuddyAddon.LOG::info,
        () -> mc.world != null);
    private final BehaviorEngine engine = new BehaviorEngine(new Clock(), messages::stopped,
        messages::alert, new LogSink(HunterBuddyAddon.LOG::info, fullTraceLog::get));
    private final McSensors mcSensors = new McSensors(mc, world, serverFacts, baritone, engine.leases());
    private LabTask requestedTask;

    public TreeJava() {
        super(HunterBuddyAddon.HUNT_CATEGORY, "TreeJava", "Behavior tree laboratory: live walk/look and simulated foundation scenarios.");
        runInMainMenu = true;
        autoSubscribe = false;
        engine.bots().register("lab", () -> new LabBot(labSettings));
        for (String bot : engine.bots().names()) engine.bots().create(bot).onRegister(settings.createGroup("Bot: " + bot));
        for (var routine : engine.routines()) routine.onRegister(settings.createGroup("Routine: " + routine.name()));
        engine.outputs().connect(new MovementOutput(baritone), new LookOutput(mc), new SlotOutput(mc), serverFacts::tick);
        ServerSlot.get();
        MeteorClient.EVENT_BUS.subscribe(serverFacts);
        MeteorClient.EVENT_BUS.subscribe(new BehaviorEvents(this));
    }

    @Override
    public void onActivate() {
        try {
            if (requestedTask == null) engine.startBot(engine.bots().create("lab"));
            else {
                requireLive(requestedTask);
                engine.startBot(new LiveLabBot(labSettings, requestedTask, world, engine.outputs()));
            }
        } catch (RuntimeException ex) {
            error("%s", ex.getMessage());
            disable();
        }
    }

    @Override
    public void onDeactivate() {
        engine.stopBot(ExitReason.STOPPED, "TreeJava disabled");
    }

    public void startLab() {
        if (isActive()) throw new IllegalStateException("TreeJava is already active; use .bt switch lab");
        enable();
        if (!isActive() || engine.snapshot().state() == RootState.STOPPED) {
            disable();
            throw new IllegalStateException("TreeJava failed to start; see stop report");
        }
    }

    public void switchLab() {
        if (!isActive()) throw new IllegalStateException("TreeJava is not active");
        engine.switchBot(engine.bots().create("lab"));
    }

    public void selectScenario(LabScenario next) {
        labSettings.selectScenario(next);
        if (!isActive()) enable();
    }

    public void walkTo(double x, double y, double z) {
        startLive(new WalkTask(new TargetPoint(x, y, z), LAB_WALK_RADIUS));
    }

    public void lookAt(double yaw, double pitch) {
        if (!Double.isFinite(yaw) || !Double.isFinite(pitch) || pitch < -90 || pitch > 90) {
            throw new IllegalArgumentException("Look requires finite yaw and pitch in [-90, 90]");
        }
        startLive(new LookTask(new LookIntent((float) yaw, (float) pitch), LAB_TURN_RATE));
    }

    private void requireLive(LabTask task) {
        String refusal = world.refusal(task.resources(), baritone.capture());
        if (refusal == null && engine.outputs().foreignMovement()) refusal = "Another producer owns Baritone movement inputs";
        if (refusal != null) throw new IllegalStateException(refusal);
        if (task instanceof WalkTask walk && !world.loaded(walk.point())) throw new IllegalStateException("Walking target chunk is not loaded");
    }

    private void startLive(LabTask task) {
        requireLive(task);
        if (isActive()) {
            engine.switchBot(new LiveLabBot(labSettings, task, world, engine.outputs()));
            return;
        }
        requestedTask = task;
        try {
            enable();
        } finally {
            requestedTask = null;
        }
        if (!isActive() || engine.snapshot().state() == RootState.STOPPED) {
            disable();
            throw new IllegalStateException("Live lab failed to start; see stop report");
        }
    }

    private void scenarioChanged() {
        if (isActive()) switchLab();
    }

    public void stopEngine(ExitReason reason, String detail) {
        engine.stopBot(reason, detail);
        if (isActive()) disable();
    }

    public void tickEngine() {
        serverFacts.beginTick();
        try {
            serverFacts.bind(mc.player == null ? -1 : mc.player.getId(), mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getConnection());
            messages.flush();
            if (!isActive()) return;
            LiveLabBot live = engine.liveLab();
            if (live == null) engine.tick();
            else if (!world.available()) stopEngine(ExitReason.DISCONNECTED, "Minecraft world became unavailable");
            else if (!live.identity().equals(world.identity())) stopEngine(ExitReason.WORLD_CHANGED, "Live laboratory world or player changed");
            else {
                String refusal = world.refusal(live.resources(), baritone.capture());
                if (refusal == null && engine.outputs().foreignMovement()) refusal = "Another producer owns Baritone movement inputs";
                if (refusal != null) stopEngine(ExitReason.STOPPED, "Live lab refused: " + refusal);
                else engine.tickCaptured(mcSensors::capture, serverFacts::samplesValid);
            }
        } catch (RuntimeException ex) {
            engine.fault(ex);
        } finally {
            serverFacts.endTick();
            if (isActive() && engine.snapshot().state() == RootState.STOPPED) disable();
        }
    }

    public EngineSnapshot snapshot() {
        return engine.snapshot();
    }

    public List<String> botNames() {
        return engine.bots().names();
    }

    public List<String> outputStatus() {
        return engine.outputs().status();
    }

    public void worldLost() {
        serverFacts.bind(-1, null);
        engine.outputs().discardSlot();
        stopEngine(ExitReason.DISCONNECTED, "Game session ended");
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
