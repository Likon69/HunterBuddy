package com.hunterbuddy.modules.mine;

import com.hunterbuddy.modules.HMine;
import com.hunterbuddy.modules.mixin.accessors.PlayerInventoryAccessor;
import com.hunterbuddy.util.ActionSwap;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.mixininterface.IClientPlayerInteractionManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

public class ToolHold {
    private final MinecraftClient mc = MeteorClient.mc;
    private final Supplier<HMine.Swap> mode;
    private final BooleanSupplier alternative;
    private int taken = -1;
    private int origin = -1;
    private BlockPos heldPos;
    private long heldSince;
    private boolean own;

    public ToolHold(Supplier<HMine.Swap> mode, BooleanSupplier alternative) {
        this.mode = mode;
        this.alternative = alternative;
    }

    public boolean ownChange() {
        return this.own;
    }

    public void holdFor(BlockState s, BlockPos pos) {
        if (this.mode.get() != HMine.Swap.NORMAL || this.mc.player == null) return;

        int best = BreakMath.fasterThanHeld(this.mc.player, s);
        if (best == -1) {
            if (this.taken != -1) {
                this.heldPos = pos;
                this.heldSince = System.currentTimeMillis();
            }
            return;
        }

        if (best != this.taken) {
            if (this.taken == -1) this.origin = this.selected();
            this.select(best);
            this.taken = best;
        }
        this.heldPos = pos;
        this.heldSince = System.currentTimeMillis();
    }

    public void withTool(BlockState state, Runnable action) {
        if (this.mode.get() != HMine.Swap.SILENT) {
            action.run();
            return;
        }
        if (this.mc.player == null) return;
        int best = BreakMath.fasterThanHeld(this.mc.player, state);
        ActionSwap.run(best == -1 ? this.selected() : best, this.alternative.getAsBoolean(), action);
    }

    public void release(boolean solidLeft, Predicate<BlockPos> cleared) {
        if (this.taken == -1 || this.origin == -1 || solidLeft) return;
        if (this.heldPos != null && !cleared.test(this.heldPos) && System.currentTimeMillis() - this.heldSince < 5000L) return;
        if (this.mode.get() == HMine.Swap.NORMAL && this.selected() == this.taken) this.select(this.origin);
        this.reset();
    }

    public void shutDown() {
        if (this.mode.get() == HMine.Swap.NORMAL && this.mc.player != null && this.taken != -1 && this.origin != -1) this.select(this.origin);
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

    private void select(int slot) {
        if (this.mc.player == null || this.mc.interactionManager == null || this.mc.getNetworkHandler() == null) return;
        this.own = true;
        try {
            this.mc.player.getInventory().setSelectedSlot(slot);
            ((IClientPlayerInteractionManager) this.mc.interactionManager).meteor$syncSelected();
        } finally {
            this.own = false;
        }
    }
}
