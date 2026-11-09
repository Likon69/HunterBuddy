package com.hunterbuddy.modules.scaffold;

import java.util.ArrayDeque;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

final class ScaffoldPrediction {
    private record Pending(ClientWorld world, int sequence, long deadline) { }
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();

    void sent(ClientWorld world, int sequence) {
        if (sequence < 0) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        long latency = entry == null ? 200 : Math.max(0, entry.getLatency());
        pending.addLast(new Pending(world, sequence, System.nanoTime() + Math.max(1000, latency * 4 + 250) * 1_000_000));
    }

    void reconcile() {
        long now = System.nanoTime();
        while (!pending.isEmpty() && pending.getFirst().deadline() <= now) finish(pending.removeFirst());
    }

    void clear() {
        while (!pending.isEmpty()) finish(pending.removeFirst());
    }

    private void finish(Pending prediction) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.world == prediction.world()) prediction.world().handlePlayerActionResponse(prediction.sequence());
    }
}
