package com.hunterbuddy.behavior.core;

import com.hunterbuddy.behavior.hooks.TreeHooks;

public final class BotCleanup {
    private BotCleanup() {
    }

    public static void remove(TickContext ctx, TreeHooks hooks, String owner) {
        if (owner == null || !owner.startsWith("bot:")) return;
        hooks.remove(owner);
        hooks.applyPendingChanges(ctx);
        ctx.bb().removeOwner(owner);
        ctx.budgets().removeOwner(owner);
    }
}
