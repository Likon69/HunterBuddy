package com.hunterbuddy.behavior.resource;

import com.hunterbuddy.behavior.mc.WorldIdentity;
import com.hunterbuddy.util.ServerSlot;
import java.util.Objects;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.MinecraftClient;

public final class SlotOutput {
    private static final int HOLD_TICKS = 2;
    private final MinecraftClient client;
    private Lease lease;
    private WorldIdentity world;
    private int originalSlot = -1;
    private int appliedSlot = -1;
    private boolean restore;
    private long changedAt = -HOLD_TICKS;

    public SlotOutput(MinecraftClient client) {
        this.client = Objects.requireNonNull(client);
    }

    public void apply(SlotIntent intent, Lease current, long tick) {
        if (intent == null || client.player == null || client.world == null) {
            release(tick);
            return;
        }
        WorldIdentity identity = WorldIdentity.capture(client);
        if (lease != null && (!lease.owner().equals(current.owner()) || !identity.equals(world))) release(tick);
        if (lease == null) {
            originalSlot = client.player.getInventory().getSelectedSlot();
            world = identity;
        }
        lease = current;
        restore = intent.restore();
        int selected = client.player.getInventory().getSelectedSlot();
        int server = ServerSlot.get().slot();
        if ((selected != intent.slot() || server != intent.slot()) && tick - changedAt < HOLD_TICKS) return;
        if (selected != intent.slot() || server != intent.slot()) {
            if (!InvUtils.swap(intent.slot(), false)) throw new IllegalStateException("Hotbar slot swap failed");
            if (ServerSlot.get().slot() != intent.slot()) ServerSlot.get().select(intent.slot());
            changedAt = tick;
        }
        appliedSlot = intent.slot();
    }

    public boolean settled(int slot, long tick) {
        return appliedSlot != slot || tick - changedAt >= HOLD_TICKS;
    }

    public void discard() {
        lease = null;
        world = null;
        originalSlot = -1;
        appliedSlot = -1;
        restore = false;
    }

    public void release(long tick) {
        boolean canRestore = lease != null && restore && originalSlot >= 0 && client.player != null && client.world != null
            && WorldIdentity.capture(client).equals(world) && client.player.getInventory().getSelectedSlot() == appliedSlot
            && ServerSlot.get().slot() == appliedSlot;
        int previous = originalSlot;
        discard();
        if (canRestore && client.player.getInventory().getSelectedSlot() != previous) {
            InvUtils.swap(previous, false);
            if (ServerSlot.get().slot() != previous) ServerSlot.get().select(previous);
            changedAt = tick;
        }
    }
}
