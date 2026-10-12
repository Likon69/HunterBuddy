package com.hunterbuddy.util;

import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.util.PlayerInput;

public final class QuietClicks {
    private static QuietClicks INSTANCE;

    private boolean moving;
    private boolean sneak;
    private boolean sprint;
    private boolean settle;
    private long ticks;
    private long blockedSince = -1L;

    private QuietClicks() {
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    public static void init() {
        if (INSTANCE == null) {
            INSTANCE = new QuietClicks();
        }
    }

    public static boolean ready() {
        init();
        return !INSTANCE.moving && !INSTANCE.sneak && !INSTANCE.sprint;
    }

    public static boolean readyOrSettle() {
        if (ready()) {
            INSTANCE.blockedSince = -1L;
            return true;
        }

        if (INSTANCE.blockedSince < 0L) {
            INSTANCE.blockedSince = INSTANCE.ticks;
        } else if (INSTANCE.ticks - INSTANCE.blockedSince > 10L) {
            HunterBuddyAddon.LOG.warn("QuietClicks released a click after 10 blocked ticks (moving={} sneak={} sprint={})", INSTANCE.moving, INSTANCE.sneak, INSTANCE.sprint);
            INSTANCE.blockedSince = -1L;
            INSTANCE.settle = false;
            return true;
        }

        INSTANCE.settle = true;
        return false;
    }

    public static boolean takeSettle() {
        if (INSTANCE == null || !INSTANCE.settle) {
            return false;
        }

        INSTANCE.settle = false;
        return true;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        this.ticks++;
    }

    @EventHandler
    private void onSend(PacketEvent.Send event) {
        if (event.packet instanceof PlayerInputC2SPacket packet) {
            PlayerInput input = packet.input();
            this.moving = input.forward() || input.backward() || input.left() || input.right() || input.jump();
            this.sneak = input.sneak();
        } else if (event.packet instanceof ClientCommandC2SPacket packet) {
            if (packet.getMode() == ClientCommandC2SPacket.Mode.START_SPRINTING) {
                this.sprint = true;
            } else if (packet.getMode() == ClientCommandC2SPacket.Mode.STOP_SPRINTING) {
                this.sprint = false;
            }
        }
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerRespawnS2CPacket) {
            this.reset();
        }
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        this.reset();
    }

    private void reset() {
        this.moving = false;
        this.sneak = false;
        this.sprint = false;
        this.settle = false;
        this.blockedSince = -1L;
    }
}
