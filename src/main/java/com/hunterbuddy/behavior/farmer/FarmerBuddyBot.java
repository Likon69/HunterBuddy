package com.hunterbuddy.behavior.farmer;

import com.hunterbuddy.behavior.bot.Bot;
import com.hunterbuddy.behavior.bot.BotConfig;
import com.hunterbuddy.behavior.bot.BotContext;
import com.hunterbuddy.behavior.bot.BotStatus;
import com.hunterbuddy.behavior.bot.LiveWorldBot;
import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.ProgressPolicy;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.StopReport;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TargetPoint;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.hooks.Contributor;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.InventoryLayout;
import com.hunterbuddy.behavior.mc.LogisticsInventory;
import com.hunterbuddy.behavior.mc.ProtectionRegistry;
import com.hunterbuddy.behavior.mc.ShulkerSignature;
import com.hunterbuddy.behavior.mc.WorldIdentity;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.mc.XaeroWaypoints;
import com.hunterbuddy.behavior.nodes.BreakBlock;
import com.hunterbuddy.behavior.nodes.Budget;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Guard;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.SetValue;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.UtilityOption;
import com.hunterbuddy.behavior.nodes.UtilitySelector;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.probes.BlockScan;
import com.hunterbuddy.behavior.probes.FindBlocks;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.behavior.routine.DeathPolicy;
import com.hunterbuddy.behavior.trees.GoToTree;
import com.hunterbuddy.behavior.trees.RegearTree;
import com.hunterbuddy.behavior.trees.StoreTree;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import meteordevelopment.meteorclient.settings.SettingGroup;
import net.minecraft.block.BlockState;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

public final class FarmerBuddyBot implements Bot, LiveWorldBot {
    private static final int LOCAL_SCAN_RADIUS = 8;
    private static final Key<BlockPos> ORIGIN = new Key<>("farmer-origin", BlockPos.class, Scope.OWNER, false);
    private static final Key<BlockScan> SCAN = new Key<>("farmer-scan", BlockScan.class, Scope.OWNER, false);
    private static final Key<BlockPos> TARGET_POS = new Key<>("farmer-target-pos", BlockPos.class, Scope.OWNER, false);
    private static final Key<TargetPoint> TARGET_POINT = new Key<>("farmer-target-point", TargetPoint.class, Scope.OWNER, false);
    private final FarmerSettings settings;
    private final FarmerCatalog catalog;
    private final WorldReader world;
    private final BaritoneBridge baritone;
    private final Outputs outputs;
    private final FarmerZoneLookup zoneLookup;
    private final MissionJournal journal;
    private WorldIdentity identity;
    private long mined;
    private String currentTarget = "idle";
    private String statusLine = "idle";
    private FarmerProfile activeProfile;
    private FarmerZone miningZone;
    private boolean regearAvailable = true;

    public FarmerBuddyBot(FarmerSettings settings, FarmerCatalog catalog, WorldReader world, BaritoneBridge baritone,
                          Outputs outputs, XaeroWaypoints waypoints) {
        this.settings = Objects.requireNonNull(settings);
        this.catalog = Objects.requireNonNull(catalog);
        this.world = Objects.requireNonNull(world);
        this.baritone = Objects.requireNonNull(baritone);
        this.outputs = Objects.requireNonNull(outputs);
        this.zoneLookup = new FarmerZoneLookup(Objects.requireNonNull(waypoints));
        this.journal = new MissionJournal(FarmerSettings.MAX_STEPS);
        identity = world.identity();
    }

    @Override
    public String name() {
        return "farmerbuddy";
    }

    @Override
    public String description() {
        return "Zone block farmer: mines the current step's profile inside its Xaero zone and delivers full shulkers.";
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
            new Guard(new Condition("farmer-ready", sensors -> true), work), false, work);
    }

    @Override
    public List<HookEntry> extraEntries(BotContext context) {
        requireContext(context);
        Node levelTwo = GoToTree.build(FarmerTracker.LAST_SAFE, 2, world, baritone, outputs, this::allowBreak);
        Guard guard = new Guard(new Condition("farmer-recovery", sensors -> true), levelTwo);
        return List.of(new HookEntry(Hook.RECOVERY, context.owner(), Contributor.BOT, 0, guard, false, levelTwo, 2));
    }

    @Override
    public BotConfig config() {
        return new BotConfig(false, DeathPolicy.STOP, true, ProgressPolicy.DEFAULT, 1_200);
    }

    @Override
    public void onStart(BotContext context) {
        requireContext(context);
        identity = world.identity();
    }

    @Override
    public void onResume(BotContext context) {
        requireContext(context);
        identity = world.identity();
        outputs.mining().breakRule(pos -> miningZone != null && miningZone.contains(pos.getX() + 0.5, pos.getZ() + 0.5));
    }

    @Override
    public void onStop(StopReport report) {
        Objects.requireNonNull(report);
        outputs.mining().breakRule(null);
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
        return Set.of(Resource.MOVEMENT, Resource.MINING, Resource.INVENTORY, Resource.INTERACT);
    }

    private List<FarmerStepConfig> enabledSteps() {
        return settings.steps().stream().filter(FarmerStepConfig::enabled).toList();
    }

    private FarmerStepConfig currentStep(TickContext ctx) {
        int stored = journal.currentStep(ctx);
        return enabledSteps().stream().filter(step -> step.index() >= stored).findFirst().orElse(null);
    }

    private boolean allowBreak(TickContext ctx, TargetPoint point) {
        FarmerStepConfig step = currentStep(ctx);
        if (step == null) return false;
        FarmerZoneLookup.Resolved resolved = zoneLookup.resolve(step.name());
        if (!resolved.ok()) return false;
        FarmerZone zone = resolved.zone();
        return zone.contains(ctx.sensors().x(), ctx.sensors().z()) && zone.contains(point.x(), point.z());
    }

    private Node buildCycle() {
        Node resolveProfile = new ResolveProfile();
        Predicate<BlockState> mineFilter = state -> !state.isAir() && activeProfile != null &&
            activeProfile.blocks().contains(state.getBlock());

        Node mineCycle = new Sequence("farmer-mine",
            new ParseTarget(),
            GoToTree.build(TARGET_POINT, 3, world, baritone, outputs, this::allowBreak),
            new Budget("farmer-mine", 10, new Timeout(300, new WithLease(Set.of(Resource.MINING), LeasePolicy.WAIT,
                new BreakBlock(TARGET_POS, false, world, outputs))))
                .keyedBy(ctx -> ctx.bb().get(ctx.owner(), TickContext.CURRENT_TARGET).orElse("none")),
            new Count(() -> mined++));

        Function<TickContext, List<BlockPos>> candidates = ctx -> {
            BlockScan scan = ctx.bb().get(ctx.owner(), SCAN).orElse(null);
            if (scan == null || scan.blocks().isEmpty()) return List.of();
            FarmerStepConfig step = currentStep(ctx);
            if (step == null) return List.of();
            FarmerZoneLookup.Resolved resolved = zoneLookup.resolve(step.name());
            if (!resolved.ok()) return List.of();
            FarmerZone zone = resolved.zone();
            TargetPoint here = new TargetPoint(ctx.sensors().x(), ctx.sensors().y(), ctx.sensors().z());
            return scan.blocks().stream()
                .filter(pos -> zone.containsWithMargin(pos.getX() + 0.5, pos.getZ() + 0.5, FarmerZoneLookup.TARGET_MARGIN))
                .sorted(Comparator.comparingDouble(pos -> here.distance(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)))
                .toList();
        };
        ToDoubleFunction<BlockPos> candidateScore = pos -> 1;
        Function<BlockPos, String> id = pos -> pos.getX() + "," + pos.getY() + "," + pos.getZ();

        Node nextStepCycle = new NextStep();

        Node task = new UtilitySelector("farmer-task",
            new UtilityOption(StoreTree.build(() -> activeProfile, this::logisticsZone, () -> activeProfile != null && regearAvailable
                && LogisticsInventory.needsRegear(world, activeProfile),
                () -> activeStepIndex, world, baritone, outputs, journal), this::storeScore, ctx -> "ranger"),
            new UtilityOption(RegearTree.build(() -> activeProfile, this::logisticsZone, () -> activeStepIndex, world, baritone,
                outputs, journal), this::regearScore, ctx -> "regear"),
            UtilityOption.withCandidates(mineCycle, this::mineScore, candidates, candidateScore, id),
            new UtilityOption(nextStepCycle, this::nextStepScore, ctx -> "next-step"));

        return new Sequence("farmer-scan",
            resolveProfile,
            new SetValue<>(ORIGIN, ctx -> new BlockPos(ctx.sensors().feetX(), ctx.sensors().feetY() - 1, ctx.sensors().feetZ())),
            new Timeout(2_400, new FindBlocks(ORIGIN, LOCAL_SCAN_RADIUS, 4, mineFilter, SCAN, world)),
            task);
    }

    private double mineScore(TickContext ctx) {
        if (LogisticsMemory.active(ctx, journal)) return 0;
        int free = 0;
        for (int slot = 0; slot < 36; slot++) if (world.client().player.getInventory().getStack(slot).isEmpty()) free++;
        if (free <= 2) return 0;
        if (activeProfile != null && LogisticsInventory.profileTools(world, activeProfile) == 0) return 0;
        FarmerStepConfig step = currentStep(ctx);
        if (step == null) return 0;
        return delivered(ctx, step) >= step.shulkerTarget() ? 0 : 0.5;
    }

    private int activeStepIndex;
    private FarmerZone logisticsZone() {
        FarmerStepConfig step = enabledSteps().stream().filter(candidate -> candidate.index() - 1 ==
            activeStepIndex).findFirst().orElseThrow();
        FarmerZoneLookup.Resolved resolved = zoneLookup.resolve(step.name());
        if (!resolved.ok()) throw new IllegalStateException(resolved.refusal());
        return resolved.zone();
    }

    private double storeScore(TickContext ctx) {
        if (LogisticsMemory.active(ctx, journal)) return new LogisticsMemory(journal, ctx.owner()).load(ctx).store ? 2 : 0;
        if (activeProfile == null) return 0;
        LogisticsFacts previous = new LogisticsMemory(journal, ctx.owner()).load(ctx);
        if (previous != null && previous.departure != null && previous.profile.equals(activeProfile.key())
            && (!previous.material.unavailable.isEmpty() || !previous.divers.unavailable.isEmpty())) return 0;
        if (world.countMatching(LogisticsInventory.accepted(activeProfile)) +
            world.countMatching(LogisticsInventory.miscellaneous(activeProfile)) == 0) return 0;
        int free = 0;
        for (int slot = 0; slot < 36; slot++) if (world.client().player.getInventory().getStack(slot).isEmpty()) free++;
        return world.countMatching(LogisticsInventory.accepted(activeProfile)) >= 27 * 64 || free <= 2 ? 1 : 0;
    }

    private double regearScore(TickContext ctx) {
        if (LogisticsMemory.active(ctx, journal)) return new LogisticsMemory(journal, ctx.owner()).load(ctx).store ? 0 : 2;
        String reserve = journal.equipment(ctx, "reserve-status");
        return activeProfile != null && LogisticsInventory.needsRegear(world, activeProfile) && (reserve.isEmpty() ||
            reserve.equals("ready")) ? 0.9 : 0;
    }

    private double nextStepScore(TickContext ctx) {
        FarmerStepConfig step = currentStep(ctx);
        if (step == null) return 0;
        return delivered(ctx, step) >= step.shulkerTarget() ? 1.0 : 0;
    }

    private long delivered(TickContext ctx, FarmerStepConfig step) {
        return journal.delivered(ctx, step.index() - 1);
    }

    private static BlockPos parsePos(String id) {
        String[] parts = id.split(",");
        if (parts.length != 3) return null;
        try {
            return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static TargetPoint center(BlockPos pos) {
        return new TargetPoint(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    private void updateStatus(TickContext ctx) {
        FarmerStepConfig step = currentStep(ctx);
        if (step == null) {
            statusLine = "no enabled step";
            return;
        }
        long delivered = journal.delivered(ctx, step.index() - 1);
        long carried = journal.carried(ctx, step.index() - 1);
        statusLine = String.format(Locale.ROOT,
            "step=%d name=%s profile=%s delivered=%d/%d carried=%d activity=%s rockets=%d elytra=%.0f%%",
            step.index(), step.name(), step.profile(), delivered, step.shulkerTarget(), carried, currentTarget,
            ctx.sensors().rockets(), world.elytraDurabilityFraction() * 100);
    }

    private final class StatusTracker extends Decorator {
        private StatusTracker(Node child) {
            super("farmer-status", child);
        }

        @Override
        protected NodeResult onTick(TickContext ctx) {
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
            FarmerZoneLookup.Resolved zone = zoneLookup.resolve(step.name());
            miningZone = zone.ok() ? zone.zone() : null;
            outputs.mining().breakRule(pos -> miningZone != null && miningZone.contains(pos.getX() + 0.5, pos.getZ() + 0.5));
            String reserve = journal.equipment(ctx, "reserve-status");
            regearAvailable = reserve.isEmpty() || reserve.equals("ready");
            String reserveSignature = journal.equipment(ctx, "logistics-reserve");
            if (!reserveSignature.isEmpty()) {
                List<String> missing = LogisticsInventory.missing(world, activeProfile, ShulkerSignature.decode(reserveSignature,
                    world).contents());
                regearAvailable = regearAvailable && missing.isEmpty();
                if (!missing.isEmpty()) {
                    String detail = "réserve incomplète: " + String.join(", ", missing);
                    if (!detail.equals(journal.equipment(ctx, "reserve-status"))) {
                        journal.equipment(ctx, "reserve-status", detail);
                        journal.milestone(ctx, detail);
                    }
                }
            }
            ProtectionRegistry.restore(ctx, journal, outputs.mining());
            activeStepIndex = step.index() - 1;
            String refusal = outputs.mining().temporaryRefusal();
            if (refusal == null && world.countMatching(InventoryLayout::silkPickaxe) == 0) refusal =
                "pioche Toucher de soie absente de l'inventaire";
            if (refusal == null && world.count(Items.ENDER_CHEST) == 0 && !LogisticsMemory.active(ctx, journal)) refusal =
                "ender chest absent de l'inventaire";
            if (refusal != null) {
                ctx.stopBot(ExitReason.FAILED, refusal);
                return fail(FailureCode.MISSING_ITEM, refusal);
            }
            return Success.INSTANCE;
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

    private static final class Count extends Node {
        private final Runnable action;

        private Count(Runnable action) {
            super("count");
            this.action = Objects.requireNonNull(action);
        }

        @Override
        protected NodeResult onTick(TickContext ctx) {
            action.run();
            return Success.INSTANCE;
        }
    }

    private static final class ParseTarget extends Node {
        private ParseTarget() {
            super("parse-target");
        }

        @Override
        protected NodeResult onTick(TickContext ctx) {
            String id = ctx.bb().get(ctx.owner(), TickContext.CURRENT_TARGET).orElse(null);
            BlockPos pos = id == null ? null : parsePos(id);
            if (pos == null) return fail(FailureCode.NO_OPTION, "mine target is missing");
            ctx.bb().set(ctx.owner(), TARGET_POS, pos);
            ctx.bb().set(ctx.owner(), TARGET_POINT, center(pos));
            return Success.INSTANCE;
        }
    }
}
