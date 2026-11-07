package com.hunterbuddy.pet;

import com.hunterbuddy.modules.AutoFlyingRegear;
import com.hunterbuddy.modules.ChunkRadar;
import com.hunterbuddy.modules.ElytraSwap;
import com.hunterbuddy.modules.KillAuraPlus;
import com.hunterbuddy.modules.RocketBoost;
import com.hunterbuddy.modules.WaypointFollower;
import com.hunterbuddy.util.TpsSampler;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public final class PetMonitor {
    private AutoFlyingRegear regear;
    private ChunkRadar radar;
    private KillAuraPlus combat;
    private RocketBoost boost;
    private WaypointFollower follower;
    private Setting<Integer> wornAt;
    private long flags;
    private boolean initialized;
    public boolean combatQuiet;
    public boolean regearQuiet;
    public boolean landingQuiet;
    public boolean danger;

    public void reset() {
        Modules modules = Modules.get();
        regear = modules.get(AutoFlyingRegear.class);
        radar = modules.get(ChunkRadar.class);
        combat = modules.get(KillAuraPlus.class);
        boost = modules.get(RocketBoost.class);
        follower = modules.get(WaypointFollower.class);
        ElytraSwap elytra = modules.get(ElytraSwap.class);
        wornAt = elytra == null ? null : elytra.settings.get("Durability Threshold", Integer.class);
        flags = 0;
        initialized = false;
        combatQuiet = false;
        regearQuiet = false;
        landingQuiet = false;
        danger = false;
    }

    public void update(double dangerRange) {
        var mc = MeteorClient.mc;
        if (mc.player == null || mc.world == null) return;
        combatQuiet = combat != null && combat.hasActiveTarget();
        regearQuiet = regear != null && regear.isActive() && regear.isBusy();
        WaypointFollower.HudState route = follower != null && follower.isActive() ? follower.hudState() : null;
        landingQuiet = route == WaypointFollower.HudState.LANDING;
        boolean close = false;
        var players = mc.world.getPlayers();
        for (int i = 0; i < players.size(); i++) {
            PlayerEntity player = players.get(i);
            if (player == mc.player || Friends.get().get(player.getGameProfile().name()) != null) continue;
            if (closeEnough(mc.player.squaredDistanceTo(player), dangerRange)) { close = true; break; }
        }
        danger = close;
        int rockets = 0;
        int totems = 0;
        var inventory = mc.player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isOf(Items.FIREWORK_ROCKET)) rockets += stack.getCount();
            if (stack.isOf(Items.TOTEM_OF_UNDYING)) totems += stack.getCount();
        }
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        boolean low = chest.isOf(Items.ELYTRA) && chest.getMaxDamage() > 0
            && (chest.getMaxDamage() - chest.getDamage()) * 100L <= chest.getMaxDamage() * (long) (wornAt == null ? 10 : wornAt.get());
        boolean trail = radar != null && radar.isActive() && radar.isAcquired();
        boolean wasTrail = (flags & (1L << PetEvent.TRAIL_ACQUIRED.ordinal())) != 0;
        if (initialized && wasTrail && !trail) PetEvents.publish(PetEvent.TRAIL_LOST);
        boolean wasClose = (flags & (1L << PetEvent.PLAYER_CLOSE.ordinal())) != 0;
        if (initialized && wasClose && !close) PetEvents.publish(PetEvent.LAUGH);
        edge(PetEvent.PLAYER_CLOSE, close);
        edge(PetEvent.TRAIL_ACQUIRED, trail);
        edge(PetEvent.TOTEM_LAST, totems == 1);
        edge(PetEvent.ELYTRA_LOW, low);
        edge(PetEvent.ROCKETS_EMPTY, mc.player.isGliding() && rockets == 0);
        finished(PetEvent.REGEAR, regearQuiet);
        edge(PetEvent.ROCKET_BOOST, boost != null && boost.isActive() && boost.isBoosting());
        edge(PetEvent.MANUAL_TAKEOVER, route == WaypointFollower.HudState.MANUAL);
        edge(PetEvent.TPS_DEAD, TpsSampler.get().timeSinceLastTick() > 5);
        edge(PetEvent.FLYING, mc.player.isGliding());
        initialized = true;
    }

    public static boolean closeEnough(double squaredDistance, double range) {
        return squaredDistance <= range * range;
    }

    private void finished(PetEvent event, boolean value) {
        long bit = 1L << event.ordinal();
        if (!value && (flags & bit) != 0 && initialized) PetEvents.publish(event);
        if (value) flags |= bit;
        else flags &= ~bit;
    }

    private void edge(PetEvent event, boolean value) {
        long bit = 1L << event.ordinal();
        if (value && (flags & bit) == 0 && initialized) PetEvents.publish(event);
        if (value) flags |= bit;
        else flags &= ~bit;
    }
}
