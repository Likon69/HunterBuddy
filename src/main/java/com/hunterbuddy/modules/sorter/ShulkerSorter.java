package com.hunterbuddy.modules.sorter;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalGetToBlock;
import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.util.BaritoneHelper;
import com.hunterbuddy.modules.regear.util.RotationUtils;
import com.hunterbuddy.modules.regear.util.ShulkerDataParser;
import com.hunterbuddy.modules.sorter.logic.CategoryRules;
import com.hunterbuddy.modules.sorter.logic.PurificationPlan;
import com.hunterbuddy.modules.sorter.logic.ShulkerClassifier;
import com.hunterbuddy.modules.sorter.model.SorterProfile;
import com.hunterbuddy.modules.sorter.model.SorterProfile.Destination;
import com.hunterbuddy.modules.sorter.model.SorterProfile.Pos;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
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
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
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
        .description("Also move loose items (not in a shulker) from the input chests.")
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

    private final Setting<Boolean> dryRun = sgSort.add(new BoolSetting.Builder()
        .name("dry-run")
        .description("Open the input chests and report what would go where, without moving anything.")
        .defaultValue(true)
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

    private enum State {
        IDLE, PICK_INPUT, GOTO, OPEN, TAKE, PICK_DEST, DEPOSIT, CLOSE, PURIFY, PAUSED, DONE

    }

    private enum Target { INPUT, DEST }

    private static final double REACH = 4.5;

    private State state = State.IDLE;
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
    private int contentsSyncId = -1;

    private final Set<Pos> exhaustedInputs = new HashSet<>();
    private final Set<Pos> excludedChests = new HashSet<>();
    private final Map<Pos, Integer> occupiedOnClose = new LinkedHashMap<>();

    private final Map<Pos, Set<Integer>> attemptedSources = new LinkedHashMap<>();
    private final SorterInventory inventory = new SorterInventory(this, useHotbar::get);
    private final SorterSafety safety = new SorterSafety();
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
    public void onActivate() {
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
        boolean recovery = !dryRun.get() && purifier.needsRecovery();
        if (!recovery && profile.inputs.isEmpty()) {
            error("No input chest. Register one with tag-key and choose its input role in the settings.");
            toggle();
            return;
        }
        if (!recovery && !dryRun.get() && profile.destinations.isEmpty() && profile.reserves.isEmpty()) {
            error("No destination or reserve. Register storage with tag-key first.");
            toggle();
            return;
        }
        if (!BaritoneHelper.isAvailable()) {
            error("Baritone is needed to walk between chests.");
            toggle();
            return;
        }
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            error("Empty the cursor before starting the sorter.");
            toggle();
            return;
        }

        inventory.reset();
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
        setState(State.PICK_INPUT);
        if (!dryRun.get() && purifyMixed.get() && profile.unpackStation != null) {
            inventory.reserveHotbarSlot();
            if (inventory.reservedSlot == -1) {
                warning("No empty hotbar slot; purification disabled for this run.");
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
        info("Started%s. %d input chest(s), %d destination(s).",
            dryRun.get() ? " (dry run)" : "", profile.inputs.size(), profile.destinations.size());
    }

    @Override
    public void onDeactivate() {
        if (setup.isInspecting()) {
            state = State.IDLE;
            return;
        }
        stopPathing();
        if (mc.player != null && mc.player.currentScreenHandler != mc.player.playerScreenHandler
            && mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            mc.player.closeHandledScreen();
        }
        SorterStore.save();
        state = State.IDLE;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) {
            return;
        }
        if (state == State.PAUSED) {
            tickPaused();
            return;
        }
        if (state == State.PICK_INPUT || state == State.PICK_DEST) {
            String reason = safety.waitingReason(ignoreFriends.get());
            if (reason != null) {
                if (!safeToPause()) return;
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
        if (state == State.PURIFY) {
            switch (purifier.tick()) {
                case DONE, FAILURE -> setState(cargoCategories().isEmpty() ? State.PICK_INPUT : State.PICK_DEST);
                case STOP -> { if (isActive()) toggle(); }
                default -> { }
            }
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
            case DEPOSIT -> tickDeposit();
            case CLOSE -> tickClose();
            case DONE -> finish();
            default -> {
            }
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (isActive()) toggle();
    }

    @EventHandler
    private void onInventory(InventoryEvent event) {
        if (mc.player != null && mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler
            && event.packet.syncId() == handler.syncId) {
            contentsSyncId = handler.syncId;
        }
    }

    private void pickInput() {
        if (pendingRecovery) {
            pendingRecovery = false;
            purifier.start(null, -1, 0);
            setState(State.PURIFY);
            return;
        }
        SorterProfile profile = SorterStore.get();

        if (!dryRun.get() && inventory.freeCargoSlots() == 0) {
            if (!cargoCategories().isEmpty()) {
                setState(State.PICK_DEST);
            } else {
                warning("Inventory full of cargo that has no free destination. Add chests or empty them.");
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
            setState(cargoCategories().isEmpty() ? State.DONE : State.PICK_DEST);
            return;
        }
        goTo(Target.INPUT, SorterStore.blockPos(best));
    }

    private void pickDest() {
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

        if (!cargoCategories().isEmpty()) {
            warning("Carrying %s but no free destination chest for it.", cargoCategories());
        }
        setState(State.PICK_INPUT);
    }

    private void goTo(Target t, BlockPos pos) {
        target = t;
        targetPos = pos;
        contentsSyncId = -1;
        retries = 0;
        pathRelaunches = 0;
        openings = 0;
        if (!inReach(pos)) {
            pathTo(new GoalGetToBlock(pos));
        }
        setState(State.GOTO);
    }

    private void tickGoto() {
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
        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
            if (++openings > 3) {
                warning("Container at %s keeps closing, skipping it.", targetPos.toShortString());
                skipTarget();
                return;
            }
            inventory.open();
            cursorReappearances = 0;
            openingChecked = false;
            timer = actionDelay.get();
            setState(target == Target.INPUT ? State.TAKE : State.DEPOSIT);
            return;
        }
        if (stateTicks == 1 || stateTicks % openTimeout.get() == 0) {
            if (++retries > 3) {
                warning("Container at %s will not open, skipping it.", targetPos.toShortString());
                skipTarget();
                return;
            }
            interact(targetPos);
        }
    }

    void interact(BlockPos pos) {
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
    }

    private void skipTarget() {
        excludedChests.add(SorterStore.pos(targetPos));
        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
            inventory.closingRequested = true;
            if (mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                closeThen();
            } else if (dryRun.get()) {
                stopWithCursor();
            } else {
                setState(target == Target.INPUT ? State.TAKE : State.DEPOSIT);
            }
            return;
        }
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            stopWithCursor();
            return;
        }
        setState(target == Target.INPUT ? State.PICK_INPUT : State.PICK_DEST);
    }

    private void tickTake() {
        int clicksBefore = inventory.openingClicks;
        try {
            if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) {
                setState(State.OPEN);
                return;
            }
            int containerSlots = handler.getRows() * 9;
            if (!checkOpening(handler)) {
                return;
            }

            if (dryRun.get()) {
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
                if (candidate != null) {
                    int slot = SorterInventory.handlerSlotOf(containerSlots, inventory.reservedSlot);
                    if (!handler.getCursorStack().isEmpty() && handler.getSlot(slot).getStack().isEmpty()) {
                        mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
                        inventory.openingClicks++;
                    } else if (handler.getCursorStack().isEmpty()) {
                        if (!isShulker(handler.getSlot(slot).getStack())) candidate = null;
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
                        && isShulker(stack) && classifyShulker(stack, SorterStore.get().rules).isMixed()
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
                    if (!stack.isEmpty() && routedCategory(stack) != null) {
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

    private void tickDeposit() {
        int clicksBefore = inventory.openingClicks;
        try {
            if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) {
                setState(State.OPEN);
                return;
            }
            int containerSlots = handler.getRows() * 9;
            if (!checkOpening(handler)) {
                return;
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
                if (stack.isEmpty() || !currentDest.category.equals(routedCategory(stack))) {
                    continue;
                }
                leftover = true;
                if (!hasRoomFor(handler, containerSlots, stack)) {
                    break;
                }
                mc.interactionManager.clickSlot(handler.syncId, SorterInventory.handlerSlotOf(containerSlots, inv), 0, SlotActionType.QUICK_MOVE, mc.player);
                clicks++;
                inventory.openingClicks++;
            }

            if (clicks == 0) {
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
        if (target == Target.DEST && assignedReserve) {
            if (contentsSyncId != handler.syncId) {
                if (stateTicks > openTimeout.get() + actionDelay.get()) {
                    assignedReserve = false;
                    abandonOpening("Reserve contents did not arrive");
                    closeThen();
                }
                return false;
            }
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
        if (previous != null && (target == Target.INPUT ? occupied > previous : occupied < previous)) {
            abandonOpening("the server undid our clicks");
            if (handler.getCursorStack().isEmpty()) {
                closeThen();
            } else if (dryRun.get()) {
                stopWithCursor();
            }
            return false;
        }
        return true;
    }

    private void abandonOpening(String reason) {
        warning("%s at %s, excluding this chest for this run.", reason, targetPos.toShortString());
        excludedChests.add(SorterStore.pos(targetPos));
        inventory.closingRequested = true;
    }

    void stopWithCursor() {
        warning("Could not safely empty the cursor. Sorter stopped; empty it manually before closing the screen.");
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
            if (++cursorReappearances > 3 || dryRun.get()
                || !(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler)) {
                stopWithCursor();
            } else {
                inventory.closingRequested = true;
                setState(target == Target.INPUT ? State.TAKE : State.DEPOSIT);
            }
            return;
        }
        mc.player.closeHandledScreen();
        timer = actionDelay.get();
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
            boolean deliverNow = !dryRun.get() && !cargoCategories().isEmpty()
                && (deliverBeforePurifying || inventory.freeCargoSlots() == 0 || exhaustedInputs.contains(SorterStore.pos(targetPos))
                    || excludedChests.contains(SorterStore.pos(targetPos)));
            setState(deliverNow ? State.PICK_DEST : State.PICK_INPUT);
            deliverBeforePurifying = false;
        } else {
            setState(State.PICK_DEST);
        }
    }

    private void finish() {
        if (dryRun.get()) {
            info("Dry run report (%d kinds):", report.size());
            report.forEach((k, v) -> info("  %dx %s", v, k));
            info("Turn off dry-run to actually move things.");
        } else {
            info("Done: nothing routable left in the input chests.");
        }
        SorterStore.save();
        toggle();
    }

    private String categoryOf(ItemStack stack) {
        SorterProfile profile = SorterStore.get();
        if (isShulker(stack)) {
            return classifyShulker(stack, profile.rules).category();
        }
        if (!sortLoose.get()) {
            return null;
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

    ShulkerClassifier.Result classifyShulker(ItemStack stack, CategoryRules rules) {
        List<ShulkerClassifier.Slot> slots = new ArrayList<>();
        for (ItemStack inner : ShulkerDataParser.parseShulkerContentsAsList(stack)) {
            slots.add(new ShulkerClassifier.Slot(Registries.ITEM.getId(inner.getItem()).toString(), inner.getCount()));
        }
        return ShulkerClassifier.classify(slots, rules, purity.get());
    }

    private String describe(ItemStack stack) {
        if (isShulker(stack)) {
            return "shulker -> " + classifyShulker(stack, SorterStore.get().rules).describe();
        }
        return Registries.ITEM.getId(stack.getItem()).getPath() + " -> " + SorterStore.get().rules.categoryOf(Registries.ITEM.getId(stack.getItem()).toString());
    }

    static boolean isShulker(ItemStack stack) {
        return stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    void disablePurification(String reason) {
        if (!purificationDisabled) {
            purificationDisabled = true;
            warning("Purification disabled for this run: %s.", reason);
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
            warning("Pause exceeded two minutes (%s); sorter stopped.", pauseReason);
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

    boolean isPurifying() {
        return state == State.PURIFY;
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
        if (state == State.PURIFY) return purifier.statusLine();
        return state + (targetPos == null ? "" : " @ " + targetPos.toShortString())
            + (currentDest == null ? "" : " [" + currentDest.category + "]");
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
