package com.hunterbuddy.behavior.mc;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;

public final class ServerFacts {
    private static final int SETBACK_TICKS = 20;
    private final AtomicLong pickups = new AtomicLong();
    private final AtomicLong setbacks = new AtomicLong();
    private final AtomicLong setbackAt = new AtomicLong(-1);
    private final AtomicBoolean hurt = new AtomicBoolean();
    private final AtomicLong hurtAt = new AtomicLong(-1);
    private final AtomicInteger attacker = new AtomicInteger(-1);
    private volatile ClientConnection connection;
    private volatile int playerId = -1;
    private volatile long clientTick;
    private volatile boolean sampling;

    public void beginTick() {
        clientTick++;
        sampling = true;
    }

    public void endTick() {
        sampling = false;
    }

    public long tick() {
        return clientTick;
    }

    public boolean samplesValid() {
        long correctedAt = setbackAt.get();
        return correctedAt < 0 || clientTick - correctedAt >= SETBACK_TICKS;
    }

    public void bind(int playerId, ClientConnection connection) {
        if (this.connection != connection || this.playerId != playerId) {
            this.playerId = -1;
            pickups.set(0);
            hurt.set(false);
            hurtAt.set(-1);
            attacker.set(-1);
            this.connection = connection;
        }
        this.playerId = playerId;
    }

    public ServerSnapshot drain() {
        boolean recentHurt = hurt.getAndSet(false) && hurtAt.get() >= clientTick - 1;
        return new ServerSnapshot(pickups.get(), setbacks.get(), recentHurt, attacker.get());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onReceive(PacketEvent.Receive event) {
        if (event.isCancelled()) return;
        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            setbackAt.set(clientTick + (sampling ? 0 : 1));
            setbacks.incrementAndGet();
            return;
        }
        int localId = playerId;
        if (localId < 0 || event.connection != connection) return;
        if (event.packet instanceof ItemPickupAnimationS2CPacket pickup && pickup.getCollectorEntityId() == localId) {
            pickups.addAndGet(Math.max(0, pickup.getStackAmount()));
        } else if (event.packet instanceof EntityDamageS2CPacket damage && damage.entityId() == localId
            && damage.sourceCauseId() >= 0 && damage.sourceCauseId() != localId) {
            attacker.set(damage.sourceCauseId());
            hurtAt.set(clientTick + 1);
            hurt.set(true);
        }
    }
}
