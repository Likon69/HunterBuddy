package com.hunterbuddy.modules.signs;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket;

public final class SignRestorer {
    private UpdateSignC2SPacket pending;
    private UpdateSignC2SPacket sent;
    private int ticksLeft;

    public void queue(SignRecord original, boolean front, int delayTicks) {
        pending = new UpdateSignC2SPacket(
            original.key().pos(),
            front,
            original.line(front, 0),
            original.line(front, 1),
            original.line(front, 2),
            original.line(front, 3)
        );
        ticksLeft = delayTicks;
    }

    public boolean tick(ClientPlayNetworkHandler network) {
        if (pending == null || ticksLeft-- > 0) return false;
        sent = pending;
        pending = null;
        network.sendPacket(sent);
        return true;
    }

    public boolean isOwn(Packet<?> packet) {
        return packet == sent;
    }

    public void clear() {
        pending = null;
        sent = null;
    }
}
