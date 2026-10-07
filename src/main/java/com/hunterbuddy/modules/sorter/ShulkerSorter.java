package com.hunterbuddy.modules.sorter;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalGetToBlock;
import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.util.BaritoneHelper;
import com.hunterbuddy.modules.regear.util.RotationUtils;
import com.hunterbuddy.modules.regear.util.ShulkerDataParser;
import com.hunterbuddy.modules.sorter.logic.CategoryRules;
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
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
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

    private final Setting<Boolean> dryRun = sgSort.add(new BoolSetting.Builder()
        .name("dry-run")
        .description("Open the input chests and report what would go where, without moving anything.")
        .defaultValue(true)
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
        IDLE, PICK_INPUT, GOTO, OPEN, TAKE, PICK_DEST, DEPOSIT, CLOSE, DONE

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
    private int openingClicks;
    private int recoveryClicks;
    private int cursorReappearances;
    private int cursorSourceSlot = -1;
    private boolean openingChecked;
    private boolean closingRequested;

    private final Set<Pos> exhaustedInputs = new HashSet<>();
    private final Set<Pos> excludedChests = new HashSet<>();
    private final Map<Pos, Integer> occupiedOnClose = new LinkedHashMap<>();

    private final Set<Integer> protectedSlots = new HashSet<>();
    private final Map<String, Integer> report = new LinkedHashMap<>();

    public ShulkerSorter() {
        super(HunterBuddyAddon.LOGISTICS_CATEGORY, "shulker-sorter",
            "Sorts shulkers and items from input chests into per-category chests (set up with .sorter).");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }
        SorterProfile profile = SorterStore.get();
        if (profile.inputs.isEmpty()) {
            error("No input chest. Look at one and run .sorter input");
            toggle();
            return;
        }
        if (!dryRun.get() && profile.destinations.isEmpty()) {
            error("No destination. Look at a chest and run .sorter dest <category>");
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

        protectedSlots.clear();
        for (int i = 0; i < 36; i++) {
            if (!mc.player.getInventory().getStack(i).isEmpty()) {
                protectedSlots.add(i);
            }
        }
        exhaustedInputs.clear();
        excludedChests.clear();
        occupiedOnClose.clear();
        report.clear();
        timer = 0;
        currentDest = null;
        targetPos = null;
        cursorSourceSlot = -1;
        setState(State.PICK_INPUT);
        info("Started%s. %d input chest(s), %d destination(s).",
            dryRun.get() ? " (dry run)" : "", profile.inputs.size(), profile.destinations.size());
    }

    @Override
    public void onDeactivate() {
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
        stateTicks++;
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

    private void pickInput() {
        SorterProfile profile = SorterStore.get();

        if (!dryRun.get() && freeCargoSlots() == 0) {
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
                currentDest = choice.destination();
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
        retries = 0;
        pathRelaunches = 0;
        openings = 0;
        if (!inReach(pos)) {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(pos));
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
            baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(targetPos));
        }
    }

    private void tickOpen() {
        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
            if (++openings > 3) {
                warning("Container at %s keeps closing, skipping it.", targetPos.toShortString());
                skipTarget();
                return;
            }
            openingClicks = 0;
            recoveryClicks = 0;
            cursorReappearances = 0;
            openingChecked = false;
            closingRequested = false;
            if (mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                cursorSourceSlot = -1;
            }
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

    private void interact(BlockPos pos) {
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
            closingRequested = true;
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
        int clicksBefore = openingClicks;
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
                if (!closingRequested && openingClicks >= clickLimit(handler)) {
                    abandonOpening("Too many clicks");
                }
                if (closingRequested) {
                    if (handler.getCursorStack().isEmpty()) {
                        closeThen();
                        return;
                    }
                    if (!emptyCursor(handler)) {
                        return;
                    }
                    continue;
                }
                if (!handler.getCursorStack().isEmpty()) {
                    if (!emptyCursor(handler)) {
                        return;
                    }
                    continue;
                }
                cursorSourceSlot = -1;
                int source = -1;
                for (int i = 0; i < containerSlots; i++) {
                    ItemStack stack = handler.getSlot(i).getStack();
                    if (!stack.isEmpty() && routedCategory(stack) != null) {
                        source = i;
                        break;
                    }
                }
                if (source == -1 || firstEmptyCargoSlot(handler) == -1) {
                    if (source == -1) {
                        exhaustedInputs.add(SorterStore.pos(targetPos));
                    }
                    closeThen();
                    return;
                }
                cursorSourceSlot = source;
                mc.interactionManager.clickSlot(handler.syncId, source, 0, SlotActionType.PICKUP, mc.player);
                openingClicks++;
            }
        } finally {
            if (openingClicks > clicksBefore) {
                timer = clickDelay.get();
            }
        }
    }

    private void tickDeposit() {
        int clicksBefore = openingClicks;
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
                if (!closingRequested && openingClicks >= clickLimit(handler)) {
                    abandonOpening("Too many clicks");
                }
                if (closingRequested || !handler.getCursorStack().isEmpty()) {
                    if (!handler.getCursorStack().isEmpty()) {
                        emptyCursor(handler);
                    } else {
                        closeThen();
                    }
                    return;
                }
                if (!isCargoSlot(inv)) {
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
                mc.interactionManager.clickSlot(handler.syncId, handlerSlotOf(containerSlots, inv), 0, SlotActionType.QUICK_MOVE, mc.player);
                clicks++;
                openingClicks++;
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
            if (openingClicks > clicksBefore) {
                timer = clickDelay.get();
            }
        }
    }

    private static int handlerSlotOf(int containerSlots, int inv) {
        return inv < 9 ? containerSlots + 27 + inv : containerSlots + (inv - 9);
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

    private static int clickLimit(GenericContainerScreenHandler handler) {
        return 2 * (handler.getRows() * 9 + 36);
    }

    private void abandonOpening(String reason) {
        warning("%s at %s, excluding this chest for this run.", reason, targetPos.toShortString());
        excludedChests.add(SorterStore.pos(targetPos));
        closingRequested = true;
    }

    private int firstEmptyCargoSlot(GenericContainerScreenHandler handler) {
        int containerSlots = handler.getRows() * 9;
        for (int inv = 9; inv < 36; inv++) {
            int slot = handlerSlotOf(containerSlots, inv);
            if (isCargoSlot(inv) && handler.getSlot(slot).getStack().isEmpty()) {
                return slot;
            }
        }
        if (useHotbar.get()) {
            for (int inv = 0; inv < 9; inv++) {
                int slot = handlerSlotOf(containerSlots, inv);
                if (isCargoSlot(inv) && handler.getSlot(slot).getStack().isEmpty()) {
                    return slot;
                }
            }
        }
        return -1;
    }

    private boolean emptyCursor(GenericContainerScreenHandler handler) {
        if (recoveryClicks >= clickLimit(handler)) {
            stopWithCursor();
            return false;
        }
        int slot = firstEmptyCargoSlot(handler);
        if (slot == -1) {
            slot = cursorSourceSlot;
            closingRequested = true;
        }
        if (slot < 0 || slot >= handler.slots.size()) {
            stopWithCursor();
            return false;
        }
        mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
        openingClicks++;
        if (closingRequested) {
            recoveryClicks++;
            if (handler.getCursorStack().isEmpty()) {
                closeThen();
                return false;
            }
        }
        return true;
    }

    private void stopWithCursor() {
        warning("Could not safely empty the cursor. Sorter stopped; empty it manually before closing the screen.");
        toggle();
    }

    private void closeThen() {
        closingRequested = true;
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
                closingRequested = true;
                setState(target == Target.INPUT ? State.TAKE : State.DEPOSIT);
            }
            return;
        }
        mc.player.closeHandledScreen();
        timer = actionDelay.get();
        if (target == Target.INPUT) {
            boolean deliverNow = !dryRun.get() && !cargoCategories().isEmpty()
                && (freeCargoSlots() == 0 || exhaustedInputs.contains(SorterStore.pos(targetPos))
                    || excludedChests.contains(SorterStore.pos(targetPos)));
            setState(deliverNow ? State.PICK_DEST : State.PICK_INPUT);
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
        return choice == null ? null : choice.destination().category;
    }

    private record DestinationChoice(Destination destination, Pos chest) {
    }

    private DestinationChoice resolveDestination(SorterProfile profile, String category) {
        List<Destination> destinations = profile.destinationsFor(category);
        if (destinations.isEmpty() && fallbackRandom.get() && !CategoryRules.MIXED.equals(category)) {
            destinations = profile.destinationsFor(CategoryRules.FALLBACK);
        }
        for (Destination destination : destinations) {
            for (Pos chest : destination.chests) {
                if (!destination.full.contains(chest) && !excludedChests.contains(chest)) {
                    return new DestinationChoice(destination, chest);
                }
            }
        }
        return null;
    }

    private ShulkerClassifier.Result classifyShulker(ItemStack stack, CategoryRules rules) {
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

    private static boolean isShulker(ItemStack stack) {
        return stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    private boolean isCargoSlot(int inv) {
        return !protectedSlots.contains(inv) && (inv >= 9 || useHotbar.get());
    }

    private int freeCargoSlots() {
        int free = 0;
        for (int i = 0; i < 36; i++) {
            if (isCargoSlot(i) && mc.player.getInventory().getStack(i).isEmpty()) {
                free++;
            }
        }
        return free;
    }

    private Set<String> cargoCategories() {
        Set<String> out = new LinkedHashSet<>();
        for (int i = 0; i < 36; i++) {
            if (!isCargoSlot(i)) {
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

    private boolean inReach(BlockPos pos) {
        return mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) <= REACH * REACH;
    }

    private void stopPathing() {
        if (BaritoneHelper.isAvailable()) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
        }
    }

    private void setState(State next) {
        state = next;
        stateTicks = 0;
    }

    public String statusLine() {
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
