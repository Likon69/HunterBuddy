package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.behavior.bot.LiveWorldBot;
import com.hunterbuddy.behavior.core.BehaviorEngine;
import com.hunterbuddy.behavior.core.Clock;
import com.hunterbuddy.behavior.core.EngineSnapshot;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.RootState;
import com.hunterbuddy.behavior.core.Sensors;
import com.hunterbuddy.behavior.core.TargetPoint;
import com.hunterbuddy.behavior.farmer.FarmerBuddyBot;
import com.hunterbuddy.behavior.farmer.FarmerCatalog;
import com.hunterbuddy.behavior.farmer.FarmerProfile;
import com.hunterbuddy.behavior.farmer.FarmerSettings;
import com.hunterbuddy.behavior.farmer.FarmerStepConfig;
import com.hunterbuddy.behavior.farmer.FarmerZone;
import com.hunterbuddy.behavior.farmer.FarmerZoneLookup;
import com.hunterbuddy.behavior.farmer.FarmerZoneReport;
import com.hunterbuddy.behavior.farmer.MissionJournal;
import com.hunterbuddy.behavior.farmer.WorldKnowledge;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.lab.BaseVisitTask;
import com.hunterbuddy.behavior.lab.BreakColumnTask;
import com.hunterbuddy.behavior.lab.FlightTask;
import com.hunterbuddy.behavior.lab.FlyToTask;
import com.hunterbuddy.behavior.lab.GoToTask;
import com.hunterbuddy.behavior.lab.LabBot;
import com.hunterbuddy.behavior.lab.LabScenario;
import com.hunterbuddy.behavior.lab.LabTask;
import com.hunterbuddy.behavior.lab.LiveLabBot;
import com.hunterbuddy.behavior.lab.LogisticsTask;
import com.hunterbuddy.behavior.lab.LookTask;
import com.hunterbuddy.behavior.lab.MineSiteTask;
import com.hunterbuddy.behavior.lab.OpenTask;
import com.hunterbuddy.behavior.lab.PillarTask;
import com.hunterbuddy.behavior.lab.PlaceTask;
import com.hunterbuddy.behavior.lab.SurveyTask;
import com.hunterbuddy.behavior.lab.WalkTask;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.BehaviorEvents;
import com.hunterbuddy.behavior.mc.BehaviorMessages;
import com.hunterbuddy.behavior.mc.FlightBridge;
import com.hunterbuddy.behavior.mc.InventoryBridge;
import com.hunterbuddy.behavior.mc.InventoryLayout;
import com.hunterbuddy.behavior.mc.LabSettings;
import com.hunterbuddy.behavior.mc.McSensors;
import com.hunterbuddy.behavior.mc.MiningBridge;
import com.hunterbuddy.behavior.mc.PlacementBridge;
import com.hunterbuddy.behavior.mc.ResumeGate;
import com.hunterbuddy.behavior.mc.ServerFacts;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.mc.XaeroWaypoints;
import com.hunterbuddy.behavior.nodes.KeepFlying;
import com.hunterbuddy.behavior.persist.PersistentStore;
import com.hunterbuddy.behavior.plugin.AlertPlugin;
import com.hunterbuddy.behavior.plugin.PlayerWatchPlugin;
import com.hunterbuddy.behavior.plugin.StatsPlugin;
import com.hunterbuddy.behavior.resource.BaritoneOutput;
import com.hunterbuddy.behavior.resource.ConsumablePolicy;
import com.hunterbuddy.behavior.resource.ElytraOutput;
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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
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
    private final FarmerSettings farmerSettings = new FarmerSettings();
    private final FarmerCatalog farmerCatalog = FarmerCatalog.load();
    private final XaeroWaypoints farmerWaypoints = new XaeroWaypoints(world);
    private final FarmerZoneLookup farmerZoneLookup = new FarmerZoneLookup(farmerWaypoints, farmerSettings::zone);
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
    private final FlightBridge flight = new FlightBridge(world, consumables, engine.outputs(), baritone);
    private final PersistentStore store = new PersistentStore(MeteorClient.FOLDER.toPath().resolve("hunterbuddy/bt"));
    private final Map<Hook, Setting<GateMode>> routineSelection = new EnumMap<>(Hook.class);
    private final Map<String, Setting<Boolean>> pluginSelection = new java.util.LinkedHashMap<>();
    private LabTask requestedTask;
    private String requestedBot;

    public TreeJava() {
        super(HunterBuddyAddon.HUNT_CATEGORY, "TreeJava", "Behavior tree laboratory: live movement, mining, placement and simulated scenarios.");
        runInMainMenu = true;
        autoSubscribe = false;
        engine.bots().register("lab", () -> new LabBot(labSettings));
        engine.bots().register("farmerbuddy", () -> new FarmerBuddyBot(farmerSettings, farmerCatalog, world, baritone, engine.outputs(), farmerWaypoints));
        List<SettingGroup> farmerStepGroups = new ArrayList<>();
        for (int i = 1; i <= FarmerSettings.MAX_STEPS; i++) farmerStepGroups.add(settings.createGroup("FarmerBuddy: step " + i));
        farmerSettings.register(farmerStepGroups, settings.createGroup("FarmerBuddy: base"));
        for (String bot : engine.bots().names()) {
            if (!bot.equals("farmerbuddy")) engine.bots().create(bot).onRegister(settings.createGroup("Bot: " + bot));
        }
        engine.outputs().connect(new MovementOutput(baritone), new LookOutput(mc), new SlotOutput(mc), serverFacts::tick);
        engine.outputs().connectBaritone(new BaritoneOutput(baritone, engine.leases(), consumables, serverFacts::tick));
        engine.outputs().connectMinecraft(mining, placement);
        engine.outputs().connectInventory(inventory);
        engine.outputs().connectFlight(flight, new ElytraOutput(baritone, world));
        engine.persistence(store, new ResumeGate(world, this::resumeRefusal));
        engine.registerSharedOwner(WorldKnowledge.OWNER);
        engine.gates().connect(mining::ownsMining, engine.outputs()::ownBaritone);
        mcSensors.screenOwner(inventory::isOurs);
        engine.flightKeeper(new KeepFlying(world, baritone, engine.outputs()));
        engine.registerRoutine(new GateSurvivalRoutine());
        engine.registerRoutine(new GateCombatRoutine());
        engine.registerRoutine(new GateRegearRoutine());
        for (var routine : engine.routines()) routine.onRegister(settings.createGroup("Routine: " + routine.name()));
        for (Hook slot : List.of(Hook.SURVIVAL, Hook.COMBAT, Hook.REGEAR)) {
            String name = "gate-" + slot.name().toLowerCase(Locale.ROOT);
            routineSelection.put(slot, settings.createGroup("Slot: " + slot).add(new EnumSetting.Builder<GateMode>()
                .name("routine").description("Select the legacy gate routine for this hook.").defaultValue(GateMode.GATE)
                .onChanged(mode -> engine.selectRoutine(slot, mode == GateMode.NONE ? "none" : name)).build()));
        }
        for (var plugin : List.of(new StatsPlugin(engine::stats), new PlayerWatchPlugin(this::pauseOnPlayer), alerts)) {
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
        MeteorClient.EVENT_BUS.subscribe(flight);
        MeteorClient.EVENT_BUS.subscribe(new BehaviorEvents(this));
    }

    @Override
    public void onActivate() {
        try {
            jsonl.session(requestedBot == null ? "lab" : requestedBot);
            if (requestedTask != null) {
                engine.bindWorld(world.storageKey());
                requireLive(requestedTask);
                engine.startBot(new LiveLabBot(labSettings, requestedTask, world, engine.outputs()));
            } else if (requestedBot != null) {
                requireLiveBot(requestedBot);
                engine.bindWorld(world.storageKey());
                engine.startBot(engine.bots().create(requestedBot));
            } else {
                engine.bindWorld("simulated");
                engine.startBot(engine.bots().create("lab"));
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

    public void startBot(String name) {
        Objects.requireNonNull(name);
        if (name.equals("lab")) { startLab(); return; }
        if (!engine.bots().names().contains(name)) throw new IllegalArgumentException("unknown bot: " + name);
        if (isActive()) throw new IllegalStateException("TreeJava is already active; use .bt switch " + name);
        requireLiveBot(name);
        requestedBot = name;
        try {
            enable();
        } finally {
            requestedBot = null;
        }
        if (!isActive() || engine.snapshot().state() == RootState.STOPPED) {
            disable();
            throw new IllegalStateException("Bot " + name + " failed to start; see stop report");
        }
    }

    public void switchBot(String name) {
        if (name.equals("lab")) { switchLab(); return; }
        if (!isActive()) throw new IllegalStateException("TreeJava is not active");
        if (!engine.bots().names().contains(name)) throw new IllegalArgumentException("unknown bot: " + name);
        requireLiveBot(name);
        engine.switchBot(engine.bots().create(name), world.storageKey());
    }

    private void requireLiveBot(String name) {
        String refusal = world.refusal(Set.of(Resource.MOVEMENT, Resource.MINING, Resource.INVENTORY),
            baritone.capture(), engine.outputs().ownBaritone() || engine.outputs().borrowedBaritone());
        if (refusal == null && !world.legacyRegear() && engine.outputs().foreignMovement()) refusal = "Another producer owns Baritone movement inputs";
        if (refusal == null) refusal = name.equals("farmerbuddy") ? mining.temporaryRefusal() : mining.refusal();
        if (refusal == null && name.equals("farmerbuddy") && world.countMatching(com.hunterbuddy.behavior.mc.InventoryLayout::silkPickaxe) == 0) refusal = "Silk Touch pickaxe missing from inventory";
        if (refusal == null && name.equals("farmerbuddy")) {
            AutoFlyingRegear regear = Modules.get().get(AutoFlyingRegear.class);
            if (regear != null && regear.isActive()) refusal = "FarmerBuddy handles its own regear";
        }
        if (refusal == null && name.equals("farmerbuddy")) refusal = farmerSettings.refusal(farmerCatalog);
        if (refusal != null) throw new IllegalStateException(refusal);
    }

    private boolean pauseOnPlayer() {
        return !engine.botName().equals("farmerbuddy") && labSettings.pauseOnPlayer();
    }

    public List<String> farmerZoneStatus() {
        FarmerStepConfig step = farmerSettings.steps().stream().filter(FarmerStepConfig::enabled).findFirst().orElse(null);
        return FarmerZoneReport.lines(step, farmerZoneLookup, farmerCatalog, world);
    }

    public void farmerReset() {
        if (!world.available()) throw new IllegalStateException("Minecraft world is unavailable");
        String owner = "bot:farmerbuddy";
        engine.editDurableMemory(world.storageKey(), owner, ctx -> MissionJournal.clear(ctx, owner));
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

    public void flightLab(String scenario) { startLive(new FlightTask(scenario, baritone)); }

    public void flyToLab(int x, int z) {
        if (!world.available()) throw new IllegalStateException("Minecraft world is unavailable");
        BlockPos home = world.feet();
        startLive(new FlyToTask(new BlockPos(x, home.getY(), z), home, world.storageKey(), baritone));
    }

    public void farmerSurvey() {
        if (!world.available()) throw new IllegalStateException("Minecraft world is unavailable");
        startLive(new SurveyTask(farmerCatalog, farmerZoneLookup, farmerSettings.steps(),
            isActive() && engine.botName().equals("farmerbuddy")
                ? CompletableFuture.completedFuture(engine.durableMemory("bot:farmerbuddy"))
                : store.load(world.storageKey(), "bot:farmerbuddy"), world.feet(), world.storageKey(), baritone));
    }

    public void farmerBaseVisit() {
        if (!world.available()) throw new IllegalStateException("Minecraft world is unavailable");
        startLive(new BaseVisitTask(farmerCatalog, farmerZoneLookup, farmerSettings,
            isActive() && engine.botName().equals("farmerbuddy")
                ? CompletableFuture.completedFuture(engine.durableMemory("bot:farmerbuddy"))
                : store.load(world.storageKey(), "bot:farmerbuddy"), world.storageKey(), baritone));
    }

    public void farmerMineSite() {
        if (!world.available()) throw new IllegalStateException("Minecraft world is unavailable");
        if (mc.player.isGliding()) throw new IllegalStateException("minesite starts on the ground after the survey");
        startLive(new MineSiteTask(farmerCatalog, farmerZoneLookup, farmerSettings.steps(),
            isActive() && engine.botName().equals("farmerbuddy")
                ? CompletableFuture.completedFuture(engine.durableMemory("bot:farmerbuddy"))
                : store.load(world.storageKey(), "bot:farmerbuddy"), world.storageKey(), baritone));
    }

    public void logisticsLab(String scenario, String profileKey) {
        if (!world.available()) throw new IllegalStateException("Minecraft world is unavailable");
        FarmerProfile profile = farmerCatalog.profile(profileKey)
            .orElseThrow(() -> new IllegalArgumentException("unknown profile: " + profileKey));
        String refusal = mining.temporaryRefusal();
        if (refusal != null) throw new IllegalStateException(refusal);
        if (world.countMatching(InventoryLayout::silkPickaxe) == 0) {
            throw new IllegalStateException("Silk Touch pickaxe missing");
        }
        BlockPos center = world.feet();
        FarmerZone zone = FarmerZone.build(List.of(
            center.add(-32, 0, -32), center.add(32, 0, -32), center.add(32, 0, 32), center.add(-32, 0, 32)));
        startLive(new LogisticsTask(scenario, profile, zone, baritone));
    }

    public void inventoryLab(String scenario) {
        if (!world.available()) throw new IllegalStateException("Minecraft world is unavailable");
        if (!Set.of("echest", "shulker", "armor elytra", "armor chestplate").contains(scenario)) throw new IllegalArgumentException("unknown inventory lab");
        List<BlockPos> positions = new ArrayList<>();
        if (!scenario.startsWith("armor")) {
            for (int radius = 1; radius <= 2; radius++) {
                for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                    BlockPos pos = world.feet().add(x, 0, z);
                    if (world.loaded(pos) && world.state(pos).isReplaceable() && !world.cleared(pos.down())
                        && com.hunterbuddy.modules.regear.util.PlacementUtils.canPlace(pos, true)
                        && com.hunterbuddy.modules.regear.util.PlacementUtils.resolvePlaceHit(pos,
                            com.hunterbuddy.modules.regear.util.PlacementUtils.DEFAULT_REACH) != null) positions.add(pos);
                }
            }
            if (positions.size() < (scenario.equals("shulker") ? 2 : 1)) throw new IllegalStateException("no reachable temporary equipment positions nearby");
        }
        BlockPos chest = positions.isEmpty() ? world.feet() : positions.getFirst();
        BlockPos shulker = positions.size() > 1 ? positions.get(1) : chest;
        startLive(new com.hunterbuddy.behavior.lab.InventoryTask(scenario, chest, shulker,
            mc.player.getMainHandStack().copy(), baritone));
    }

    private String resumeRefusal(Sensors sensors) {
        LiveWorldBot live = engine.liveBot();
        String refused = world.refusal(live != null ? live.resources() : Set.of(), baritone.capture(), engine.outputs().ownBaritone() || engine.outputs().borrowedBaritone());
        if (refused != null) return refused;
        if (!sensors.gliding() && sensors.eating()) return "AutoEatSync eating";
        if (!sensors.gliding() && sensors.combatTarget()) return "KillAuraPlus combat target";
        if (sensors.regearRunning() || sensors.regearPending()) return "AutoFlyingRegear running or pending";
        if (!sensors.gliding() && sensors.mlepMining() && !mining.ownsMining()) return "HMine foreign mining";
        if (!sensors.gliding() && pauseOnPlayer() && sensors.playersVisible() > 0) return "another player visible";
        if (engine.outputs().foreignMovement()) return "foreign forced movement";
        return null;
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
            engine.switchBot(new LiveLabBot(labSettings, task, world, engine.outputs()), world.storageKey());
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
        if (reason == ExitReason.WORLD_CHANGED) engine.outputs().worldLost();
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
            LiveWorldBot live = engine.liveBot();
            if (live == null) engine.tick();
            else if (!world.available()) engine.disconnected();
            else if (engine.suspended()) {
                Sensors captured = mcSensors.capture();
                if (!captured.inQueue() && !engine.worldKey().equals(world.storageKey())) engine.requestStop(
                    ExitReason.WORLD_CHANGED, "Reconnected into another server or dimension", captured);
                else engine.tickCaptured(() -> captured, serverFacts::samplesValid);
            }
            else if (!engine.landingToStop() && !live.identity().equals(world.identity())) engine.requestStop(
                ExitReason.WORLD_CHANGED, "Live world or player changed", mcSensors.capture());
            else {
                String refusal = world.refusal(live.resources(), baritone.capture(), engine.outputs().ownBaritone() || engine.outputs().borrowedBaritone());
                if (refusal == null && !world.legacyRegear() && engine.outputs().foreignMovement()) refusal = "Another producer owns Baritone movement inputs";
                if (refusal != null && !engine.landingToStop()) engine.requestStop(
                    ExitReason.STOPPED, "Live refused: " + refusal, mcSensors.capture());
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
        lines.add(store.status());
        return List.copyOf(lines);
    }

    public List<String> why() {
        List<String> lines = new ArrayList<>(engine.why());
        lines.add(inventory.status());
        lines.add(jsonl.status());
        lines.add(store.status());
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
        engine.outputs().worldLost();
        engine.disconnected();
        if (isActive() && engine.snapshot().state() == RootState.STOPPED) disable();
    }

    public void worldJoined() { engine.joined(); }

    public void tickPost() {
        if (!isActive() || !world.available() || engine.suspended()) return;
        LiveWorldBot live = engine.liveBot();
        if (live == null || !live.identity().equals(world.identity())) return;
        if (world.legacyRegear()) { flight.cancel(); return; }
        engine.tickPost();
        if (engine.snapshot().state() == RootState.STOPPED) disable();
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
