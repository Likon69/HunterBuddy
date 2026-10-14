package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.OperationFailure;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.Sensors;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.WorldKnowledge;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.util.GliderSwap;
import com.hunterbuddy.util.Rockets;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

public final class FlightPlan {
    public static final Key<String> LAST_FAILURE = new Key<>("flight-last-failure", String.class, Scope.WORLD, true);
    public final WorldReader world;
    public final BaritoneBridge baritone;
    public final Outputs outputs;
    private final Supplier<BlockPos> destinationSource;
    private final Supplier<BlockPos> homeSource;
    public boolean returning;
    public FlightPerformance performance;
    public FlightBudget budget;
    public BlockPos destination;
    public BlockPos home;
    public BlockPos landing;
    public String worldKey;
    public double altitude;
    public double bestDistance = Double.POSITIVE_INFINITY;
    public long improvedAt;
    public boolean cruiseThrough;
    public Consumer<TickContext> observer = ctx -> {};
    public final Set<BlockPos> failedLandings = new HashSet<>();
    public final Set<BlockPos> failedTakeoffs = new HashSet<>();

    public FlightPlan(Supplier<BlockPos> destination, Supplier<BlockPos> home, boolean returning,
                      WorldReader world, BaritoneBridge baritone, Outputs outputs) {
        destinationSource = destination;
        homeSource = home;
        this.returning = returning;
        this.world = world;
        this.baritone = baritone;
        this.outputs = outputs;
    }

    public void initialize(TickContext ctx) {
        destination = destinationSource.get().toImmutable();
        home = homeSource.get().toImmutable();
        worldKey = world.storageKey();
        WorldKnowledge knowledge = WorldKnowledge.load(ctx);
        if (knowledge.biomeBase == null) knowledge.biomeBounds(home, 3000);
        performance = FlightPerformance.load(ctx);
        budget = new FlightBudget(performance, outputs.flight().minimumRockets());
        landing = null;
        failedLandings.clear();
        failedTakeoffs.clear();
        bestDistance = Double.POSITIVE_INFINITY;
        improvedAt = ctx.tick();
        altitude = Math.max(world.feet().getY() + 24, 96);
        if (world.client().world.getDimension().hasCeiling() && world.feet().getY() < 128) {
            altitude = Math.min(112, world.feet().getY() + 16);
        }
        altitude = Math.min(world.topY() - 8, altitude);
        if (!world.client().world.getWorldBorder().contains(destination)) {
            throw new OperationFailure(FailureCode.REFUSED, "flight destination outside world border");
        }
        if (atDestination()) return;
        double homeDistance = Math.hypot(destination.getX() - home.getX(), destination.getZ() - home.getZ());
        int required = budget.tripRockets(distance(destination), homeDistance, returning);
        if (world.countMatching(Rockets::isPlain) < required) {
            throw new OperationFailure(FailureCode.BUDGET, "flight budget missing: need " + required + " plain rockets including return reserve");
        }
        if (world.countMatching(GliderSwap::isGlider) == 0) {
            throw new OperationFailure(FailureCode.MISSING_ITEM, "elytra missing for flight");
        }
        if (totalDurability() < wearRequired() || bestGlider().isEmpty()) {
            throw new OperationFailure(FailureCode.BUDGET, "carried elytra durability cannot cover flight and return reserve");
        }
        if (!GliderSwap.isChestPiece(world.client().player.getEquippedStack(EquipmentSlot.CHEST))
            && world.countMatching(GliderSwap::isChestPiece) == 0) {
            throw new OperationFailure(FailureCode.MISSING_ITEM, "chestplate missing for landing");
        }
        if (new InventoryLayout(world).freeSlot() < 0) {
            throw new OperationFailure(FailureCode.MISSING_ITEM, "flight needs a free inventory slot; nothing will be discarded");
        }
    }

    public double distance(BlockPos point) {
        return Math.hypot(point.getX() + 0.5 - world.client().player.getX(), point.getZ() + 0.5 - world.client().player.getZ());
    }

    public void beginReturn(TickContext ctx) {
        returning = true;
        destination = home;
        bestDistance = distance(home);
        improvedAt = ctx.tick();
        cruiseThrough = false;
    }

    public int rocketReserve() {
        return returning ? 0 : budget.reserve(distance(home));
    }

    public int wearRequired() {
        double backDistance = Math.hypot(destination.getX() - home.getX(), destination.getZ() - home.getZ());
        return budget.tripWear(distance(destination), backDistance, returning);
    }

    public ItemStack bestGlider() {
        ItemStack best = ItemStack.EMPTY;
        for (int i = 0; i < world.client().player.getInventory().size(); i++) {
            ItemStack stack = world.client().player.getInventory().getStack(i);
            if (GliderSwap.isGlider(stack) && stack.getCount() == 1 && remaining(stack) > 16
                && (best.isEmpty() || remaining(stack) > remaining(best))) best = stack.copy();
        }
        return best;
    }

    public boolean renewalNeeded() {
        ItemStack worn = world.client().player.getEquippedStack(EquipmentSlot.CHEST);
        ItemStack best = bestGlider();
        return !GliderSwap.isGlider(worn) || remaining(worn) <= 16
            || remaining(worn) < Math.max(64, returnWear()) && remaining(best) > remaining(worn);
    }

    public int totalDurability() {
        return totalDurability(world);
    }

    public static int totalDurability(WorldReader world) {
        int total = 0;
        for (int i = 0; i < world.client().player.getInventory().size(); i++) {
            ItemStack stack = world.client().player.getInventory().getStack(i);
            if (GliderSwap.isGlider(stack) && stack.getCount() == 1) total += usableDurability(stack);
        }
        return total;
    }

    public int returnWear() {
        return budget.returnWear(distance(home));
    }

    public boolean nearDestination() {
        return !cruiseThrough && distance(destination) <= 96 && world.loaded(destination);
    }

    public boolean atDestination() {
        return !cruiseThrough && world.client().player.isOnGround() && distance(destination) <= 12
            && FlightTerrain.landing(world.feet(), world);
    }

    public boolean maintenanceNeeded() {
        return renewalNeeded() || world.findSlot(Rockets::isPlain) < 0 || !world.client().player.isGliding();
    }

    public boolean nativeMovement() {
        BaritoneSnapshot nativeState = baritone.capture();
        return nativeState.inControl() || nativeState.pathing() || nativeState.calcInProgress();
    }

    public static int usableDurability(ItemStack stack) {
        return Math.max(0, remaining(stack) - 16);
    }

    public static int remaining(ItemStack stack) {
        return stack.isEmpty() ? 0 : stack.isDamageable() ? Math.max(0, stack.getMaxDamage() - stack.getDamage() - 1) : 100000;
    }

    public double speed(Sensors sensors) {
        return Math.hypot(sensors.velocityX(), sensors.velocityZ());
    }
}
