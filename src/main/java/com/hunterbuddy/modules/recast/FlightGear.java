package com.hunterbuddy.modules.recast;

import com.hunterbuddy.util.GliderSwap;
import com.hunterbuddy.util.Rockets;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.world.World;

public final class FlightGear {
    private FlightGear() {
    }

    public static boolean hasElytra(ClientPlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA) || GliderSwap.hasGlider(player);
    }

    public static boolean hasRockets() {
        return InvUtils.find(Rockets::isPlain).found();
    }

    public static boolean isNether(World world) {
        return world != null && world.getRegistryKey() == World.NETHER;
    }
}
