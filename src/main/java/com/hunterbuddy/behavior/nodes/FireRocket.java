package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.RocketAction;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.SlotIntent;

public final class FireRocket extends RocketAction {
    private final Key<Integer> slotKey;
    private final Outputs outputs;

    public FireRocket(Key<Integer> slotKey, Outputs outputs) { super("fire-rocket"); this.slotKey = slotKey; this.outputs = outputs; }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        int slot = ctx.bb().get(ctx.owner(), slotKey).orElse(-1);
        FailureCode refusal = outputs.flight().rocketRefusal(slot);
        if (refusal != null) return fail(refusal, switch (refusal) {
            case NOT_CONFIRMED -> "glide lost before rocket emission";
            case MISSING_ITEM -> "plain rocket stock exhausted";
            case BUDGET -> "plain rocket reserve reached";
            default -> "rocket slot changed or landing forbids emission";
        });
        outputs.slot(ctx.owner(), new SlotIntent(slot, true));
        if (ctx.sensors().selectedSlot() != slot || ctx.sensors().serverSlot() != slot || !outputs.slotSettled(slot)) return new Running("settling rocket slot");
        return outputs.flight().rocket(ctx, slot) ? Success.INSTANCE
            : fail(FailureCode.NOT_CONFIRMED, "rocket interaction was not accepted");
    }
}
