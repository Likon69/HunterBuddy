package com.hunterbuddy.modules.mine;

import com.hunterbuddy.modules.MlepMine;
import com.hunterbuddy.modules.mixin.accessors.PlayerInventoryAccessor;
import com.hunterbuddy.util.ServerSlot;
import java.util.function.Predicate;
import java.util.function.Supplier;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.math.BlockPos;

public class ToolHold {
    private final MinecraftClient mc = MeteorClient.mc;
    private final Supplier<MlepMine.Swap> mode;
    private int taken = -1;
    private int origin = -1;
    private BlockPos heldPos;
    private long heldSince;
    private boolean own;
    private boolean silentUsed;

    public ToolHold(Supplier<MlepMine.Swap> mode) {
        this.mode = mode;
    }

    public boolean ownChange() {
        return this.own;
    }

    public void prepare() {
        if (this.mode.get() == MlepMine.Swap.SILENT) {
            ServerSlot.get();
            this.silentUsed = true;
        }
    }

    public void holdFor(BlockState s, BlockPos pos) {
        if (this.mode.get() == MlepMine.Swap.OFF || this.mc.player == null) {
            return;
        }

        int best = BreakMath.fasterThanHeld(this.mc.player, s);
        if (best == -1) {
            if (this.taken != -1) {
                this.heldPos = pos;
                this.heldSince = System.currentTimeMillis();
            }

            return;
        }

        if (best != this.taken) {
            if (this.taken == -1) {
                this.origin = this.selected();
            }

            this.swapTo(best);
            this.taken = best;
        }

        this.heldPos = pos;
        this.heldSince = System.currentTimeMillis();
    }

    public void release(boolean solidLeft, Predicate<BlockPos> cleared) {
        if (this.taken == -1 || this.origin == -1) {
            return;
        }

        if (solidLeft) {
            return;
        }

        if (this.heldPos != null && !cleared.test(this.heldPos) && System.currentTimeMillis() - this.heldSince < 5000L) {
            return;
        }

        boolean playerLeft = this.mode.get() == MlepMine.Swap.NORMAL && this.selected() != this.taken;
        if (!playerLeft) {
            this.returnTo(this.origin);
        }

        this.reset();
    }

    public void shutDown() {
        if (this.taken != -1 && this.origin != -1) {
            this.returnTo(this.origin);
        } else if (this.mode.get() == MlepMine.Swap.SILENT && this.silentUsed) {
            ServerSlot.get().resync();
        }

        this.reset();
    }

    public void forget() {
        this.reset();
    }

    private void reset() {
        this.taken = -1;
        this.origin = -1;
        this.heldPos = null;
        this.heldSince = 0L;
        this.own = false;
    }

    private int selected() {
        return ((PlayerInventoryAccessor) this.mc.player.getInventory()).getSelectedSlot();
    }

    private void swapTo(int n) {
        this.own = true;

        try {
            switch (this.mode.get()) {
                case NORMAL -> this.normalSelect(n);
                case SILENT -> {
                    this.silentUsed = true;
                    ServerSlot.get().select(n);
                }
                case OFF -> {
                }
            }
        } finally {
            this.own = false;
        }
    }

    private void returnTo(int o) {
        this.own = true;

        try {
            switch (this.mode.get()) {
                case NORMAL -> this.normalSelect(o);
                case SILENT -> {
                    this.silentUsed = true;
                    ServerSlot.get().resync();
                }
                case OFF -> {
                }
            }
        } finally {
            this.own = false;
        }
    }

    private void normalSelect(int n) {
        if (this.mc.player == null || this.mc.getNetworkHandler() == null) {
            return;
        }

        ((PlayerInventoryAccessor) this.mc.player.getInventory()).setSelectedSlot(n);
        this.mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(n));
    }
}
