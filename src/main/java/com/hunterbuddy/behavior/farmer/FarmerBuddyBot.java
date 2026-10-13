package com.hunterbuddy.behavior.farmer;

import com.hunterbuddy.behavior.bot.Bot;
import com.hunterbuddy.behavior.bot.BotConfig;
import com.hunterbuddy.behavior.bot.BotContext;
import com.hunterbuddy.behavior.bot.BotStatus;
import com.hunterbuddy.behavior.bot.LiveWorldBot;
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
import com.hunterbuddy.behavior.mc.InventoryBridge;
import com.hunterbuddy.behavior.mc.WorldIdentity;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.nodes.Budget;
import com.hunterbuddy.behavior.nodes.BreakBlock;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.ForceSuccess;
import com.hunterbuddy.behavior.nodes.Guard;
import com.hunterbuddy.behavior.nodes.SetValue;
import com.hunterbuddy.behavior.nodes.Sequence;
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
import com.hunterbuddy.behavior.trees.OpenChestTree;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import meteordevelopment.meteorclient.settings.SettingGroup;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

public final class FarmerBuddyBot implements Bot, LiveWorldBot {
    private static final Key<BlockPos> ORIGIN = new Key<>("farmer-origin", BlockPos.class, Scope.OWNER, false);
    private static final Key<BlockScan> SCAN = new Key<>("farmer-scan", BlockScan.class, Scope.OWNER, false);
    private static final Key<BlockPos> TARGET_POS = new Key<>("farmer-target-pos", BlockPos.class, Scope.OWNER, false);
    private static final Key<TargetPoint> TARGET_POINT = new Key<>("farmer-target-point", TargetPoint.class, Scope.OWNER, false);
    private static final Key<TargetPoint> START = new Key<>("farmer-start", TargetPoint.class, Scope.OWNER, false);
    private final FarmerSettings settings;
    private final WorldReader world;
    private final BaritoneBridge baritone;
    private final Outputs outputs;
    private WorldIdentity identity;
    private long mined;
    private long deposited;
    private String currentTarget = "idle";

    public FarmerBuddyBot(FarmerSettings settings, WorldReader world, BaritoneBridge baritone, Outputs outputs) {
        this.settings = Objects.requireNonNull(settings);
        this.world = Objects.requireNonNull(world);
        this.baritone = Objects.requireNonNull(baritone);
        this.outputs = Objects.requireNonNull(outputs);
        identity = world.identity();
    }

    @Override
    public String name() {
        return "farmerbuddy";
    }

    @Override
    public String description() {
        return "Ground block farmer: mine the nearest matching block, deposit at the chest, return to the start point.";
    }

    @Override
    public void onRegister(SettingGroup settings) {
        this.settings.register(settings);
    }

    private void requireContext(BotContext context) {
        Objects.requireNonNull(context);
        if (!context.owner().equals("bot:" + name())) throw new IllegalArgumentException("wrong FarmerBuddyBot owner");
    }

    @Override
    public HookEntry taskEntry(BotContext context) {
        requireContext(context);
        Node work = new FarmerTracker(buildCycle(), value -> currentTarget = value);
        return new HookEntry(Hook.BOT, context.owner(), Contributor.BOT, 0,
            new Guard(new Condition("farmer-ready", sensors -> true), work), false, work);
    }

    @Override
    public List<HookEntry> extraEntries(BotContext context) {
        requireContext(context);
        Node levelTwo = GoToTree.build(FarmerTracker.LAST_SAFE, 2, world, baritone, outputs);
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
    }

    @Override
    public void onStop(StopReport report) {
        Objects.requireNonNull(report);
    }

    @Override
    public BotStatus status() {
        return new BotStatus("farmerbuddy", currentTarget + " mined=" + mined + " deposited=" + deposited, mined);
    }

    @Override
    public WorldIdentity identity() {
        return identity;
    }

    @Override
    public Set<Resource> resources() {
        return Set.of(Resource.MOVEMENT, Resource.MINING, Resource.INVENTORY);
    }

    private Node buildCycle() {
        Predicate<BlockState> mineFilter = state -> !state.isAir() && settings.blocks().contains(state.getBlock());
        Predicate<ItemStack> depositFilter = stack -> !stack.isEmpty() && stack.getItem() instanceof BlockItem item
            && settings.blocks().contains(item.getBlock());
        InventoryBridge inventory = outputs.inventory();

        Node mineCycle = new Sequence("farmer-mine",
            new SetValue<>(TARGET_POS, ctx -> parsePos(ctx.bb().get(ctx.owner(), TickContext.CURRENT_TARGET).orElseThrow())),
            new SetValue<>(TARGET_POINT, ctx -> center(ctx.bb().get(ctx.owner(), TARGET_POS).orElseThrow())),
            GoToTree.build(TARGET_POINT, 3, world, baritone, outputs),
            new Budget("farmer-mine", 10, new Timeout(300, new WithLease(Set.of(Resource.MINING), LeasePolicy.WAIT,
                new BreakBlock(TARGET_POS, false, world, outputs))))
                .keyedBy(ctx -> ctx.bb().get(ctx.owner(), TickContext.CURRENT_TARGET).orElse("none")),
            new Count(() -> mined++));

        Function<TickContext, List<BlockPos>> candidates = ctx -> {
            BlockScan scan = ctx.bb().get(ctx.owner(), SCAN).orElse(null);
            if (scan == null || scan.blocks().isEmpty()) return List.of();
            TargetPoint here = new TargetPoint(ctx.sensors().x(), ctx.sensors().y(), ctx.sensors().z());
            return scan.blocks().stream()
                .sorted(Comparator.comparingDouble(pos -> here.distance(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)))
                .toList();
        };
        ToDoubleFunction<BlockPos> candidateScore = pos -> 1;
        Function<BlockPos, String> id = pos -> pos.getX() + "," + pos.getY() + "," + pos.getZ();

        Node depositCycle = new ForceSuccess(new Sequence("farmer-deposit",
            OpenChestTree.build(settings.chest(), world, baritone, outputs, () -> inventory.firstDeposit(depositFilter)),
            new Count(() -> deposited++)));

        BlockPos start = settings.startPoint();
        TargetPoint startPoint = new TargetPoint(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        Node returnCycle = new ForceSuccess(new Sequence("farmer-return",
            new SetValue<>(START, ctx -> startPoint),
            GoToTree.build(START, startPoint, 3, world, baritone, outputs)));

        Node task = new UtilitySelector("farmer-task",
            UtilityOption.withCandidates(mineCycle, ctx -> world.countMatching(depositFilter) >= settings.threshold() ? 0 : 0.8,
                candidates, candidateScore, id),
            new UtilityOption(depositCycle, ctx -> world.countMatching(depositFilter) >= settings.threshold() ? 0.9 : 0, ctx -> "deposit"),
            new UtilityOption(returnCycle, ctx -> 0.3, ctx -> "return"));

        return new Sequence("farmer-scan",
            new SetValue<>(ORIGIN, ctx -> new BlockPos(ctx.sensors().feetX(), ctx.sensors().feetY() - 1, ctx.sensors().feetZ())),
            new Timeout(2_400, new FindBlocks(ORIGIN, settings.radius(), 4, mineFilter, SCAN, world)),
            task);
    }

    private static BlockPos parsePos(String id) {
        String[] parts = id.split(",");
        return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    private static TargetPoint center(BlockPos pos) {
        return new TargetPoint(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
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
}
