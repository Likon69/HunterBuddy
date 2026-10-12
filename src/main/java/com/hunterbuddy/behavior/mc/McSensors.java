package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.core.Sensors;
import com.hunterbuddy.behavior.resource.Leases;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.modules.AutoEatSync;
import com.hunterbuddy.modules.AutoFlyingRegear;
import com.hunterbuddy.modules.HMine;
import com.hunterbuddy.modules.KillAuraPlus;
import com.hunterbuddy.modules.regear.util.Utils;
import com.hunterbuddy.util.ServerSlot;
import com.hunterbuddy.util.SessionStats;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.world.TickRate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class McSensors {
    private final MinecraftClient client;
    private final WorldReader world;
    private final ServerFacts facts;
    private final BaritoneBridge baritone;
    private final Leases leases;
    private BooleanSupplier screenOwner = () -> false;

    public McSensors(MinecraftClient client, WorldReader world, ServerFacts facts, BaritoneBridge baritone, Leases leases) {
        this.client = Objects.requireNonNull(client);
        this.world = Objects.requireNonNull(world);
        this.facts = Objects.requireNonNull(facts);
        this.baritone = Objects.requireNonNull(baritone);
        this.leases = Objects.requireNonNull(leases);
    }

    public void screenOwner(BooleanSupplier owner) { screenOwner = Objects.requireNonNull(owner); }

    public Sensors capture() {
        if (!world.available()) throw new IllegalStateException("Minecraft world is unavailable");
        WorldIdentity identity = world.identity();
        facts.bind(identity.playerId(), client.getNetworkHandler() == null ? null : client.getNetworkHandler().getConnection());
        ServerSnapshot server = facts.drain();
        BaritoneSnapshot path = baritone.capture();
        BlockPos feet = client.player.getBlockPos();
        Vec3d velocity = client.player.getVelocity();
        Modules modules = Modules.get();
        AutoEatSync eat = modules.get(AutoEatSync.class);
        AutoFlyingRegear regear = modules.get(AutoFlyingRegear.class);
        KillAuraPlus combat = modules.get(KillAuraPlus.class);
        HMine mine = modules.get(HMine.class);
        int visible = (int) client.world.getPlayers().stream().filter(player -> player != client.player && !player.isInvisibleTo(client.player)).count();
        return new Sensors(client.player.getX(), client.player.getY(), client.player.getZ(), feet.getX(), feet.getY(), feet.getZ(),
            client.player.getYaw(), client.player.getPitch(), velocity.x, velocity.y, velocity.z,
            client.player.isOnGround(), client.player.isTouchingWater(), client.player.isSubmergedInWater(), client.player.isInLava(),
            client.player.isGliding(), client.player.isClimbing(), client.player.horizontalCollision,
            client.player.getHealth(), client.player.getHungerManager().getFoodLevel(), client.player.getAir(), client.player.isDead(), server.hurtThisTick(),
            server.lastAttackerId(), client.player.getInventory().getSelectedSlot(), ServerSlot.get().slot(),
            client.currentScreen != null, screenOwner.getAsBoolean() || regear != null && regear.isRunning()
                && client.currentScreen instanceof HandledScreen<?>, client.currentScreen == null ? "none" : client.currentScreen.getClass().getSimpleName(), client.player.isUsingItem(),
            client.interactionManager.isBreakingBlock(), identity.dimensionKey(), identity.worldId(), identity.playerId(),
            Utils.isIn2b2tQueue(), TickRate.INSTANCE.getTimeSinceLastTick() >= 1.0f, path.available(),
            path.pathing(), path.hasPath(), path.goalActive(), path.calcInProgress(), path.inControl(), path.lastPathEvent(),
            path.elytraActive(), path.elytraDestinationSet(), SessionStats.get().blocksMined(), server.pickups(), server.setbacks(),
            mine != null && mine.isActive() && mine.isMining(), regear != null && regear.isRunning(), regear != null && regear.wantsRegear(),
            eat != null && eat.isEating(), combat != null && combat.hasActiveTarget(), leases.holder(Resource.MOVEMENT).map(lease -> lease.owner()).orElse(null),
            world.count(Items.FIREWORK_ROCKET), world.count(Items.OBSIDIAN), world.count(Items.ENDER_CHEST), visible);
    }
}
