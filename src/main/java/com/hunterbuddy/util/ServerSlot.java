package com.hunterbuddy.util;

import com.hunterbuddy.modules.mixin.accessors.PlayerInventoryAccessor;
import com.hunterbuddy.modules.mixin.accessors.UpdateSelectedSlotS2CPacketAccessor;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;

public final class ServerSlot {
    private static ServerSlot INSTANCE;

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private int serverSlot = -1;
    private boolean sending;

    private ServerSlot() {
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    public static ServerSlot get() {
        if (INSTANCE == null) {
            INSTANCE = new ServerSlot();
        }

        return INSTANCE;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.serverSlot == -1 && this.mc.player != null) {
            this.serverSlot = this.clientSlot();
        }
    }

    @EventHandler
    private void onSend(PacketEvent.Send event) {
        if (this.sending || !(event.packet instanceof UpdateSelectedSlotC2SPacket packet)) {
            return;
        }

        int slot = packet.getSelectedSlot();
        if (!PlayerInventory.isValidHotbarIndex(slot) || slot == this.serverSlot) {
            event.cancel();
            return;
        }

        this.serverSlot = slot;
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (event.packet instanceof UpdateSelectedSlotS2CPacket packet) {
            this.serverSlot = ((UpdateSelectedSlotS2CPacketAccessor) (Object) packet).getSlot();
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        this.serverSlot = -1;
    }

    public void select(int slot) {
        if (this.mc.player == null || this.mc.getNetworkHandler() == null) {
            return;
        }

        if (this.serverSlot == -1) {
            this.serverSlot = this.clientSlot();
        }

        if (PlayerInventory.isValidHotbarIndex(slot) && slot != this.serverSlot) {
            this.send(slot);
        }
    }

    public void resync() {
        if (this.mc.player == null || this.mc.getNetworkHandler() == null) {
            return;
        }

        int slot = this.clientSlot();
        if (slot != this.serverSlot) {
            this.send(slot);
        }
    }

    private void send(int slot) {
        this.sending = true;

        try {
            this.mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            this.serverSlot = slot;
        } finally {
            this.sending = false;
        }
    }

    private int clientSlot() {
        return ((PlayerInventoryAccessor) this.mc.player.getInventory()).getSelectedSlot();
    }
}
