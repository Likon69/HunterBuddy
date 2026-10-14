package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.farmer.ShulkerCycleFacts;
import com.hunterbuddy.behavior.mc.LogisticsContext;
import com.hunterbuddy.behavior.mc.LogisticsInventory;
import com.hunterbuddy.behavior.nodes.ClickSlot;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.item.ItemStack;

public final class MoveLogisticsOffhandTree {
    private MoveLogisticsOffhandTree() {}

    public static Node build(LogisticsContext logistics, String role, Predicate<ItemStack> filter) {
        return new Timeout(600, LogisticsOperations.unless("offhand ready " + role,
            () -> logistics.cycle(role).secondaryStack.isEmpty()
                && !filter.test(logistics.world.client().player.getOffHandStack()),
            new Sequence("move-offhand-" + role,
                ContainerTree.close(logistics.outputs),
                new InventoryMilestone("offhand-intent", ctx -> {
                    ShulkerCycleFacts cycle = logistics.cycle(role);
                    if (!cycle.secondaryStack.isEmpty()) return;
                    ItemStack expected = logistics.world.client().player.getOffHandStack().copy();
                    cycle.secondaryStack = LogisticsInventory.encode(List.of(expected), logistics.world);
                    cycle.secondaryBaseline = count(logistics, expected);
                    logistics.save(ctx, "offhand move intent " + role);
                }),
                LogisticsOperations.unless("offhand movement received", () -> moved(logistics, role),
                    new Sequence("offhand-click", LogisticsOperations.prepare(logistics, ItemStack::isEmpty, 8),
                        new WithLease(Set.of(Resource.INVENTORY), LeasePolicy.WAIT,
                            new Sequence("player-inventory-claimed", new InventoryMilestone("claim-inventory", ctx -> {
                                if (!logistics.outputs.inventory().claimPlayer(ctx.leases()
                                    .holder(Resource.INVENTORY).orElseThrow())) {
                                    throw new IllegalStateException("player inventory unavailable");
                                }
                            }), new ClickSlot(() -> {
                                ShulkerCycleFacts cycle = logistics.cycle(role);
                                ItemStack expected = LogisticsInventory.decode(cycle.secondaryStack, 1, logistics.world).getFirst();
                                if (!ItemStack.areEqual(expected, logistics.world.client().player.getOffHandStack())
                                    || !logistics.layout.stack(8).isEmpty()) {
                                    throw new IllegalStateException("offhand source or destination changed");
                                }
                                return logistics.outputs.inventory().playerSwap(40, 8);
                            }, logistics.outputs.inventory()))))),
                new InventoryMilestone("offhand-confirmed", ctx -> {
                    if (!moved(logistics, role)) throw new IllegalStateException("offhand move not confirmed");
                    logistics.cycle(role).secondaryStack = "";
                    logistics.save(ctx, "offhand move confirmed " + role);
                }))));
    }

    private static boolean moved(LogisticsContext logistics, String role) {
        ShulkerCycleFacts cycle = logistics.cycle(role);
        ItemStack expected = LogisticsInventory.decode(cycle.secondaryStack, 1, logistics.world).getFirst();
        if (logistics.world.client().player.getOffHandStack().isEmpty()
            && count(logistics, expected) == cycle.secondaryBaseline + expected.getCount()) return true;
        if (!ItemStack.areEqual(expected, logistics.world.client().player.getOffHandStack())) {
            throw new IllegalStateException("offhand movement cannot be reconciled");
        }
        return false;
    }

    private static int count(LogisticsContext logistics, ItemStack expected) {
        return LogisticsInventory.player(logistics.world).stream()
            .filter(stack -> ItemStack.areItemsAndComponentsEqual(stack, expected)).mapToInt(ItemStack::getCount).sum();
    }
}
