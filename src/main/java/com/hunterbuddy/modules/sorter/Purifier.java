package com.hunterbuddy.modules.sorter;

import baritone.api.pathing.goals.GoalBlock;
import com.hunterbuddy.modules.regear.util.RotationUtils;
import com.hunterbuddy.modules.sorter.logic.PurificationPlan;
import com.hunterbuddy.modules.sorter.logic.ShulkerClassifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static meteordevelopment.meteorclient.MeteorClient.mc;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class Purifier {
    public enum Result {
        RUNNING, DONE, FAILURE, STOP
    }

    private enum State {
        GOTO, PLACE, OPEN, EXTRACT, CLOSE, BREAK, PICKUP
    }

    private final ShulkerSorter owner;
    private final SorterInventory inventory;
    private final SorterSafety safety;
    private final Set<Integer> emptyBeforeBreak = new HashSet<>();
    private final Map<Integer, ItemStack> extracted = new LinkedHashMap<>();
    private PurificationPlan plan;
    private BlockPos pos;
    private State state;
    private int slot;
    private int ticks;
    private int jobTicks;
    private int index;
    private int timer;
    private boolean placed;
    private boolean failed;
    private boolean contentsChecked;
    private String pauseReason;
    private long pauseStarted;

    Purifier(ShulkerSorter owner, SorterInventory inventory, SorterSafety safety) {
        this.owner = owner;
        this.inventory = inventory;
        this.safety = safety;
    }

    public static boolean validStation(BlockPos support) {
        BlockPos box = support.up();
        return mc.world != null && !mc.world.getBlockState(support).getCollisionShape(mc.world, support).isEmpty()
            && mc.world.getBlockState(box).isReplaceable()
            && mc.world.isSpaceEmpty(new Box(box).contract(1.0E-6))
            && mc.world.isSpaceEmpty(ShulkerEntity.calculateBoundingBox(1.0F, Direction.UP, 0.0F, 0.5F,
                box.toBottomCenterPos()).contract(1.0E-6))
            && standingPosition(box) != null;
    }

    private static BlockPos standingPosition(BlockPos box) {
        if (mc.world == null || mc.player == null) {
            return null;
        }
        BlockPos best = null;
        double distance = Double.MAX_VALUE;
        for (Direction side : Direction.Type.HORIZONTAL) {
            BlockPos feet = box.offset(side);
            BlockPos ground = feet.down();
            if (!mc.world.getBlockState(ground).isSolidBlock(mc.world, ground)
                || !mc.world.isSpaceEmpty(mc.player, new Box(feet).stretch(0, 1, 0).contract(1.0E-6))) {
                continue;
            }
            double candidate = mc.player.squaredDistanceTo(Vec3d.ofCenter(feet));
            if (candidate < distance) {
                distance = candidate;
                best = feet;
            }
        }
        return best;
    }

    static PurificationPlan plan(ItemStack stack) {
        DefaultedList<ItemStack> contents = DefaultedList.ofSize(27, ItemStack.EMPTY);
        stack.getOrDefault(DataComponentTypes.CONTAINER, ContainerComponent.DEFAULT).copyTo(contents);
        return plan(contents);
    }

    private static PurificationPlan plan(List<ItemStack> contents) {
        List<ShulkerClassifier.Slot> slots = new ArrayList<>();
        for (ItemStack stack : contents) {
            slots.add(new ShulkerClassifier.Slot(Registries.ITEM.getId(stack.getItem()).toString(), stack.getCount()));
        }
        return PurificationPlan.create(slots, SorterStore.get().rules);
    }

    boolean needsRecovery() {
        if (SorterStore.get().unpackStation == null) {
            return false;
        }
        BlockPos station = SorterStore.blockPos(SorterStore.get().unpackStation).up();
        return mc.world.getBlockState(station).getBlock() instanceof ShulkerBoxBlock;
    }

    void start(PurificationPlan plan, int slot, int delay) {
        this.plan = plan;
        this.slot = slot;
        timer = delay;
        pos = SorterStore.blockPos(SorterStore.get().unpackStation).up();
        placed = mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock;
        if (placed) {
            owner.warning("Recovering the shulker left at %s.", pos.toShortString());
        }
        failed = placed && plan != null;
        contentsChecked = false;
        pauseReason = null;
        pauseStarted = 0;
        jobTicks = 0;
        index = 0;
        extracted.clear();
        emptyBeforeBreak.clear();
        inventory.resetClicks();
        enter(State.GOTO);
        walkToStation();
    }

    Result tick() {
        if (pauseReason != null && System.nanoTime() - pauseStarted >= SorterSafety.PAUSE_LIMIT_NANOS) {
            return stop("Pause exceeded two minutes: " + pauseReason);
        }
        if (state == State.PLACE || state == State.BREAK) {
            String reason = safety.visiblePlayerReason(owner.ignoreFriends());
            if (reason != null) {
                long now = System.nanoTime();
                if (pauseReason == null) {
                    pauseStarted = now;
                    owner.stopPathing();
                    if (state == State.BREAK) mc.interactionManager.cancelBlockBreaking();
                }
                pauseReason = reason;
                return Result.RUNNING;
            }
        }
        pauseReason = null;
        ticks++;
        jobTicks++;
        if (!placed && mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock) {
            placed = true;
        }
        if (jobTicks > globalLimit()) {
            return stop("Job deadline reached");
        }
        if (ticks > stateLimit()) {
            if (state == State.BREAK || state == State.PICKUP || state == State.CLOSE) {
                return stop("Recovery deadline reached");
            }
            if (!placed) {
                if (state == State.GOTO) {
                    return stationFailure("Station could not be reached");
                }
                if (state == State.PLACE && mc.player.getInventory().getStack(slot).isEmpty()) {
                    return stop("Placement was not confirmed");
                }
                if (!validStation(pos.down())) {
                    return stationFailure("Station obstructed");
                }
                return fail("Station deadline reached");
            }
            recover("Station deadline reached");
        }
        if (timer > 0) {
            timer--;
            return Result.RUNNING;
        }
        switch (state) {
            case GOTO -> {
                BlockPos standing = standingPosition(pos);
                if (!placed && standing == null) {
                    return stationFailure("Station has no accessible side");
                }
                if ((placed || mc.player.getBlockPos().equals(standing))
                    && owner.inReach(pos) && owner.inReach(pos.down())
                    && !mc.player.getBoundingBox().intersects(new Box(pos))) {
                    owner.stopPathing();
                    enter(placed ? State.CLOSE : State.PLACE);
                } else if (ticks % 40 == 0) {
                    walkToStation();
                }
            }
            case PLACE -> {
                if (mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock) {
                    placed = true;
                    enter(State.OPEN);
                    timer = owner.actionDelay();
                } else if (ticks == 1) {
                    if (nearestDrop() != null) {
                        return stop("Uncollected shulker near the station");
                    }
                    ItemStack stack = mc.player.getInventory().getStack(slot);
                    if (!validStation(pos.down())) {
                        return stationFailure("Station obstructed; keeping normal routing");
                    }
                    if (!ShulkerSorter.isShulker(stack) || !plan.equals(plan(stack))) {
                        return fail("Source changed; keeping normal routing");
                    }
                    mc.player.getInventory().setSelectedSlot(slot);
                    Vec3d hit = Vec3d.ofCenter(pos.down()).add(0, 0.5, 0);
                    rotate(hit, true);
                    PlayerInput previous = mc.player.input.playerInput;
                    PlayerInput sneaking = new PlayerInput(previous.forward(), previous.backward(), previous.left(),
                        previous.right(), previous.jump(), true, previous.sprint());
                    mc.player.input.playerInput = sneaking;
                    mc.player.networkHandler.sendPacket(new PlayerInputC2SPacket(sneaking));
                    try {
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                            new BlockHitResult(hit, Direction.UP, pos.down(), false));
                    } finally {
                        mc.player.input.playerInput = previous;
                        mc.player.networkHandler.sendPacket(new PlayerInputC2SPacket(previous));
                    }
                }
            }
            case OPEN -> {
                if (mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler) {
                    inventory.resetClicks();
                    enter(State.EXTRACT);
                    timer = owner.actionDelay();
                } else if ((ticks - owner.actionDelay() - 1) % owner.openTimeout() == 0) {
                    owner.interact(pos);
                }
            }
            case EXTRACT -> extract();
            case CLOSE -> {
                if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                    if (!(mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler handler)) {
                        return stop("Cursor could not be recovered");
                    }
                    inventory.emptyCursor(handler);
                    timer = owner.clickDelay();
                } else if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
                    mc.player.closeHandledScreen();
                    timer = owner.actionDelay();
                } else {
                    if (!(mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock) && sourceReturned()) {
                        return finishReturnedSource();
                    }
                    emptyBeforeBreak.clear();
                    for (int i = 0; i < 36; i++) {
                        if (mc.player.getInventory().getStack(i).isEmpty()) {
                            emptyBeforeBreak.add(i);
                        }
                    }
                    if (emptyBeforeBreak.isEmpty()) {
                        return stop("No room to collect the shulker");
                    }
                    BlockState block = mc.world.getBlockState(pos);
                    int best = slot >= 0 && mc.player.getInventory().getStack(slot).isEmpty()
                        ? slot : mc.player.getInventory().getSelectedSlot();
                    for (int i = 0; i < 9; i++) {
                        if (mc.player.getInventory().getStack(i).isEmpty()) {
                            best = i;
                            break;
                        }
                    }
                    float speed = 1;
                    for (int i = 0; i < 9; i++) {
                        float candidate = mc.player.getInventory().getStack(i).getMiningSpeedMultiplier(block);
                        if (candidate > speed) {
                            speed = candidate;
                            best = i;
                        }
                    }
                    mc.player.getInventory().setSelectedSlot(best);
                    enter(State.BREAK);
                }
            }
            case BREAK -> {
                if (!(mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock)) {
                    enter(State.PICKUP);
                } else {
                    rotate(Vec3d.ofCenter(pos), ticks == 1);
                    if (ticks == 1) {
                        mc.interactionManager.attackBlock(pos, Direction.UP);
                    } else {
                        mc.interactionManager.updateBlockBreakingProgress(pos, Direction.UP);
                    }
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
            }
            case PICKUP -> {
                if (mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock) {
                    recover("Server restored the shulker block");
                    break;
                }
                if (sourceReturned()) {
                    return finishReturnedSource();
                }
                for (int i : emptyBeforeBreak) {
                    ItemStack stack = mc.player.getInventory().getStack(i);
                    if (!ShulkerSorter.isShulker(stack)) {
                        continue;
                    }
                    inventory.acceptRecovered(i);
                    owner.stopPathing();
                    if (owner.classifyShulker(stack, SorterStore.get().rules).isMixed()) {
                        owner.warning("Recovered shulker is still mixed; using normal routing.");
                    }
                    return failed ? Result.FAILURE : Result.DONE;
                }
                if (ticks >= 10 && ticks % 20 == 10) {
                    ItemEntity nearest = nearestDrop();
                    if (nearest != null) {
                        walk(nearest.getBlockPos());
                    }
                }
            }
        }
        return owner.isActive() ? Result.RUNNING : Result.STOP;
    }

    private boolean sourceReturned() {
        if (slot < 0 || plan == null) {
            return false;
        }
        ItemStack stack = mc.player.getInventory().getStack(slot);
        return ShulkerSorter.isShulker(stack) && plan.equals(plan(stack));
    }

    private Result finishReturnedSource() {
        inventory.acceptRecovered(slot);
        return fail("Placement cancelled; shulker returned to inventory, using normal routing");
    }

    private void extract() {
        if (!(mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler handler)) {
            recover("Station screen closed");
            return;
        }
        if (!contentsChecked) {
            List<ItemStack> contents = new ArrayList<>();
            for (int i = 0; i < 27; i++) {
                contents.add(handler.getSlot(i).getStack());
            }
            contentsChecked = true;
            if (!plan.equals(plan(contents))) {
                recover("Source contents changed");
                return;
            }
        }
        for (Map.Entry<Integer, ItemStack> entry : extracted.entrySet()) {
            if (!ItemStack.areEqual(entry.getValue(), handler.getSlot(entry.getKey()).getStack())) {
                recover("Server undid extraction clicks");
                return;
            }
        }
        for (int i = 0; i < index; i++) {
            if (!handler.getSlot(plan.extractionSlots().get(i)).getStack().isEmpty()) {
                recover("Server restored extracted contents");
                return;
            }
        }
        int before = inventory.openingClicks;
        for (int clicks = 0; clicks < owner.clicksPerTick(); clicks++) {
            if (inventory.openingClicks >= SorterInventory.clickLimit(handler)) {
                recover("Too many extraction clicks");
                break;
            }
            if (!handler.getCursorStack().isEmpty()) {
                int destination = inventory.firstEmptyCargoSlot(handler);
                if (destination == -1) {
                    recover("Cargo space changed");
                    break;
                }
                ItemStack stack = handler.getCursorStack().copy();
                if (!inventory.emptyCursor(handler)) {
                    break;
                }
                extracted.put(destination, stack);
            } else if (index == plan.requiredCargoSlots()) {
                enter(State.CLOSE);
                break;
            } else {
                int source = plan.extractionSlots().get(index);
                if (handler.getSlot(source).getStack().isEmpty()) {
                    index++;
                    clicks--;
                    continue;
                }
                inventory.cursorSourceSlot = source;
                mc.interactionManager.clickSlot(handler.syncId, source, 0, SlotActionType.PICKUP, mc.player);
                inventory.openingClicks++;
            }
        }
        if (inventory.openingClicks > before) {
            timer = owner.clickDelay();
        }
    }

    private int stateLimit() {
        return switch (state) {
            case GOTO -> owner.pathTimeout() * 20;
            case PLACE -> 60;
            case OPEN -> owner.openTimeout() * 3 + owner.actionDelay();
            case EXTRACT, CLOSE -> 126 * (owner.clickDelay() + 1) + 2 * owner.actionDelay() + 40;
            case BREAK -> 200;
            case PICKUP -> 200;
        };
    }

    private int globalLimit() {
        return owner.pathTimeout() * 20 + 252 * (owner.clickDelay() + 1)
            + 3 * owner.openTimeout() + 5 * owner.actionDelay() + 600;
    }

    private void enter(State state) {
        this.state = state;
        ticks = 0;
    }

    private void walkToStation() {
        BlockPos standing = standingPosition(pos);
        if (standing != null) {
            walk(standing);
        }
    }

    private ItemEntity nearestDrop() {
        ItemEntity nearest = null;
        for (ItemEntity item : mc.world.getEntitiesByClass(ItemEntity.class, new Box(pos).expand(4),
            entity -> ShulkerSorter.isShulker(entity.getStack()))) {
            if (nearest == null
                || item.squaredDistanceTo(Vec3d.ofCenter(pos)) < nearest.squaredDistanceTo(Vec3d.ofCenter(pos))) {
                nearest = item;
            }
        }
        return nearest;
    }

    private void walk(BlockPos target) {
        owner.pathTo(new GoalBlock(target));
    }

    private void recover(String reason) {
        owner.warning("%s at %s; recovering the shulker.", reason, pos.toShortString());
        failed = true;
        enter(State.CLOSE);
    }

    private Result stationFailure(String reason) {
        owner.disablePurification(reason + " at " + pos.toShortString());
        owner.stopPathing();
        return Result.FAILURE;
    }

    private Result fail(String reason) {
        owner.warning("%s at %s.", reason, pos.toShortString());
        owner.stopPathing();
        return Result.FAILURE;
    }

    private Result stop(String reason) {
        owner.warning("%s; sorter stopped. Recover the shulker at %s.", reason, pos.toShortString());
        owner.stopPathing();
        return Result.STOP;
    }

    String statusLine() {
        if (pauseReason != null) return "PURIFY PAUSED: " + pauseReason + " @ " + pos.toShortString();
        return "PURIFY " + state + " @ " + pos.toShortString();
    }

    private void rotate(Vec3d hit, boolean sendPacket) {
        float yaw = RotationUtils.getYaw(hit);
        float pitch = RotationUtils.getPitch(hit);
        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
        if (sendPacket) {
            mc.player.networkHandler.sendPacket(new LookAndOnGround(yaw, pitch,
                mc.player.isOnGround(), mc.player.horizontalCollision));
        }
    }
}
