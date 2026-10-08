package com.hunterbuddy.modules.sorter;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalGetToBlock;
import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.util.BaritoneHelper;
import com.hunterbuddy.modules.regear.util.RotationUtils;
import com.hunterbuddy.modules.sorter.logic.CategoryRules;
import com.hunterbuddy.modules.sorter.logic.PurificationPlan;
import com.hunterbuddy.modules.sorter.logic.ShulkerClassifier;
import com.hunterbuddy.modules.sorter.model.SorterProfile;
import com.hunterbuddy.modules.sorter.model.SorterProfile.Destination;
import com.hunterbuddy.modules.sorter.model.SorterProfile.Pos;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.packets.InventoryEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class ShulkerSorter extends Module {
    private final SettingGroup sgSort = settings.createGroup("Sorting");
    private final SettingGroup sgSpeed = settings.createGroup("Speed / anticheat");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SorterSetup setup = new SorterSetup(this);

    private final Setting<Keybind> tagKey = sgSort.add(new KeybindSetting.Builder()
        .name("tag-key")
        .description("Register the aimed storage block while the sorter is off.")
        .defaultValue(Keybind.none())
        .action(setup::tag)
        .build());

    public final Setting<Double> purity = sgSort.add(new DoubleSetting.Builder()
        .name("purity")
        .description("Share of a shulker's slots its top category needs. Below that the shulker is 'mixed'.")
        .defaultValue(0.8).min(0.5).max(1.0).sliderRange(0.5, 1.0).decimalPlaces(2)
        .build());

    private final Setting<Boolean> sortLoose = sgSort.add(new BoolSetting.Builder()
        .name("sort-loose-items")
        .description("Take loose items from inputs too. Loose cargo from station jobs is always routed.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> fallbackRandom = sgSort.add(new BoolSetting.Builder()
        .name("fallback-to-random")
        .description("A category with no destination goes to the 'random' destination instead of staying put.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> useHotbar = sgSort.add(new BoolSetting.Builder()
        .name("use-hotbar")
        .description("Allow cargo in empty hotbar slots too.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> purifyMixed = sgSort.add(new BoolSetting.Builder()
        .name("purify-mixed")
        .description("Keep the dominant category in mixed shulkers at the configured station.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> packShulkers = sgSort.add(new BoolSetting.Builder()
        .name("pack-shulkers")
        .description("Fill existing pure partial shulkers with loose items at the station after sorting.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> dryRun = sgSort.add(new BoolSetting.Builder()
        .name("dry-run")
        .description("Report input routing without transfers. Read once at activation; changes apply to the next run. Withdrawals always move items.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> autoStart = sgSort.add(new BoolSetting.Builder()
        .name("auto-start")
        .description("Start after you close a registered input containing items, with dry-run off.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> resumeAfterReconnect = sgSort.add(new BoolSetting.Builder()
        .name("resume-after-reconnect")
        .description("Resume an interrupted run near an input after five seconds clear of safety pauses.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> ignoreFriends = sgSort.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Do not pause when a visible player is a friend.")
        .defaultValue(false)
        .build());

    private final Setting<Integer> clicksPerTick = sgSpeed.add(new IntSetting.Builder()
        .name("clicks-per-tick")
        .description("Inventory clicks per tick. Keep it low on 2b2t.")
        .defaultValue(1).min(1).sliderMax(6)
        .build());

    private final Setting<Integer> clickDelay = sgSpeed.add(new IntSetting.Builder()
        .name("click-delay")
        .description("Ticks to wait after a tick with inventory clicks.")
        .defaultValue(3).min(0).sliderMax(10)
        .build());

    private final Setting<Integer> actionDelay = sgSpeed.add(new IntSetting.Builder()
        .name("action-delay")
        .description("Ticks to wait between steps (after opening, before closing...).")
        .defaultValue(4).min(0).sliderMax(20)
        .build());

    private final Setting<Integer> openTimeout = sgSpeed.add(new IntSetting.Builder()
        .name("open-timeout")
        .description("Ticks to wait for a container to open before retrying.")
        .defaultValue(40).min(10).sliderMax(100)
        .build());

    private final Setting<Integer> pathTimeout = sgSpeed.add(new IntSetting.Builder()
        .name("path-timeout")
        .description("Seconds to reach a chest before giving up on it.")
        .defaultValue(60).min(5).sliderMax(300)
        .build());

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Outline input (yellow), destination (green) and full (red) chests.")
        .defaultValue(true)
        .build());

    enum State {
        IDLE, PICK_INPUT, GOTO, OPEN, TAKE, PICK_DEST, DEPOSIT, CLOSE, PURIFY, PACK, PAUSED, DONE

    }

    enum Target { INPUT, DEST, PACK, ENDER }
    private enum RunMode { SORT, WITHDRAW }
    enum ContainerType { STORAGE, ENDER, SHULKER }

    private static final double REACH = 4.5;

    private State state = State.IDLE;
    private RunMode runMode = RunMode.SORT;
    private boolean runDryRun;
    private SorterProfile runProfile;
    private SorterWithdrawal.Request pendingWithdrawal;
    private SorterWithdrawal withdrawal;
    private Target target;
    private BlockPos targetPos;
    private Destination currentDest;
    private int timer;
    private int stateTicks;
    private int retries;
    private int pathRelaunches;
    private int openings;
    private int cursorReappearances;
    private boolean openingChecked;
    private State resumeState;
    private String pauseReason;
    private long pauseStarted;
    private long clearSince;
    private boolean pendingRecovery;
    private boolean pathingStarted;
    private boolean assignedReserve;
    private long closedScreenWaitSince;
    private int closedDepositTicks;
    private int contentsSyncId = -1;
    private GenericContainerScreenHandler visitHandler;

    private final Set<Pos> exhaustedInputs = new HashSet<>();
    private final Set<Pos> excludedChests = new HashSet<>();
    private final Map<Pos, Integer> occupiedOnClose = new LinkedHashMap<>();

    private final Map<Pos, Set<Integer>> attemptedSources = new LinkedHashMap<>();
    private final SorterInventory inventory = new SorterInventory(this, useHotbar::get);
    private final SorterSafety safety = new SorterSafety();
    private final SorterActivation activation = new SorterActivation(this, safety);
    private final SorterDeliveryTracker deliveries = new SorterDeliveryTracker(inventory);
    private final SorterPacking packing = new SorterPacking(this, inventory);
    private SorterRunSnapshot snapshot = SorterRunSnapshot.empty();
    private final Set<Pos> emptiedInputs = new HashSet<>();
    private long runStarted;
    private long runEnded;
    private boolean runCompleted;
    private final SorterAlerts alerts = new SorterAlerts();
    private final SorterSnapshotBuilder snapshotBuilder = new SorterSnapshotBuilder(alerts);
    private SorterProfile snapshotProfile;
    private final Purifier purifier = new Purifier(this, inventory, safety);
    private PurificationPlan candidate;
    private boolean deliverBeforePurifying;
    private boolean purificationDisabled;
    private final Map<String, Integer> report = new LinkedHashMap<>();

    public ShulkerSorter() {
        super(HunterBuddyAddon.LOGISTICS_CATEGORY, "shulker-sorter",
            "Sorts storage by category. Register blocks with tag-key and edit their roles below.");
    }

    @Override
    public void toggle() {
        if (activation != null) {
            activation.onToggle();
        }
        super.toggle();
        refreshSnapshot();
    }

    void stopForDisconnect() {
        deliveries.resetVisit();
        visitHandler = null;
        toggle();
    }

    public boolean withdraw(String category, int count, boolean ender, boolean fullOnly) {
        if (isActive() || setup.isInspecting()) {
            warning("Stop the sorter and wait for tagging before withdrawing.");
            return false;
        }
        if (mc.player == null || mc.world == null) {
            warning("Join a world before withdrawing.");
            return false;
        }
        SorterProfile profile = SorterStore.get();
        if (!profile.rules.isKnown(category) || CategoryRules.MIXED.equals(category) || count < 1 || count > 27) {
            warning("Choose a category other than mixed and a quantity from 1 to 27.");
            return false;
        }
        if (ender && profile.enderChest == null) {
            warning("Register an ender chest with tag-key first.");
            return false;
        }
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler
            || !mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            warning("Close the container and empty the cursor before withdrawing.");
            return false;
        }
        pendingWithdrawal = new SorterWithdrawal.Request(category, count, ender, fullOnly);
        mc.setScreen(null);
        toggle();
        return isActive();
    }

    boolean isWithdrawing() {
        return runMode == RunMode.WITHDRAW;
    }

    private boolean depositing() {
        return target == Target.DEST || target == Target.ENDER;
    }

    @Override
    public void onActivate() {
        SorterWithdrawal.Request requested = pendingWithdrawal;
        pendingWithdrawal = null;
        runMode = requested == null ? RunMode.SORT : RunMode.WITHDRAW;
        runDryRun = requested == null && dryRun.get();
        withdrawal = null;
        if (setup.isInspecting()) {
            warning("Wait for container tagging to finish before starting the sorter.");
            toggle();
            return;
        }
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }
        SorterProfile profile = SorterStore.get();
        boolean recovery = !isWithdrawing() && !runDryRun && purifier.needsRecovery();
        if (!isWithdrawing() && !recovery && profile.inputs.isEmpty()) {
            error("No input chest. Register one with tag-key and choose its input role in the settings.");
            toggle();
            return;
        }
        if (!isWithdrawing() && !recovery && !runDryRun && profile.destinations.isEmpty() && profile.reserves.isEmpty()) {
            error("No destination or reserve. Register storage with tag-key first.");
            toggle();
            return;
        }
        if (!BaritoneHelper.isAvailable()) {
            error("Baritone is needed to walk between chests.");
            toggle();
            return;
        }
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler
            || !mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            error("Close the container and empty the cursor before starting the sorter.");
            toggle();
            return;
        }

        deliveries.reset();
        packing.reset();
        emptiedInputs.clear();
        runStarted = System.nanoTime();
        runEnded = 0;
        runCompleted = false;
        alerts.clear();
        snapshotProfile = profile;
        runProfile = profile;
        inventory.reset();
        if (requested != null) {
            withdrawal = new SorterWithdrawal(inventory, requested, profile);
        }
        attemptedSources.clear();
        candidate = null;
        deliverBeforePurifying = false;
        purificationDisabled = false;
        exhaustedInputs.clear();
        excludedChests.clear();
        occupiedOnClose.clear();
        report.clear();
        timer = 0;
        currentDest = null;
        targetPos = null;
        pauseReason = null;
        pauseStarted = 0;
        clearSince = 0;
        pendingRecovery = false;
        assignedReserve = false;
        closedScreenWaitSince = 0;
        closedDepositTicks = 0;
        setState(State.PICK_INPUT);
        if (!isWithdrawing() && (purifyMixed.get() || packShulkers.get()) && profile.unpackStation != null) {
            inventory.reserveHotbarSlot();
            if (inventory.reservedSlot == -1) {
                warning("No empty hotbar slot; station jobs disabled for this run.");
            }
        }
        if (recovery) {
            if (safety.waitingReason(ignoreFriends.get()) != null) {
                pendingRecovery = true;
            } else {
                purifier.start(null, -1, 0);
                setState(State.PURIFY);
            }
        }
        if (isWithdrawing()) {
            info("Withdraw: %s 0/%d -> %s.", requested.category(), requested.count(), requested.ender() ? "Ender chest" : "Inventory");
            return;
        }
        info("Started%s. %d input chest(s), %d destination(s).",
            runDryRun ? " (dry run)" : "", profile.inputs.size(), profile.destinations.size());
    }

    @Override
    public void onDeactivate() {
        if (setup.isInspecting()) {
            state = State.IDLE;
            return;
        }
        if (runStarted != 0 && runEnded == 0) {
            runEnded = System.nanoTime();
        }
        if (state == State.PURIFY && mc.world != null && purifier.needsRecovery() && !alerts.blocking()) {
            blockingWarning("Recovery required at %s.", purifier.position().toShortString());
        }
        stopPathing();
        purifier.restoreSelectedSlot();
        if (mc.player != null && mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler
            && handler.getCursorStack().isEmpty()) {
            recordVisitClose(handler);
        }
        if (mc.player != null && mc.player.currentScreenHandler != mc.player.playerScreenHandler
            && mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            mc.player.closeHandledScreen();
        }
        SorterStore.save();
        state = State.IDLE;
        refreshSnapshot();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        try {
            tickRun();
        } finally {
            refreshSnapshot();
        }
    }

    private void tickRun() {
        if (mc.player == null || mc.world == null) {
            return;
        }
        if (runProfile != SorterStore.get()) {
            warning("Profile changed during run; stopped with cargo in inventory.");
            deliveries.resetVisit();
            visitHandler = null;
            toggle();
            return;
        }
        if ((state == State.PACK || state == State.PICK_INPUT || state == State.PICK_DEST)
            && waitForClosedScreen(System.nanoTime())) {
            return;
        }
        if (state == State.PAUSED) {
            tickPaused();
            return;
        }
        if (state == State.PICK_INPUT || state == State.PICK_DEST || state == State.PACK) {
            String reason = safety.waitingReason(ignoreFriends.get());
            if (reason != null) {
                resumeState = state;
                pauseReason = reason;
                pauseStarted = System.nanoTime();
                clearSince = 0;
                setState(State.PAUSED);
                stopPathing();
                return;
            }
        }
        stateTicks++;
        if (state == State.TAKE || state == State.DEPOSIT || state == State.CLOSE) {
            int slots = mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler ? handler.slots.size() : 90;
            long limit = 4L * slots * (clickDelay.get() + 1L) + openTimeout.get() + 2L * actionDelay.get() + 40;
            if (stateTicks > limit) {
                warning("Sorter container wait exceeded its limit; stopped with cargo in inventory.");
                toggle();
                return;
            }
        }
        if (state == State.PURIFY) {
            switch (purifier.tick()) {
                case DONE -> {
                    ItemStack completed = purifier.completedShulker();
                    if (purifier.isFilling()) {
                        if (packing.completed(completed, true)) {
                            deliveries.packed(PackingPlan.full(PackingPlan.contents(completed)));
                        }
                    } else {
                        ShulkerClassifier.Result result = SorterShulkers.classify(completed, SorterStore.get().rules, purity.get());
                        if (isShulker(completed) && !result.empty() && result.purity() == 1.0) {
                            deliveries.purified(completed);
                        }
                    }
                    setState(packing.returning() || !cargoCategories().isEmpty() ? State.PICK_DEST : State.PICK_INPUT);
                }
                case FAILURE -> {
                    if (purifier.isFilling()) {
                        packing.completed(ItemStack.EMPTY, false);
                    }
                    setState(packing.returning() || !cargoCategories().isEmpty() ? State.PICK_DEST : State.PICK_INPUT);
                }
                case STOP -> {
                    if (isActive()) {
                        toggle();
                    }
                }
                default -> { }
            }
            return;
        }
        if (waitForClosedDeposit()) {
            return;
        }
        if (timer > 0) {
            timer--;
            return;
        }

        switch (state) {
            case PICK_INPUT -> pickInput();
            case GOTO -> tickGoto();
            case OPEN -> tickOpen();
            case TAKE -> tickTake();
            case PICK_DEST -> pickDest();
            case PACK -> pickPack();
            case DEPOSIT -> tickDeposit();
            case CLOSE -> tickClose();
            case DONE -> finish();
            default -> {
            }
        }
    }

    @EventHandler
    private void onInventory(InventoryEvent event) {
        if (mc.player != null && mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler
            && event.packet.syncId() == handler.syncId) {
            contentsSyncId = handler.syncId;
        } else if (mc.player != null && mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler handler
            && event.packet.syncId() == handler.syncId) {
            purifier.receiveContents(handler.syncId);
        }
    }

    private void pickInput() {
        if (isWithdrawing()) {
            if (withdrawal.cargoChanged()) {
                warning("Withdrawal cargo changed; stopping before touching other items.");
                setState(State.DONE);
                return;
            }
            if (withdrawal.withdrawn() >= withdrawal.request().count() || inventory.freeCargoSlots() == 0) {
                setState(withdrawal.request().ender() && withdrawal.held() > 0 ? State.PICK_DEST : State.DONE);
                return;
            }
            Pos source = withdrawal.nextSource(excludedChests);
            if (source == null) {
                setState(withdrawal.request().ender() && withdrawal.held() > 0 ? State.PICK_DEST : State.DONE);
            } else {
                goTo(Target.INPUT, SorterStore.blockPos(source));
            }
            return;
        }
        if (pendingRecovery) {
            pendingRecovery = false;
            purifier.start(null, -1, 0);
            setState(State.PURIFY);
            return;
        }
        SorterProfile profile = SorterStore.get();

        if (!runDryRun && inventory.freeCargoSlots() == 0) {
            if (!cargoCategories().isEmpty()) {
                setState(State.PICK_DEST);
            } else {
                blockingWarning("Inventory full of cargo that has no free destination. Add chests or empty them.");
                setState(State.DONE);
            }
            return;
        }

        Pos best = null;
        double bestDist = Double.MAX_VALUE;
        for (Pos p : profile.inputs) {
            if (exhaustedInputs.contains(p) || excludedChests.contains(p)) {
                continue;
            }
            double d = mc.player.getBlockPos().getSquaredDistance(SorterStore.blockPos(p));
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }

        if (best == null) {
            setState(!cargoCategories().isEmpty() ? State.PICK_DEST
                : canPack() && !packing.started() && !hasCargo() ? State.PACK : State.DONE);
            return;
        }
        goTo(Target.INPUT, SorterStore.blockPos(best));
    }

    private boolean canPack() {
        return packShulkers.get() && !runDryRun && !purificationDisabled && inventory.reservedSlot >= 0
            && SorterStore.get().unpackStation != null;
    }

    private boolean waitForClosedScreen(long now) {
        if (safeToPause()) {
            closedScreenWaitSince = 0;
            return false;
        }
        if (closedScreenWaitSince == 0) {
            closedScreenWaitSince = now;
        } else if (now - closedScreenWaitSince >= SorterSafety.PAUSE_LIMIT_NANOS) {
            blockingWarning("Sorter waited two minutes for a closed screen and empty cursor; sorter stopped.");
            toggle();
        }
        return true;
    }

    private void pickPack() {
        if (!canPack() && packing.plan() == null) {
            packing.finish();
            setState(State.DONE);
            return;
        }
        if (!packing.started()) {
            packing.begin(SorterStore.get());
        }
        Pos next = canPack() ? packing.nextChest(excludedChests) : null;
        if (next != null) {
            assignedReserve = false;
            goTo(Target.PACK, SorterStore.blockPos(next));
        } else if (packing.plan() != null) {
            if (canPack() && packing.canFill()) {
                purifier.startFilling(packing.plan(), inventory.reservedSlot, timer);
                timer = 0;
                setState(State.PURIFY);
            } else {
                packing.completed(ItemStack.EMPTY, false);
                setState(State.PICK_DEST);
            }
        } else {
            setState(State.DONE);
        }
    }

    private boolean hasCargo() {
        for (int inv = 0; inv < 36; inv++) {
            if (inventory.isCargoSlot(inv) && !mc.player.getInventory().getStack(inv).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void pickDest() {
        if (isWithdrawing()) {
            if (withdrawal.held() == 0 || withdrawal.cargoChanged()) {
                setState(State.DONE);
                return;
            }
            Pos ender = withdrawal.nextEnder(excludedChests);
            if (ender == null) {
                setState(State.DONE);
            } else {
                goTo(Target.ENDER, SorterStore.blockPos(ender));
            }
            return;
        }
        SorterProfile profile = SorterStore.get();
        for (String category : cargoCategories()) {
            DestinationChoice choice = resolveDestination(profile, category);
            if (choice != null) {
                assignedReserve = choice.reserve();
                currentDest = assignedReserve ? setup.assignReserve(choice.category(), choice.chest()) : choice.destination();
                if (currentDest == null) {
                    excludedChests.add(choice.chest());
                    continue;
                }
                goTo(Target.DEST, SorterStore.blockPos(choice.chest()));
                return;
            }
        }

        if (packing.returning()) {
            if (hasCargo()) {
                blockingWarning("No free destination for packing cargo; sorter stopped with items in inventory.");
                toggle();
            } else {
                packing.returned();
                setState(State.PACK);
            }
            return;
        }
        if (!cargoCategories().isEmpty()) {
            warning("Carrying %s but no free destination chest for it.", cargoCategories());
        }
        setState(State.PICK_INPUT);
    }

    private void goTo(Target t, BlockPos pos) {
        target = t;
        targetPos = pos;
        contentsSyncId = -1;
        visitHandler = null;
        deliveries.resetVisit();
        retries = 0;
        pathRelaunches = 0;
        openings = 0;
        if (!inReach(pos)) {
            pathTo(new GoalGetToBlock(pos));
        }
        setState(State.GOTO);
    }

    private void tickGoto() {
        if (isWithdrawing() && !safeToPause()) {
            stopPathing();
            if (stateTicks > pathTimeout.get() * 20) {
                skipTarget();
            }
            return;
        }
        if (inReach(targetPos)) {
            stopPathing();
            setState(State.OPEN);
            return;
        }
        if (stateTicks > pathTimeout.get() * 20) {
            warning("Could not reach %s, skipping it.", targetPos.toShortString());
            stopPathing();
            skipTarget();
            return;
        }
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (!baritone.getPathingBehavior().isPathing() && !baritone.getCustomGoalProcess().isActive()) {
            if (pathRelaunches >= 2) {
                warning("Pathing to %s was abandoned, skipping it.", targetPos.toShortString());
                stopPathing();
                skipTarget();
                return;
            }
            pathRelaunches++;
            pathTo(new GoalGetToBlock(targetPos));
        }
    }

    private void tickOpen() {
        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler) {
            visitHandler = handler;
            if (retries == 0 || target == Target.ENDER && (handler.getRows() != 3
                || !(mc.world.getBlockState(targetPos).getBlock() instanceof EnderChestBlock))) {
                skipTarget();
                return;
            }
            if (isWithdrawing() && openings > 0 && !withdrawal.visit(SorterStore.pos(targetPos))) {
                skipTarget();
                return;
            }
            if (++openings > 3) {
                warning("Container at %s keeps closing, skipping it.", targetPos.toShortString());
                skipTarget();
                return;
            }
            inventory.open();
            cursorReappearances = 0;
            openingChecked = false;
            timer = actionDelay.get();
            setState(depositing() ? State.DEPOSIT : State.TAKE);
            return;
        }
        if (isWithdrawing() && mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
            skipTarget();
            return;
        }
        if (stateTicks == 1 || stateTicks % openTimeout.get() == 0) {
            if (++retries > 3) {
                String blocked = SorterSetup.blockedChestReason(targetPos);
                if (blocked != null) {
                    warning("%s", blocked);
                } else {
                    warning("Container at %s will not open, skipping it.", targetPos.toShortString());
                }
                skipTarget();
                return;
            }
            if (!interact(targetPos, target == Target.ENDER ? ContainerType.ENDER : ContainerType.STORAGE)) {
                skipTarget();
            }
        }
    }

    boolean interact(BlockPos pos, ContainerType type) {
        Block block = mc.world.getBlockState(pos).getBlock();
        boolean valid = switch (type) {
            case STORAGE -> block instanceof ChestBlock || block instanceof BarrelBlock;
            case ENDER -> block instanceof EnderChestBlock;
            case SHULKER -> block instanceof ShulkerBoxBlock;
        };
        if (!valid) {
            warning("Container at %s changed; skipping it.", pos.toShortString());
            return false;
        }
        Vec3d eye = mc.player.getEyePos();
        Vec3d center = Vec3d.ofCenter(pos);
        Vec3d toEye = eye.subtract(center);
        Direction face = Direction.getFacing(toEye.x, toEye.y, toEye.z);
        Vec3d hit = center.add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
        float yaw = (float) RotationUtils.getYaw(hit);
        float pitch = (float) RotationUtils.getPitch(hit);
        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
        mc.player.networkHandler.sendPacket(new LookAndOnGround(yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision));

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(hit, face, pos, false));
        return true;
    }

    private void skipTarget() {
        excludedChests.add(SorterStore.pos(targetPos));
        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
            inventory.closingRequested = true;
            if (mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                closeThen();
            } else if (!isWithdrawing() && runDryRun) {
                stopWithCursor();
            } else {
                setState(depositing() ? State.DEPOSIT : State.TAKE);
            }
            return;
        }
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            stopWithCursor();
            return;
        }
        setState(target == Target.PACK ? State.PACK : target == Target.INPUT ? State.PICK_INPUT : State.PICK_DEST);
    }

    private void tickTake() {
        int clicksBefore = inventory.openingClicks;
        try {
            if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) {
                setState(State.OPEN);
                return;
            }
            if (handler != visitHandler) {
                blockingWarning("Sorter container screen changed; sorter stopped with cargo.");
                toggle();
                return;
            }
            int containerSlots = handler.getRows() * 9;
            if (!checkOpening(handler)) {
                return;
            }

            if (target == Target.PACK && !canPack()) {
                if (!handler.getCursorStack().isEmpty()) {
                    stopWithCursor();
                } else {
                    closeThen();
                }
                return;
            }

            if (!isWithdrawing() && runDryRun) {
                if (!handler.getCursorStack().isEmpty()) {
                    stopWithCursor();
                    return;
                }
                for (int i = 0; i < containerSlots; i++) {
                    ItemStack stack = handler.getSlot(i).getStack();
                    if (!stack.isEmpty()) {
                        report.merge(describe(stack), 1, Integer::sum);
                    }
                }
                exhaustedInputs.add(SorterStore.pos(targetPos));
                closeThen();
                return;
            }

            for (int clicks = 0; clicks < clicksPerTick.get(); clicks++) {
                if (!inventory.closingRequested && inventory.openingClicks >= SorterInventory.clickLimit(handler)) {
                    abandonOpening("Too many clicks");
                }
                if (inventory.closingRequested) {
                    if (handler.getCursorStack().isEmpty()) {
                        closeThen();
                        return;
                    }
                    if (!inventory.emptyCursor(handler)) {
                        return;
                    }
                    continue;
                }
                if (isWithdrawing()) {
                    if (!withdrawal.take(handler, SorterStore.pos(targetPos))) {
                        closeThen();
                        return;
                    }
                    continue;
                }
                if (target == Target.PACK) {
                    if (!packing.take(handler, SorterStore.pos(targetPos))) {
                        closeThen();
                        return;
                    }
                    continue;
                }
                if (candidate != null) {
                    int slot = SorterInventory.handlerSlotOf(containerSlots, inventory.reservedSlot);
                    if (!handler.getCursorStack().isEmpty() && handler.getSlot(slot).getStack().isEmpty()) {
                        mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
                        inventory.openingClicks++;
                    } else if (handler.getCursorStack().isEmpty()) {
                        if (!isShulker(handler.getSlot(slot).getStack())) {
                            candidate = null;
                        }
                        closeThen();
                        return;
                    } else {
                        candidate = null;
                        abandonOpening("Reserved slot changed");
                    }
                    continue;
                }
                if (!handler.getCursorStack().isEmpty()) {
                    if (!inventory.emptyCursor(handler)) {
                        return;
                    }
                    continue;
                }
                inventory.cursorSourceSlot = -1;
                int source = -1;
                for (int i = 0; i < containerSlots; i++) {
                    ItemStack stack = handler.getSlot(i).getStack();
                    if (!purificationDisabled && inventory.reservedSlot >= 0 && purifyMixed.get()
                        && SorterStore.get().unpackStation != null
                        && mc.player.getInventory().getStack(inventory.reservedSlot).isEmpty()
                        && isShulker(stack) && SorterShulkers.classify(stack, SorterStore.get().rules, purity.get()).isMixed()
                        && !attemptedSources.getOrDefault(SorterStore.pos(targetPos), Set.of()).contains(i)) {
                        PurificationPlan plan = Purifier.plan(stack);
                        int capacity = inventory.cargoCapacity();
                        if (resolveDestination(SorterStore.get(), plan.retainedCategory()) != null
                            && plan.requiredCargoSlots() <= capacity) {
                            if (inventory.freeCargoSlots() < plan.requiredCargoSlots()) {
                                if (!cargoCategories().isEmpty()) {
                                    deliverBeforePurifying = true;
                                    closeThen();
                                    return;
                                }
                            } else if (inventory.openingClicks > 0) {
                                deliverBeforePurifying = true;
                                closeThen();
                                return;
                            } else {
                                candidate = plan;
                                attemptedSources.computeIfAbsent(SorterStore.pos(targetPos), key -> new HashSet<>()).add(i);
                                source = i;
                                break;
                            }
                        }
                    }
                    if (!stack.isEmpty() && inputDestination(stack) != null) {
                        source = i;
                        break;
                    }
                }
                if (source == -1 || inventory.firstEmptyCargoSlot(handler) == -1) {
                    if (source == -1) {
                        exhaustedInputs.add(SorterStore.pos(targetPos));
                    }
                    closeThen();
                    return;
                }
                inventory.cursorSourceSlot = source;
                mc.interactionManager.clickSlot(handler.syncId, source, 0, SlotActionType.PICKUP, mc.player);
                inventory.openingClicks++;
            }
        } finally {
            if (inventory.openingClicks > clicksBefore) {
                timer = clickDelay.get();
            }
        }
    }

    private boolean waitForClosedDeposit() {
        if (!isWithdrawing() || target != Target.ENDER || state != State.DEPOSIT && state != State.CLOSE) {
            return false;
        }
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
            closedDepositTicks = 0;
            return false;
        }
        if (mc.currentScreen != null || !mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            closedDepositTicks = 0;
            return true;
        }
        if (++closedDepositTicks < actionDelay.get()) {
            return true;
        }
        withdrawal.finishClosedDeposit();
        closedDepositTicks = 0;
        visitHandler = null;
        timer = actionDelay.get();
        setState(withdrawal.cargoChanged() ? State.DONE : withdrawal.held() > 0 ? State.PICK_DEST : State.PICK_INPUT);
        return true;
    }

    private void tickDeposit() {
        int clicksBefore = inventory.openingClicks;
        try {
            if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) {
                deliveries.resetVisit();
                setState(State.OPEN);
                return;
            }
            if (handler != visitHandler) {
                blockingWarning("Sorter container screen changed; sorter stopped with cargo.");
                toggle();
                return;
            }
            int containerSlots = handler.getRows() * 9;
            if (!checkOpening(handler)) {
                return;
            }
            if (isWithdrawing() && withdrawal.cargoChanged()) {
                inventory.closingRequested = true;
                if (handler.getCursorStack().isEmpty()) {
                    closeThen();
                } else {
                    inventory.emptyCursor(handler);
                }
                return;
            }
            if (isWithdrawing()) {
                withdrawal.beginDeposit(handler);
            } else {
                deliveries.begin(handler, currentDest.category, (inv, stack) -> currentDest.category.equals(routedCategory(stack)));
            }
            int clicks = 0;
            boolean leftover = false;

            for (int inv = 0; inv < 36 && clicks < clicksPerTick.get(); inv++) {
                if (!inventory.closingRequested && inventory.openingClicks >= SorterInventory.clickLimit(handler)) {
                    abandonOpening("Too many clicks");
                }
                if (inventory.closingRequested || !handler.getCursorStack().isEmpty()) {
                    if (!handler.getCursorStack().isEmpty()) {
                        inventory.emptyCursor(handler);
                    } else {
                        closeThen();
                    }
                    return;
                }
                if (!inventory.isCargoSlot(inv)) {
                    continue;
                }
                ItemStack stack = mc.player.getInventory().getStack(inv);
                if (stack.isEmpty() || (isWithdrawing() ? !withdrawal.owns(inv, stack)
                    : !currentDest.category.equals(routedCategory(stack)))) {
                    continue;
                }
                leftover = true;
                if (!hasRoomFor(handler, containerSlots, stack)) {
                    break;
                }
                mc.interactionManager.clickSlot(handler.syncId, SorterInventory.handlerSlotOf(containerSlots, inv), 0, SlotActionType.QUICK_MOVE, mc.player);
                if (isWithdrawing()) {
                    withdrawal.depositClicked(inv);
                }
                clicks++;
                inventory.openingClicks++;
            }

            if (clicks == 0) {
                if (isWithdrawing()) {
                    closeThen();
                    return;
                }
                Pos here = SorterStore.pos(targetPos);
                if (leftover) {
                    currentDest.full.add(here);
                    info("Chest %s (%s) is full.", here, currentDest.category);
                } else {
                    currentDest.full.remove(here);
                }
                SorterStore.save();
                closeThen();
            }
        } finally {
            if (inventory.openingClicks > clicksBefore) {
                timer = clickDelay.get();
            }
        }
    }

    private static boolean hasRoomFor(GenericContainerScreenHandler handler, int containerSlots, ItemStack stack) {
        for (int i = 0; i < containerSlots; i++) {
            ItemStack in = handler.getSlot(i).getStack();
            if (in.isEmpty()) {
                return true;
            }
            if (ItemStack.areItemsAndComponentsEqual(in, stack) && in.getCount() < in.getMaxCount()) {
                return true;
            }
        }
        return false;
    }

    private static int occupiedSlots(GenericContainerScreenHandler handler) {
        int occupied = 0;
        for (int i = 0; i < handler.getRows() * 9; i++) {
            if (!handler.getSlot(i).getStack().isEmpty()) {
                occupied++;
            }
        }
        return occupied;
    }

    private boolean checkOpening(GenericContainerScreenHandler handler) {
        if (contentsSyncId != handler.syncId) {
            if (stateTicks > openTimeout.get() + actionDelay.get()) {
                assignedReserve = false;
                abandonOpening("Container contents did not arrive");
                if (!handler.getCursorStack().isEmpty()) {
                    if (runDryRun) {
                        stopWithCursor();
                    } else {
                        inventory.emptyCursor(handler);
                    }
                }
                if (isActive() && handler.getCursorStack().isEmpty()) {
                    closeThen();
                }
            }
            return false;
        }
        if (target == Target.DEST && assignedReserve) {
            assignedReserve = false;
            if (occupiedSlots(handler) != 0) {
                currentDest.full.add(SorterStore.pos(targetPos));
                SorterStore.save();
                warning("Reserve at %s was not empty; kept assigned to %s and marked full.",
                    targetPos.toShortString(), currentDest.category);
                closeThen();
                return false;
            }
        }
        if (openingChecked) {
            return true;
        }
        openingChecked = true;
        Integer previous = occupiedOnClose.get(SorterStore.pos(targetPos));
        int occupied = occupiedSlots(handler);
        if (previous != null && (depositing() ? occupied < previous : occupied > previous)) {
            abandonOpening("the server undid our clicks");
            if (handler.getCursorStack().isEmpty()) {
                closeThen();
            } else if (!isWithdrawing() && runDryRun) {
                stopWithCursor();
            }
            return false;
        }
        if (!isWithdrawing() && target != Target.INPUT) {
            packing.observe(SorterStore.pos(targetPos), handler);
        }
        return true;
    }

    private void abandonOpening(String reason) {
        warning("%s at %s, excluding this chest for this run.", reason, targetPos.toShortString());
        excludedChests.add(SorterStore.pos(targetPos));
        inventory.closingRequested = true;
    }

    void stopWithCursor() {
        blockingWarning("Could not safely empty the cursor. Sorter stopped; empty it manually before closing the screen.");
        toggle();
    }

    void closeThen() {
        inventory.closingRequested = true;
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            return;
        }
        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler) {
            occupiedOnClose.put(SorterStore.pos(targetPos), occupiedSlots(handler));
        }
        timer = actionDelay.get();
        setState(State.CLOSE);
    }

    private void tickClose() {
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            if (++cursorReappearances > 3 || !isWithdrawing() && runDryRun
                || !(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler)) {
                stopWithCursor();
            } else {
                inventory.closingRequested = true;
                setState(depositing() ? State.DEPOSIT : State.TAKE);
            }
            return;
        }
        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler) {
            recordVisitClose(handler);
        }
        mc.player.closeHandledScreen();
        timer = actionDelay.get();
        if (isWithdrawing()) {
            setState(target == Target.ENDER && withdrawal.held() > 0 ? State.DONE : State.PICK_INPUT);
            return;
        }
        if (target == Target.PACK) {
            setState(State.PACK);
            return;
        }
        if (candidate != null) {
            boolean ready = SorterStore.get().unpackStation != null
                && isShulker(mc.player.getInventory().getStack(inventory.reservedSlot));
            if (ready) {
                purifier.start(candidate, inventory.reservedSlot, timer);
                timer = 0;
            }
            candidate = null;
            if (ready) {
                setState(State.PURIFY);
                return;
            }
        }
        if (target == Target.INPUT) {
            boolean deliverNow = !runDryRun && !cargoCategories().isEmpty()
                && (deliverBeforePurifying || inventory.freeCargoSlots() == 0 || exhaustedInputs.contains(SorterStore.pos(targetPos))
                    || excludedChests.contains(SorterStore.pos(targetPos)));
            setState(deliverNow ? State.PICK_DEST : State.PICK_INPUT);
            deliverBeforePurifying = false;
        } else {
            setState(State.PICK_DEST);
        }
    }

    private void finish() {
        runCompleted = true;
        if (isWithdrawing()) {
            info("%s", withdrawal.summary());
        } else if (runDryRun) {
            info("Dry run report (%d kinds):", report.size());
            report.forEach((k, v) -> info("  %dx %s", v, k));
            info("Turn off dry-run to actually move things.");
        } else {
            String reason = hasCargo() && cargoCategories().isEmpty()
                ? (inventory.freeCargoSlots() == 0 ? "inventory full of cargo with no free destination" : "cargo has no free destination")
                : inventory.freeCargoSlots() == 0 ? "inventory full: no cargo space"
                : SorterStore.get().inputs.stream().anyMatch(excludedChests::contains) ? "inputs excluded" : "nothing routable left";
            info("Done: %s.", reason);
        }
        SorterStore.save();
        toggle();
    }

    private void recordVisitClose(GenericContainerScreenHandler handler) {
        if (handler != visitHandler) {
            deliveries.resetVisit();
            return;
        }
        if (isWithdrawing()) {
            withdrawal.settle(handler);
            if (target == Target.ENDER) {
                withdrawal.finishDeposit(handler);
            }
            if (contentsSyncId == handler.syncId) {
                occupiedOnClose.put(SorterStore.pos(targetPos), occupiedSlots(handler));
            }
            return;
        }
        if (target != Target.INPUT && contentsSyncId == handler.syncId) {
            Pos pos = SorterStore.pos(targetPos);
            packing.observe(pos, handler);
            occupiedOnClose.put(pos, occupiedSlots(handler));
            if (target == Target.PACK && occupiedSlots(handler) < handler.getRows() * 9) {
                boolean changed = false;
                for (Destination destination : SorterStore.get().destinations) {
                    if (destination.chests.contains(pos)) {
                        changed |= destination.full.remove(pos);
                    }
                }
                if (changed) {
                    SorterStore.save();
                }
            }
        }
        if (target == Target.DEST && !runDryRun) {
            deliveries.finish(handler);
        } else if (target == Target.INPUT && targetPos != null && contentsSyncId == handler.syncId && occupiedSlots(handler) == 0) {
            emptiedInputs.add(SorterStore.pos(targetPos));
        }
    }

    private String categoryOf(ItemStack stack) {
        SorterProfile profile = SorterStore.get();
        if (isShulker(stack)) {
            return SorterShulkers.classify(stack, profile.rules, purity.get()).category();
        }
        return profile.rules.categoryOf(Registries.ITEM.getId(stack.getItem()).toString());
    }

    private String routedCategory(ItemStack stack) {
        String category = categoryOf(stack);
        if (category == null) {
            return null;
        }
        DestinationChoice choice = resolveDestination(SorterStore.get(), category);
        return choice == null ? null : choice.category();
    }

    private record DestinationChoice(Destination destination, Pos chest, String category, boolean reserve) {
    }

    private DestinationChoice resolveDestination(SorterProfile profile, String category) {
        List<Destination> destinations = profile.destinationsFor(category);
        for (Destination destination : destinations) {
            for (Pos chest : destination.chests) {
                if (!destination.full.contains(chest) && !excludedChests.contains(chest)) {
                    return new DestinationChoice(destination, chest, destination.category, false);
                }
            }
        }
        Pos reserve = null;
        double distance = Double.MAX_VALUE;
        for (Pos pos : profile.reserves) {
            if (excludedChests.contains(pos)) {
                continue;
            }
            double candidate = mc.player.getBlockPos().getSquaredDistance(SorterStore.blockPos(pos));
            if (candidate < distance) {
                reserve = pos;
                distance = candidate;
            }
        }
        if (reserve != null) {
            return new DestinationChoice(null, reserve, category, true);
        }
        if (destinations.stream().noneMatch(destination -> !destination.chests.isEmpty())
            && fallbackRandom.get() && !CategoryRules.MIXED.equals(category)) {
            for (Destination destination : profile.destinationsFor(CategoryRules.FALLBACK)) {
                for (Pos chest : destination.chests) {
                    if (!destination.full.contains(chest) && !excludedChests.contains(chest)) {
                        return new DestinationChoice(destination, chest, destination.category, false);
                    }
                }
            }
        }
        return null;
    }

    public SorterSetup setup() {
        return setup;
    }

    Keybind tagKey() {
        return tagKey.get();
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return setup.widget(theme);
    }

    private DestinationChoice inputDestination(ItemStack stack) {
        if (!isShulker(stack) && !sortLoose.get()) {
            return null;
        }
        return resolveDestination(SorterStore.get(), categoryOf(stack));
    }

    private String describe(ItemStack stack) {
        SorterProfile profile = SorterStore.get();
        ShulkerClassifier.Result result = isShulker(stack) ? SorterShulkers.classify(stack, profile.rules, purity.get()) : null;
        String item = result != null ? "shulker (" + result.describe() + ")" : Registries.ITEM.getId(stack.getItem()).getPath();
        if (result != null && result.isMixed() && purifyMixed.get() && !purificationDisabled
            && inventory.reservedSlot >= 0 && profile.unpackStation != null) {
            PurificationPlan plan = Purifier.plan(stack);
            if (plan.requiredCargoSlots() <= inventory.cargoCapacity() && resolveDestination(profile, plan.retainedCategory()) != null) {
                return item + " -> station: keeps " + plan.retainedCategory();
            }
        }
        DestinationChoice choice = inputDestination(stack);
        return item + " -> " + (choice == null ? "stays" : choice.reserve() ? "a reserve (if still free)"
            : choice.category() + " at " + choice.chest());
    }

    static boolean isShulker(ItemStack stack) {
        return stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    void disablePurification(String reason) {
        if (!purificationDisabled) {
            purificationDisabled = true;
            warning("Station jobs disabled for this run: %s.", reason);
        }
    }

    private Set<String> cargoCategories() {
        Set<String> out = new LinkedHashSet<>();
        for (int i = 0; i < 36; i++) {
            if (!inventory.isCargoSlot(i)) {
                continue;
            }
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                String routed = routedCategory(stack);
                if (routed != null) {
                    out.add(routed);
                }
            }
        }
        return out;
    }

    boolean inReach(BlockPos pos) {
        return mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) <= REACH * REACH;
    }

    void stopPathing() {
        if (pathingStarted && BaritoneHelper.isAvailable() && safety.driverReason() == null) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
        }
        pathingStarted = false;
    }

    void pathTo(Goal goal) {
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
        pathingStarted = true;
    }

    private boolean safeToPause() {
        return mc.currentScreen == null && mc.player.currentScreenHandler == mc.player.playerScreenHandler
            && mc.player.currentScreenHandler.getCursorStack().isEmpty();
    }

    private void tickPaused() {
        long now = System.nanoTime();
        if (now - pauseStarted >= SorterSafety.PAUSE_LIMIT_NANOS) {
            blockingWarning("Pause exceeded two minutes (%s); sorter stopped.", pauseReason);
            toggle();
            return;
        }
        String reason = safety.waitingReason(ignoreFriends.get());
        if (reason != null) {
            pauseReason = reason;
            clearSince = 0;
        } else if (!safeToPause()) {
            clearSince = 0;
        } else if (clearSince == 0) {
            clearSince = now;
        } else if (now - clearSince >= SorterSafety.RESUME_DELAY_NANOS) {
            pauseReason = null;
            setState(resumeState);
        }
    }

    boolean isAtStation() {
        return state == State.PURIFY;
    }

    boolean autoStart() {
        return autoStart.get();
    }

    boolean resumeAfterReconnect() {
        return resumeAfterReconnect.get();
    }

    boolean dryRun() {
        return isActive() ? runDryRun : dryRun.get();
    }

    boolean ignoreFriends() {
        return ignoreFriends.get();
    }

    int clicksPerTick() {
        return clicksPerTick.get();
    }

    int clickDelay() {
        return clickDelay.get();
    }

    int actionDelay() {
        return actionDelay.get();
    }

    int openTimeout() {
        return openTimeout.get();
    }

    int pathTimeout() {
        return pathTimeout.get();
    }

    private void setState(State next) {
        state = next;
        stateTicks = 0;
    }

    public String statusLine() {
        if (state == State.PAUSED) {
            return "PAUSED: " + pauseReason + (clearSince == 0 ? "" : " (resuming after five seconds clear)");
        }
        if (state == State.PURIFY) {
            return purifier.statusLine();
        }
        return state + (targetPos == null ? "" : " @ " + targetPos.toShortString())
            + (currentDest == null ? "" : " [" + currentDest.category + "]");
    }

    @Override
    public void warning(String message, Object... args) {
        alerts.record(message, false, mc.world == null ? snapshotProfile : SorterStore.get(), args);
        super.warning(message, args);
    }

    @Override
    public void error(String message, Object... args) {
        alerts.record(message, isActive(), mc.world == null ? snapshotProfile : SorterStore.get(), args);
        super.error(message, args);
    }

    void blockingWarning(String message, Object... args) {
        alerts.record(message, true, mc.world == null ? snapshotProfile : SorterStore.get(), args);
        super.warning(message, args);
    }

    public SorterRunSnapshot snapshot() {
        return snapshot;
    }

    void refreshSnapshot() {
        long now = System.nanoTime();
        SorterProfile profile = mc.world == null || mc.player == null ? null : SorterStore.get();
        if (profile != null) {
            if (snapshotProfile != null && snapshotProfile != profile) {
                runCompleted = false;
                runStarted = 0;
                runEnded = 0;
                deliveries.reset();
                emptiedInputs.clear();
                alerts.discardOtherProfile(profile);
            }
            snapshotProfile = profile;
        }
        snapshot = snapshotBuilder.build(new SorterSnapshotBuilder.View(
            new SorterSnapshotBuilder.Run(profile != null, isActive(), autoStart.get(), runDryRun, isWithdrawing(),
                runCompleted, runStarted, runEnded),
            new SorterSnapshotBuilder.Work(state, resumeState, target, targetPos, currentDest == null ? "" : currentDest.category,
                pauseReason, packing.inProgress(), packing.category(), closedScreenWaitSince != 0, purifier.position(),
                isActive() && state == State.PURIFY ? purifier.pauseReason() : null, state == State.PURIFY ? purifier.hudState() : ""),
            new SorterSnapshotBuilder.Inputs(exhaustedInputs, excludedChests, emptiedInputs),
            profile == null ? null : SorterSnapshotBuilder.storage(profile), deliveries.results(), this::missingCargoCategories,
            withdrawal == null ? null : withdrawal::snapshot,
            pos -> Math.sqrt(mc.player.squaredDistanceTo(Vec3d.ofCenter(pos)))), now);
    }

    private List<String> missingCargoCategories() {
        Set<String> missing = new LinkedHashSet<>();
        SorterProfile profile = SorterStore.get();
        for (int inv = 0; inv < 36; inv++) {
            ItemStack stack = mc.player.getInventory().getStack(inv);
            if (inventory.isCargoSlot(inv) && !stack.isEmpty()) {
                String category = categoryOf(stack);
                if (category != null && resolveDestination(profile, category) == null) {
                    missing.add(category);
                }
            }
        }
        return List.copyOf(missing);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || mc.world == null) {
            return;
        }
        SorterProfile profile = SorterStore.get();
        Color yellow = new Color(255, 220, 0, 40), yellowLine = new Color(255, 220, 0, 200);
        Color green = new Color(0, 220, 80, 40), greenLine = new Color(0, 220, 80, 200);
        Color red = new Color(230, 40, 40, 40), redLine = new Color(230, 40, 40, 200);
        for (Pos p : profile.inputs) {
            event.renderer.box(SorterStore.blockPos(p), yellow, yellowLine, ShapeMode.Both, 0);
        }
        for (Destination d : profile.destinations) {
            for (Pos p : d.chests) {
                boolean full = d.full.contains(p);
                event.renderer.box(SorterStore.blockPos(p), full ? red : green, full ? redLine : greenLine, ShapeMode.Both, 0);
            }
        }
    }
}
