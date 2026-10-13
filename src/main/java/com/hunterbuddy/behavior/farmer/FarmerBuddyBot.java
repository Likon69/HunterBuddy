package com.hunterbuddy.behavior.farmer;

import com.hunterbuddy.behavior.bot.Bot;
import com.hunterbuddy.behavior.bot.BotConfig;
import com.hunterbuddy.behavior.bot.BotContext;
import com.hunterbuddy.behavior.bot.BotStatus;
import com.hunterbuddy.behavior.bot.LiveWorldBot;
import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.ProgressPolicy;
import com.hunterbuddy.behavior.core.StopReport;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TargetPoint;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.hooks.Contributor;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.FarmerMissionContext;
import com.hunterbuddy.behavior.mc.InventoryLayout;
import com.hunterbuddy.behavior.mc.LogisticsContext;
import com.hunterbuddy.behavior.mc.LogisticsInventory;
import com.hunterbuddy.behavior.mc.ShulkerSignature;
import com.hunterbuddy.behavior.mc.WorldIdentity;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.mc.XaeroWaypoints;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Deferred;
import com.hunterbuddy.behavior.nodes.Fail;
import com.hunterbuddy.behavior.nodes.Guard;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.Succeed;
import com.hunterbuddy.behavior.nodes.UtilityOption;
import com.hunterbuddy.behavior.nodes.UtilitySelector;
import com.hunterbuddy.behavior.nodes.WaitForFarmerZone;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.behavior.routine.DeathPolicy;
import com.hunterbuddy.behavior.trees.FarmerBaseTree;
import com.hunterbuddy.behavior.trees.FarmerSiteTree;
import com.hunterbuddy.behavior.trees.FarmerSurveyTree;
import com.hunterbuddy.behavior.trees.GroundTravel;
import com.hunterbuddy.behavior.trees.RegearTree;
import com.hunterbuddy.behavior.trees.StoreTree;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import meteordevelopment.meteorclient.settings.SettingGroup;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

public final class FarmerBuddyBot implements Bot, LiveWorldBot {
    private final FarmerSettings settings;
    private final FarmerCatalog catalog;
    private final WorldReader world;
    private final BaritoneBridge baritone;
    private final Outputs outputs;
    private final MissionJournal journal;
    private final FarmerMissionContext mission;
    private WorldIdentity identity;
    private long mined;
    private String currentTarget = "idle";
    private String statusLine = "idle";
    private FarmerProfile activeProfile;
    private boolean regearAvailable = true;
    private Node mining;
    private Node base;
    private Node survey;
    private Node store;
    private Node regear;

    public FarmerBuddyBot(FarmerSettings settings, FarmerCatalog catalog, WorldReader world, BaritoneBridge baritone,
                          Outputs outputs, XaeroWaypoints waypoints) {
        this.settings = Objects.requireNonNull(settings);
        this.catalog = Objects.requireNonNull(catalog);
        this.world = Objects.requireNonNull(world);
        this.baritone = Objects.requireNonNull(baritone);
        this.outputs = Objects.requireNonNull(outputs);
        journal = new MissionJournal(FarmerSettings.MAX_STEPS);
        mission = new FarmerMissionContext(settings, catalog, world, baritone, outputs,
            new FarmerZoneLookup(Objects.requireNonNull(waypoints)), journal);
        identity = world.identity();
    }

    @Override
    public String name() {
        return "farmerbuddy";
    }

    @Override
    public String description() {
        return "FarmerBuddy: prospects, mines verified sites and combines delivery and resupply at base.";
    }

    @Override
    public void onRegister(SettingGroup settings) {
    }

    private void requireContext(BotContext context) {
        Objects.requireNonNull(context);
        if (!context.owner().equals("bot:" + name())) throw new IllegalArgumentException("wrong FarmerBuddyBot owner");
    }

    @Override
    public HookEntry taskEntry(BotContext context) {
        requireContext(context);
        Node work = new StatusTracker(new FarmerTracker(buildCycle(), value -> currentTarget = value));
        return new HookEntry(Hook.BOT, context.owner(), Contributor.BOT, 0,
            new Guard(new Condition("farmer-ready", sensors -> true), work), true, work);
    }

    @Override
    public List<HookEntry> extraEntries(BotContext context) {
        requireContext(context);
        Node recovery = new Deferred("retour-au-dernier-appui-par-Baritone", () -> {
            if (mission.tick == null || mission.unfinishedMine() || mission.fieldActive() || mission.baseActive()) {
                return new Succeed();
            }
            if (!mission.tick.sensors().onGround()) {
                return new Fail(FailureCode.UNREACHABLE, "retour au dernier appui différé jusqu'au repli de mission");
            }
            TargetPoint last = mission.tick.bb().get(context.owner(), FarmerTracker.LAST_SAFE).orElse(null);
            BlockPos destination = last == null ? world.feet() : BlockPos.ofFloored(last.x(), last.y(), last.z());
            return GroundTravel.build(destination, 2, baritone, outputs);
        });
        Guard guard = new Guard(new Condition("farmer-recovery", sensors -> true), recovery);
        Node change = new InventoryMilestone("demander-un-autre-site-après-échec-de-reprise", ctx -> {
            mission.tick = ctx;
            if (mission.facts == null) return;
            mission.facts.changeRequested = true;
            mission.facts.changeReason = "site inaccessible après reprise du dernier appui";
            if (ctx.sensors().gliding()) mission.facts.baseRequested = true;
            mission.save(ctx, mission.facts.changeReason);
        });
        Guard changeGuard = new Guard(new Condition("farmer-change-site-recovery", sensors -> true), change);
        return List.of(new HookEntry(Hook.RECOVERY, context.owner(), Contributor.BOT, 0, guard, true, recovery, 2),
            new HookEntry(Hook.RECOVERY, context.owner(), Contributor.BOT, 0, changeGuard, true, change, 3));
    }

    @Override
    public BotConfig config() {
        return new BotConfig(false, DeathPolicy.STOP, true, ProgressPolicy.DEFAULT, 1_200);
    }

    @Override
    public void onStart(BotContext context) {
        requireContext(context);
        identity = world.identity();
        outputs.flight().requestPilot(context.owner());
    }

    @Override
    public void onResume(BotContext context) {
        requireContext(context);
        identity = world.identity();
        outputs.flight().requestPilot(context.owner());
    }

    @Override
    public void onStop(StopReport report) {
        Objects.requireNonNull(report);
        outputs.flight().releasePilot("bot:" + name());
    }

    @Override
    public BotStatus status() {
        return new BotStatus("farmerbuddy", statusLine, mined);
    }

    @Override
    public WorldIdentity identity() {
        return identity;
    }

    @Override
    public Set<Resource> resources() {
        return Set.of(Resource.MOVEMENT, Resource.MINING, Resource.INVENTORY, Resource.INTERACT,
            Resource.BARITONE, Resource.ROCKETS);
    }

    private List<FarmerStepConfig> enabledSteps() {
        return settings.steps().stream().filter(FarmerStepConfig::enabled).toList();
    }

    private FarmerStepConfig currentStep(TickContext ctx) {
        int stored = journal.currentStep(ctx);
        return enabledSteps().stream().filter(step -> step.index() >= stored).findFirst().orElse(null);
    }

    private Node buildCycle() {
        mining = new Deferred("miner-le-site-vérifié", () -> FarmerSiteTree.mine(mission, value -> mined += value)).resumable();
        base = new Deferred("visite-unique-de-base", () -> FarmerBaseTree.build(mission)).resumable();
        survey = new Deferred("prospecter-la-zone", () -> FarmerSurveyTree.build(mission)).resumable();
        store = new Deferred("ranger-et-regear-sous-la-même-protection", () -> {
            LogisticsContext logistics = new LogisticsContext(() -> activeProfile, () -> mission.zone, true,
                () -> regearAvailable && LogisticsInventory.needsRegear(world, activeProfile), () -> false,
                () -> mission.step.index() - 1, world, baritone, outputs, journal);
            logistics.evacuationRequested = () -> mission.players() || mission.facts.changeRequested || mission.fuelDue();
            return StoreTree.build(logistics);
        }).resumable();
        regear = new Deferred("regear-dans-la-zone", () -> RegearTree.build(() -> activeProfile,
            () -> mission.zone, () -> mission.step.index() - 1, world, baritone, outputs, journal)).resumable();
        return new Sequence("cerveau-de-mission",
            new ResolveProfile(),
            new UtilitySelector("farmer-task",
                new UtilityOption(store, this::storeScore, ctx -> "ranger"),
                new UtilityOption(regear, this::regearScore, ctx -> "regear"),
                new UtilityOption(base, this::baseScore, ctx -> "base"),
                new UtilityOption(new Deferred("changer-de-site", () -> FarmerSiteTree.change(mission)),
                    this::changeScore, ctx -> "changer de site"),
                new UtilityOption(survey, this::surveyScore, ctx -> "prospecter"),
                new UtilityOption(mining, this::mineScore, ctx -> "miner site=" + mission.facts.site),
                new UtilityOption(new WaitForFarmerZone(mission), this::waitScore, ctx -> "attendre à la base"),
                new UtilityOption(new NextStep(), this::nextStepScore, ctx -> "next-step")));
    }

    private boolean busy() {
        return mission.unfinishedMine() || mission.fieldActive() || mission.baseActive()
            || mining.active() || store.active() || regear.active() || base.active() || survey.active();
    }

    private double mineScore(TickContext ctx) {
        if (mining.active() || mission.unfinishedMine()) return 4;
        if (busy() || mission.facts.waitUntil > 0 || mission.facts.baseRequested || mission.facts.changeRequested) return 0;
        if (mission.selected == null || !mission.selected.usable() || !mission.eligible(mission.selected)) return 0;
        if (mission.siteOccupied() || mission.fuelDue() || mission.freeSlots() <= 2
            || world.countMatching(LogisticsInventory.accepted(activeProfile)) >= MaterialSite.SHULKER_BLOCKS
            || LogisticsInventory.needsRegear(world, activeProfile)) return 0;
        return delivered(ctx, mission.step) < mission.step.shulkerTarget() ? 0.5 : 0;
    }

    private double storeScore(TickContext ctx) {
        if (store.active()) return 5;
        if (mission.fieldActive()) return new LogisticsMemory(journal, ctx.owner()).load(ctx).store ? 5 : 0;
        if (busy() || mission.facts.waitUntil > 0 || mission.storageUnavailable() || mission.players()) return 0;
        int objects = world.countMatching(LogisticsInventory.accepted(activeProfile))
            + world.countMatching(LogisticsInventory.miscellaneous(activeProfile));
        if (objects == 0) return 0;
        if (mission.facts.changeRequested && mission.capacity() == 0) return 0;
        if (mission.mineFacts() != null && mission.mineFacts().exited
            && mission.mineFacts().outcome.startsWith("rangement nécessaire")) return 1;
        return world.countMatching(LogisticsInventory.accepted(activeProfile)) >= MaterialSite.SHULKER_BLOCKS
            || mission.freeSlots() <= 2 ? 1 : 0;
    }

    private double regearScore(TickContext ctx) {
        if (regear.active()) return 5;
        if (mission.fieldActive()) return new LogisticsMemory(journal, ctx.owner()).load(ctx).store ? 0 : 5;
        if (busy() || mission.facts.waitUntil > 0 || mission.players() || mission.facts.changeRequested) return 0;
        return regearAvailable && LogisticsInventory.needsRegear(world, activeProfile) ? 0.9 : 0;
    }

    private double baseScore(TickContext ctx) {
        if (base.active() || mission.baseActive()) return 5;
        if (busy() || mission.facts.waitUntil > 0) return 0;
        long remaining = mission.step.shulkerTarget() - delivered(ctx, mission.step);
        if (remaining <= 0) return 0;
        if (mission.facts.baseRequested || mission.fuelDue()
            || !regearAvailable && LogisticsInventory.needsRegear(world, activeProfile)) return 1.2;
        if (mission.players() && (mission.freeSlots() <= 2
            || world.countMatching(LogisticsInventory.accepted(activeProfile)) >= MaterialSite.SHULKER_BLOCKS
            || LogisticsInventory.needsRegear(world, activeProfile))) return 1.2;
        if (mission.stock().isEmpty() || mission.storageUnavailable()) return 1.1;
        long carried = journal.carried(ctx, mission.step.index() - 1);
        return mission.capacity() == 0 || carried >= remaining ? 1.1 : 0;
    }

    private double changeScore(TickContext ctx) {
        if (busy() || mission.facts.waitUntil > 0 || mission.facts.baseRequested) return 0;
        if (mission.facts.changeRequested || mission.siteOccupied()
            || mission.selected != null && (!mission.selected.usable() || !mission.eligible(mission.selected))) return 0.85;
        return mission.selected == null && (mission.best() != null || mission.occupiedOnly()) ? 0.75 : 0;
    }

    private double surveyScore(TickContext ctx) {
        if (survey.active()) return 4;
        if (busy() || mission.facts.waitUntil > 0 || mission.facts.baseRequested || mission.facts.changeRequested) return 0;
        return mission.selected == null && mission.best() == null && !mission.occupiedOnly() ? 0.6 : 0;
    }

    private double waitScore(TickContext ctx) {
        return !busy() && mission.facts.waitUntil > 0 ? 3 : 0;
    }

    private double nextStepScore(TickContext ctx) {
        FarmerStepConfig step = currentStep(ctx);
        if (step == null || busy()) return 0;
        return delivered(ctx, step) >= step.shulkerTarget() ? 3.5 : 0;
    }

    private long delivered(TickContext ctx, FarmerStepConfig step) {
        return journal.delivered(ctx, step.index() - 1);
    }

    private void updateStatus(TickContext ctx) {
        FarmerStepConfig step = currentStep(ctx);
        if (step == null) {
            statusLine = "no enabled step";
            return;
        }
        statusLine = String.format(Locale.ROOT,
            "step=%d name=%s profile=%s delivered=%d/%d carried=%d activity=%s rockets=%d elytra=%.0f%%",
            step.index(), step.name(), step.profile(), delivered(ctx, step), step.shulkerTarget(),
            journal.carried(ctx, step.index() - 1), currentTarget, ctx.sensors().rockets(), world.elytraDurabilityFraction() * 100);
    }

    private final class StatusTracker extends Decorator {
        private StatusTracker(Node child) {
            super("farmer-status", child);
        }

        @Override
        protected NodeResult onTick(TickContext ctx) {
            mission.tick = ctx;
            outputs.flight().requestPilot(ctx.owner());
            if (mission.facts != null && mission.zone != null) mission.avoidVisiblePlayers(ctx);
            NodeResult result = child().execute(ctx);
            updateStatus(ctx);
            return result;
        }
    }

    private final class ResolveProfile extends Node {
        private ResolveProfile() {
            super("resolve-profile");
        }

        @Override
        protected NodeResult onTick(TickContext ctx) {
            FarmerStepConfig step = currentStep(ctx);
            if (step == null) return fail(FailureCode.NO_OPTION, "no enabled farmer step");
            Optional<FarmerProfile> profile = catalog.profile(step.profile());
            if (profile.isEmpty()) return fail(FailureCode.REFUSED, "unknown catalog profile: " + step.profile());
            activeProfile = profile.get();
            try {
                mission.resolve(ctx, step, activeProfile);
                String reserve = journal.equipment(ctx, "reserve-status");
                regearAvailable = reserve.isEmpty() || reserve.equals("ready");
                String signature = journal.equipment(ctx, "logistics-reserve");
                if (!signature.isEmpty()) {
                    List<String> missing = LogisticsInventory.missing(world, activeProfile,
                        ShulkerSignature.decode(signature, world).contents());
                    regearAvailable = regearAvailable && missing.isEmpty();
                }
                String refusal = outputs.mining().temporaryRefusal();
                if (refusal == null && world.countMatching(InventoryLayout::silkPickaxe) == 0) {
                    refusal = "pioche Toucher de soie absente de l'inventaire";
                }
                if (refusal == null && world.count(Items.ENDER_CHEST) == 0 && !mission.fieldActive() && !mission.baseActive()) {
                    refusal = "ender chest absent de l'inventaire";
                }
                if (refusal != null) throw new IllegalStateException(refusal);
                return Success.INSTANCE;
            } catch (IllegalStateException ex) {
                ctx.stopBot(ExitReason.FAILED, ex.getMessage());
                return fail(FailureCode.REFUSED, ex.getMessage());
            }
        }
    }

    private final class NextStep extends Node {
        private NextStep() {
            super("farmer-next-step");
        }

        @Override
        protected NodeResult onTick(TickContext ctx) {
            FarmerStepConfig step = currentStep(ctx);
            if (step == null) return fail(FailureCode.NO_OPTION, "no enabled farmer step");
            journal.milestone(ctx, "step-" + step.index() + "-complete");
            FarmerStepConfig next = enabledSteps().stream().filter(candidate -> candidate.index() > step.index()).findFirst().orElse(null);
            if (next == null) {
                ctx.stopBot(ExitReason.COMPLETED, "FarmerBuddy mission complete: step " + step.index() + " was the last one");
                return Success.INSTANCE;
            }
            journal.currentStep(ctx, next.index());
            return Success.INSTANCE;
        }
    }

}
