package com.hunterbuddy.behavior.mc;

import baritone.api.pathing.goals.GoalXZ;
import com.hunterbuddy.behavior.core.TargetPoint;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.modules.AutoFlyingRegear;
import com.hunterbuddy.modules.AutoPortal;
import com.hunterbuddy.modules.ElytraBounce;
import com.hunterbuddy.modules.ElytraRecast;
import com.hunterbuddy.modules.MlepAirPlace;
import com.hunterbuddy.modules.MlepScaffold;
import com.hunterbuddy.modules.Pitch40;
import com.hunterbuddy.modules.Pitch40Classic;
import com.hunterbuddy.modules.RocketFly;
import com.hunterbuddy.modules.SpawnerDetector;
import com.hunterbuddy.modules.TrailFollower;
import com.hunterbuddy.modules.WaypointFollower;
import com.hunterbuddy.modules.logistics.PearlButler;
import com.hunterbuddy.modules.regear.arealoader.AreaLoader;
import com.hunterbuddy.modules.sorter.ShulkerSorter;
import com.hunterbuddy.modules.tradingsystem.ExperienceTraderModule;
import com.hunterbuddy.modules.tradingsystem.ExperienceTraderStarterModule;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

public final class WorldReader {
    private static final Set<Class<? extends Module>> REFUSED = Set.of(SpawnerDetector.class, WaypointFollower.class,
        TrailFollower.class, AreaLoader.class, RocketFly.class, Pitch40.class, Pitch40Classic.class, ElytraRecast.class,
        ElytraBounce.class, AutoPortal.class, MlepScaffold.class, MlepAirPlace.class, PearlButler.class, ShulkerSorter.class,
        ExperienceTraderModule.class, ExperienceTraderStarterModule.class);
    private final MinecraftClient client;

    public WorldReader(MinecraftClient client) {
        this.client = Objects.requireNonNull(client);
    }

    public boolean available() {
        return client.world != null && client.player != null && client.interactionManager != null;
    }

    public WorldIdentity identity() {
        return WorldIdentity.capture(client);
    }

    public String storageKey() {
        if (!available()) throw new IllegalStateException("Minecraft world is unavailable");
        String server = client.getCurrentServerEntry() != null ? client.getCurrentServerEntry().address
            : client.getServer() != null ? "sp:" + client.getServer().getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize() : null;
        if (server == null) throw new IllegalStateException("server identity is unavailable");
        return server + "/" + identity().dimensionKey();
    }

    public boolean loadedRadiusOne() {
        if (!available() || !client.player.isAlive()) return false;
        BlockPos feet = feet();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (!client.world.getChunkManager().isChunkLoaded((feet.getX() >> 4) + dx, (feet.getZ() >> 4) + dz)) return false;
        }
        return true;
    }

    public boolean elytraEquipped() {
        return available() && LivingEntity.canGlideWith(client.player.getEquippedStack(EquipmentSlot.CHEST), EquipmentSlot.CHEST);
    }

    MinecraftClient client() {
        return client;
    }

    public BlockPos feet() {
        if (!available()) throw new IllegalStateException("Minecraft world is unavailable");
        return client.player.getBlockPos();
    }

    public boolean loaded(BlockPos pos) {
        return available() && client.world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
    }

    public boolean inBounds(BlockPos pos) {
        return available() && pos.getY() >= client.world.getBottomY() && pos.getY() <= client.world.getTopYInclusive()
            && client.world.getWorldBorder().contains(pos);
    }

    public BlockState state(BlockPos pos) {
        if (!loaded(pos)) throw new IllegalStateException("Block chunk is not loaded: " + pos);
        return client.world.getBlockState(pos);
    }

    public boolean cleared(BlockPos pos) {
        BlockState state = state(pos);
        return state.isAir() || state.getBlock() instanceof FluidBlock;
    }

    public float hardness(BlockPos pos) {
        return state(pos).getHardness(client.world, pos);
    }

    public boolean openDryNeighbor(BlockPos column) {
        for (var direction : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
            BlockPos neighbor = column.offset(direction);
            if (loaded(neighbor) && state(neighbor).isAir() && state(neighbor.up()).isAir()) return true;
        }
        return false;
    }

    public boolean loaded(TargetPoint point) {
        return available() && client.world.getChunkManager().isChunkLoaded((int) Math.floor(point.x()) >> 4, (int) Math.floor(point.z()) >> 4);
    }

    public TargetPoint eyes() {
        if (!available()) throw new IllegalStateException("Minecraft world is unavailable");
        Vec3d eyes = client.player.getEyePos();
        return new TargetPoint(eyes.x, eyes.y, eyes.z);
    }

    public int findSlot(Predicate<ItemStack> filter) {
        Objects.requireNonNull(filter);
        if (!available()) return -1;
        for (int slot = 0; slot < 9; slot++) {
            if (filter.test(client.player.getInventory().getStack(slot))) return slot;
        }
        return -1;
    }

    public int count(Item item) {
        if (!available()) return 0;
        int count = 0;
        for (int slot = 0; slot < client.player.getInventory().size(); slot++) {
            ItemStack stack = client.player.getInventory().getStack(slot);
            if (stack.isOf(item)) count += stack.getCount();
        }
        return count;
    }

    public int countMatching(Predicate<ItemStack> filter) {
        if (!available()) return 0;
        int count = 0;
        for (int slot = 0; slot < client.player.getInventory().size(); slot++) {
            ItemStack stack = client.player.getInventory().getStack(slot);
            if (filter.test(stack)) count += stack.getCount();
        }
        return count;
    }

    public BlockPos surface(int x, int z) {
        BlockPos column = new BlockPos(x, feet().getY(), z);
        if (!loaded(column)) return null;
        BlockPos point = new BlockPos(x, client.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z), z);
        if (!inBounds(point.up(2)) || !state(point).isAir() || !state(point.up()).isAir() || !state(point.up(2)).isAir()) return null;
        return state(point.down()).getFluidState().isEmpty() && !cleared(point.down()) ? point : null;
    }

    public boolean matchesSlot(int slot, Predicate<ItemStack> filter) {
        return available() && slot >= 0 && slot < 9 && filter.test(client.player.getInventory().getStack(slot));
    }

    public String refusal(Set<Resource> resources, BaritoneSnapshot baritone, boolean ownBaritone) {
        if (!available()) return "Minecraft world is unavailable";
        if (resources.contains(Resource.MOVEMENT) && !baritone.available()) return "Baritone is unavailable for movement";
        if (!ownBaritone && !legacyRegear() && (baritone.inControl() || baritone.pathing() || baritone.calcInProgress())) return "Baritone is already controlling the player";
        Modules modules = Modules.get();
        for (Module module : modules.getActive()) {
            if (REFUSED.contains(module.getClass())) return "Active producer: " + module.name;
        }
        return null;
    }

    public boolean legacyRegear() {
        AutoFlyingRegear regear = Modules.get().get(AutoFlyingRegear.class);
        return regear != null && (regear.isRunning() || regear.wantsRegear());
    }

    public GoalXZ awayFrom(com.hunterbuddy.behavior.core.Sensors sensors, int distance) {
        var attacker = available() ? client.world.getEntityById(sensors.lastAttackerId()) : null;
        double dx = attacker == null ? Math.sin(Math.toRadians(sensors.yaw())) : sensors.x() - attacker.getX();
        double dz = attacker == null ? -Math.cos(Math.toRadians(sensors.yaw())) : sensors.z() - attacker.getZ();
        double length = Math.hypot(dx, dz);
        if (length < 0.01) { dx = 1; dz = 0; length = 1; }
        return new GoalXZ((int) Math.floor(sensors.x() + dx / length * distance),
            (int) Math.floor(sensors.z() + dz / length * distance));
    }
}
