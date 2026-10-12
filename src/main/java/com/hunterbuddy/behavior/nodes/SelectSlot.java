package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.SlotIntent;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.item.ItemStack;

public final class SelectSlot extends Action {
    private final Predicate<ItemStack> filter;
    private final boolean restore;
    private final WorldReader world;
    private final Outputs outputs;
    private int selected = -1;

    public SelectSlot(Predicate<ItemStack> filter, boolean restore, WorldReader world, Outputs outputs) {
        super("select-slot");
        this.filter = Objects.requireNonNull(filter);
        this.restore = restore;
        this.world = Objects.requireNonNull(world);
        this.outputs = Objects.requireNonNull(outputs);
    }

    @Override
    protected void onActionStart(TickContext ctx) {
        selected = world.findSlot(filter);
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        if (selected < 0) return fail(FailureCode.MISSING_ITEM, "no matching item in the hotbar");
        if (!world.matchesSlot(selected, filter)) return fail(FailureCode.MISSING_ITEM, "selected hotbar item no longer matches");
        if (ctx.sensors().selectedSlot() == selected && ctx.sensors().serverSlot() == selected && outputs.slotSettled(selected)) {
            return Success.INSTANCE;
        }
        outputs.slot(ctx.owner(), new SlotIntent(selected, restore));
        return new Running(ctx.sensors().serverSlot() < 0 ? "server slot is unknown" : "waiting for client/server slot agreement");
    }
}
