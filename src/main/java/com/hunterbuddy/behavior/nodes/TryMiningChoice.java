package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.MineSiteContext;
import java.util.function.Supplier;
import net.minecraft.util.math.BlockPos;

public final class TryMiningChoice extends Decorator {
    private final MineSiteContext mine;
    private final Supplier<BlockPos> choice;

    public TryMiningChoice(MineSiteContext mine, Supplier<BlockPos> choice, Node child) {
        super("try-next-choice-before-any-unconfirmed-break", child);
        this.mine = mine;
        this.choice = choice;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        NodeResult result = child().execute(ctx);
        if (result instanceof Failure failure && mine.facts.pending == null && mine.facts.outcome.isEmpty()
            && (failure.code() == FailureCode.UNREACHABLE || failure.code() == FailureCode.TIMEOUT
                || failure.code() == FailureCode.NOT_CONFIRMED || failure.code() == FailureCode.REFUSED)) {
            mine.rejected.add(choice.get());
            return Success.INSTANCE;
        }
        return result;
    }
}
