package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.Sensors;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.util.GliderSwap;
import com.hunterbuddy.util.Rockets;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.Consumer;
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
        performance = FlightPerformance.load(ctx);
        landing = null;
        failedLandings.clear();
        bestDistance = Double.POSITIVE_INFINITY;
        improvedAt = ctx.tick();
        altitude = Math.max(world.feet().getY() + 24, 96);
        if (world.client().world.getDimension().hasCeiling() && world.feet().getY() < 128) {
            altitude = Math.min(112, world.feet().getY() + 16);
        }
        altitude = Math.min(world.topY() - 8, altitude);
        if (!world.client().world.getWorldBorder().contains(destination)) {
            throw new IllegalStateException("flight destination outside world border");
        }
        if (atDestination()) return;
        int outward = estimatedRockets(distance(destination));
        double homeDistance = Math.hypot(destination.getX() - home.getX(), destination.getZ() - home.getZ());
        int required = returning ? outward + Math.max(8, outputs.flight().minimumRockets())
            : (int) Math.ceil(outward * 1.5) + reserveAt(homeDistance);
        if (world.countMatching(Rockets::isPlain) < required) {
            throw new IllegalStateException("flight budget missing: need " + required + " plain rockets including return reserve");
        }
        if (totalDurability() < wearRequired() || bestGlider().isEmpty()) {
            throw new IllegalStateException("carried elytra durability cannot cover flight and return reserve");
        }
        if (!GliderSwap.isChestPiece(world.client().player.getEquippedStack(EquipmentSlot.CHEST))
            && world.countMatching(GliderSwap::isChestPiece) == 0) {
            throw new IllegalStateException("chestplate missing for landing");
        }
        if (new InventoryLayout(world).freeSlot() < 0) {
            throw new IllegalStateException("flight needs a free inventory slot; nothing will be discarded");
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

    public int estimatedRockets(double metres) {
        return 2 + (int) Math.ceil(performance.rocketsPerKm() * metres / 1000);
    }

    private int reserveAt(double metres) {
        return Math.max(outputs.flight().minimumRockets(), 8 + (int) Math.ceil(estimatedRockets(metres) * 1.5));
    }

    public int rocketReserve() {
        return returning ? Math.max(8, outputs.flight().minimumRockets()) : reserveAt(distance(home));
    }

    public int wearRequired() {
        double outbound = performance.durabilityPerKm() * distance(destination) / 1000;
        double backDistance = Math.hypot(destination.getX() - home.getX(), destination.getZ() - home.getZ());
        double back = returning ? 0 : performance.durabilityPerKm() * backDistance / 1000 * 1.5;
        return 16 + (int) Math.ceil(outbound * 1.5 + back);
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
            if (GliderSwap.isGlider(stack) && stack.getCount() == 1) total += remaining(stack);
        }
        return total;
    }

    public int returnWear() {
        return 16 + (int) Math.ceil(performance.durabilityPerKm() * distance(home) / 1000 * 1.5);
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

    public static int remaining(ItemStack stack) {
        return stack.isEmpty() ? 0 : stack.isDamageable() ? Math.max(0, stack.getMaxDamage() - stack.getDamage() - 1) : 100000;
    }

    public double speed(Sensors sensors) {
        return Math.hypot(sensors.velocityX(), sensors.velocityZ());
    }
}
