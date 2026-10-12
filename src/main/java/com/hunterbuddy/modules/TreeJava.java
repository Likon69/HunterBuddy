package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.behavior.core.BehaviorEngine;
import com.hunterbuddy.behavior.core.Clock;
import com.hunterbuddy.behavior.core.EngineSnapshot;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.RootState;
import com.hunterbuddy.behavior.core.TargetPoint;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.lab.BreakColumnTask;
import com.hunterbuddy.behavior.lab.GoToTask;
import com.hunterbuddy.behavior.lab.LabBot;
import com.hunterbuddy.behavior.lab.LabScenario;
import com.hunterbuddy.behavior.lab.LabTask;
import com.hunterbuddy.behavior.lab.LiveLabBot;
import com.hunterbuddy.behavior.lab.LookTask;
import com.hunterbuddy.behavior.lab.OpenTask;
import com.hunterbuddy.behavior.lab.PillarTask;
import com.hunterbuddy.behavior.lab.PlaceTask;
import com.hunterbuddy.behavior.lab.WalkTask;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.BehaviorEvents;
import com.hunterbuddy.behavior.mc.BehaviorMessages;
import com.hunterbuddy.behavior.mc.InventoryBridge;
import com.hunterbuddy.behavior.mc.LabSettings;
import com.hunterbuddy.behavior.mc.McSensors;
import com.hunterbuddy.behavior.mc.MiningBridge;
import com.hunterbuddy.behavior.mc.PlacementBridge;
import com.hunterbuddy.behavior.mc.ServerFacts;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.nodes.KeepFlying;
import com.hunterbuddy.behavior.nodes.Landing;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.plugin.AlertPlugin;
import com.hunterbuddy.behavior.plugin.PlayerWatchPlugin;
import com.hunterbuddy.behavior.plugin.StatsPlugin;
import com.hunterbuddy.behavior.resource.BaritoneOutput;
import com.hunterbuddy.behavior.resource.ConsumablePolicy;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.LookIntent;
import com.hunterbuddy.behavior.resource.LookOutput;
import com.hunterbuddy.behavior.resource.MovementOutput;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.behavior.resource.SlotOutput;
import com.hunterbuddy.behavior.routine.GateCombatRoutine;
import com.hunterbuddy.behavior.routine.GateRegearRoutine;
import com.hunterbuddy.behavior.routine.GateSurvivalRoutine;
import com.hunterbuddy.behavior.trace.JsonlSink;
import com.hunterbuddy.behavior.trace.LogSink;
import com.hunterbuddy.util.ServerSlot;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

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
    private final Setting<Integer> rocketReserve = general.add(new IntSetting.Builder()
        .name("rocket-reserve").description("Minimum rockets kept by the consumable policy.")
        .defaultValue(1).min(0).build());
    private final BehaviorMessages messages = new BehaviorMessages(text -> info("%s", text), HunterBuddyAddon.LOG::info,
        () -> mc.world != null);
    private final AlertPlugin alerts = new AlertPlugin(messages::alert, messages::stopped);
    private final JsonlSink jsonl = new JsonlSink(MeteorClient.FOLDER.toPath().resolve("hunterbuddy/bt"));
    private final BehaviorEngine engine = new BehaviorEngine(new Clock(), alerts::stopped,
        alerts::notify, new LogSink(HunterBuddyAddon.LOG::info, fullTraceLog::get), jsonl);
    private final McSensors mcSensors = new McSensors(mc, world, serverFacts, baritone, engine.leases());
    private final ConsumablePolicy consumables = new ConsumablePolicy(world, baritone, rocketReserve::get);
    private final MiningBridge mining = new MiningBridge(world, baritone, engine.outputs()::ownBaritone);
    private final PlacementBridge placement = new PlacementBridge(world, engine.outputs(), consumables);
    private final InventoryBridge inventory = new InventoryBridge(world, serverFacts::tick, alerts::notify);
    private final Map<Hook, Setting<GateMode>> routineSelection = new EnumMap<>(Hook.class);
    private final Map<String, Setting<Boolean>> pluginSelection = new java.util.LinkedHashMap<>();
    private LabTask requestedTask;

    public TreeJava() {
        super(HunterBuddyAddon.HUNT_CATEGORY, "TreeJava", "Behavior tree laboratory: live movement, mining, placement and simulated scenarios.");
        runInMainMenu = true;
        autoSubscribe = false;
        engine.bots().register("lab", () -> new LabBot(labSettings));
        for (String bot : engine.bots().names()) engine.bots().create(bot).onRegister(settings.createGroup("Bot: " + bot));
        engine.outputs().connect(new MovementOutput(baritone), new LookOutput(mc), new SlotOutput(mc), serverFacts::tick);
        engine.outputs().connectBaritone(new BaritoneOutput(baritone, engine.leases(), consumables, serverFacts::tick));
        engine.outputs().connectMinecraft(mining, placement);
        engine.outputs().connectInventory(inventory);
        engine.gates().connect(mining::ownsMining, engine.outputs()::ownBaritone);
        mcSensors.screenOwner(inventory::isOurs);
        engine.flightKeeper(new Landing(new WithLease(Set.of(Resource.MOVEMENT, Resource.LOOK), LeasePolicy.WAIT, new KeepFlying(engine.outputs()))));
        engine.registerRoutine(new GateSurvivalRoutine());
        engine.registerRoutine(new GateCombatRoutine(world, baritone, engine.outputs()));
        engine.registerRoutine(new GateRegearRoutine());
        for (var routine : engine.routines()) routine.onRegister(settings.createGroup("Routine: " + routine.name()));
        for (Hook slot : List.of(Hook.SURVIVAL, Hook.COMBAT, Hook.REGEAR)) {
            String name = "gate-" + slot.name().toLowerCase(Locale.ROOT);
            routineSelection.put(slot, settings.createGroup("Slot: " + slot).add(new EnumSetting.Builder<GateMode>()
                .name("routine").description("Select the legacy gate routine for this hook.").defaultValue(GateMode.GATE)
                .onChanged(mode -> engine.selectRoutine(slot, mode == GateMode.NONE ? "none" : name)).build()));
        }
        for (var plugin : List.of(new StatsPlugin(engine::stats), new PlayerWatchPlugin(labSettings::pauseOnPlayer), alerts)) {
            String name = plugin.name();
            SettingGroup group = settings.createGroup("Plugin: " + name);
            engine.plugins().register(plugin, group);
            engine.plugins().enable(name);
            pluginSelection.put(name, group.add(new BoolSetting.Builder().name("enabled")
                .description("Enable this behavior plugin.").defaultValue(true).onChanged(value -> applyPlugin(name, value)).build()));
        }
        alerts.enabledWhen(() -> engine.plugins().enabled("alerts"));
        ServerSlot.get();
        MeteorClient.EVENT_BUS.subscribe(serverFacts);
        MeteorClient.EVENT_BUS.subscribe(mining);
        MeteorClient.EVENT_BUS.subscribe(placement);
        MeteorClient.EVENT_BUS.subscribe(inventory);
        MeteorClient.EVENT_BUS.subscribe(new BehaviorEvents(this));
    }

    @Override
    public void onActivate() {
        try {
            jsonl.session("lab");
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

    public void goTo(double x, double y, double z) {
        startLive(new GoToTask(new TargetPoint(x, y, z), LAB_WALK_RADIUS, baritone));
    }

    public void breakColumn(int height) {
        if (!world.available()) throw new IllegalStateException("Minecraft world is unavailable");
        startLive(new BreakColumnTask(world.feet().up(2), height));
    }

    public void placeAt(double x, double y, double z) {
        TargetPoint point = new TargetPoint(x, y, z);
        if (!baritone.capture().available()) throw new IllegalStateException("Baritone consumable policy is unavailable");
        startLive(new PlaceTask(BlockPos.ofFloored(point.x(), point.y(), point.z()),
            stack -> stack.isOf(Items.OBSIDIAN) || baritone.settings().acceptableThrowawayItems.value.contains(stack.getItem())));
    }

    public void pillar(int height) {
        if (!world.available()) throw new IllegalStateException("Minecraft world is unavailable");
        if (height < 1 || height > 24) throw new IllegalArgumentException("pillar height outside [1,24]");
        startLive(new PillarTask(mc.player.getY() + height));
    }

    public void openAt(int x, int y, int z) {
        startLive(new OpenTask(new BlockPos(x, y, z), baritone));
    }

    private void requireLive(LabTask task) {
        String refusal = world.refusal(task.resources(), baritone.capture(), engine.outputs().ownBaritone() || engine.outputs().borrowedBaritone());
        if (refusal == null && !world.legacyRegear() && engine.outputs().foreignMovement()) refusal = "Another producer owns Baritone movement inputs";
        if (refusal == null && task.resources().contains(Resource.MINING)) refusal = mining.refusal();
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
            engine.advanceNativeOutputs();
            messages.flush();
            if (!isActive()) return;
            LiveLabBot live = engine.liveLab();
            if (live == null) engine.tick();
            else if (!world.available()) stopEngine(ExitReason.DISCONNECTED, "Minecraft world became unavailable");
            else if (!live.identity().equals(world.identity())) stopEngine(ExitReason.WORLD_CHANGED, "Live laboratory world or player changed");
            else {
                String refusal = world.refusal(live.resources(), baritone.capture(), engine.outputs().ownBaritone() || engine.outputs().borrowedBaritone());
                if (refusal == null && !world.legacyRegear() && engine.outputs().foreignMovement()) refusal = "Another producer owns Baritone movement inputs";
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
        List<String> lines = new ArrayList<>(engine.outputs().status());
        lines.add(jsonl.status());
        return List.copyOf(lines);
    }

    public List<String> why() {
        List<String> lines = new ArrayList<>(engine.why());
        lines.add(inventory.status());
        lines.add(jsonl.status());
        return List.copyOf(lines);
    }
    public List<String> routineStatus() { return engine.routineStatus(); }
    public List<String> pluginStatus() { return engine.plugins().status(); }
    public void resetStats() { engine.resetStats(); }

    public void selectRoutine(String slotName, String name) {
        Hook slot = Hook.valueOf(slotName.toUpperCase(Locale.ROOT));
        Setting<GateMode> setting = routineSelection.get(slot);
        if (setting == null) throw new IllegalArgumentException("slot must be survival, combat or regear");
        if (!name.equals("none") && !name.equals("gate-" + slot.name().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("unknown routine: " + name);
        setting.set(name.equals("none") ? GateMode.NONE : GateMode.GATE);
    }

    public void setPlugin(String name, boolean enabled) {
        Setting<Boolean> setting = pluginSelection.get(name);
        if (setting == null) throw new IllegalArgumentException("unknown plugin: " + name);
        setting.set(enabled);
        applyPlugin(name, enabled);
    }

    private void applyPlugin(String name, boolean enabled) {
        if (enabled) engine.plugins().enable(name);
        else engine.plugins().disable(name);
    }

    public void worldLost() {
        serverFacts.bind(-1, null);
        mining.worldLost();
        placement.worldLost();
        inventory.worldLost();
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

    private enum GateMode { GATE, NONE }
}
