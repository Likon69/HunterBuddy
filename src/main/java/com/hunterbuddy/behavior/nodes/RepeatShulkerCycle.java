package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.LogisticsRoles;
import com.hunterbuddy.behavior.farmer.ShulkerCycleFacts;
import com.hunterbuddy.behavior.mc.LogisticsContext;
import com.hunterbuddy.behavior.mc.LogisticsInventory;
import java.util.function.Predicate;
import net.minecraft.item.ItemStack;

public final class RepeatShulkerCycle extends Decorator {
    private final LogisticsContext logistics;
    private final String role;

    public RepeatShulkerCycle(LogisticsContext logistics, String role, Node child) {
        super("all-shulkers-" + role, child);
        this.logistics = logistics;
        this.role = role;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        NodeResult result = child().execute(ctx);
        if (!(result instanceof Success)) return result;
        ShulkerCycleFacts cycle = logistics.cycle(role);
        Predicate<ItemStack> filter = role.equals(LogisticsRoles.MATERIAL)
            ? LogisticsInventory.accepted(logistics.profile) : LogisticsInventory.miscellaneous(logistics.profile);
        if (logistics.evacuationRequested.getAsBoolean() || !cycle.unavailable.isEmpty()
            || logistics.world.countMatching(filter) == 0) return result;
        if (!logistics.stored(role)) throw new IllegalStateException("repeating a shulker cycle without a storage receipt");
        ShulkerCycleFacts next = new ShulkerCycleFacts();
        if (role.equals(LogisticsRoles.MATERIAL)) logistics.facts.material = next;
        else logistics.facts.divers = next;
        logistics.forget(role);
        logistics.save(ctx, "another " + role + " shulker needed after confirmed storage");
        child().resetMemory();
        return new Running("remaining objects need another shulker");
    }
}
