package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.mc.BaseVisitContext;
import com.hunterbuddy.behavior.mc.InventoryLayout;
import com.hunterbuddy.behavior.nodes.Deferred;
import com.hunterbuddy.behavior.nodes.Sequence;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;

public final class BaseDepartureInventoryTree {
    private BaseDepartureInventoryTree() {}

    public static Node build(BaseVisitContext visit) {
        return new Deferred("departure-slots-confirmed", () -> {
            InventoryLayout layout = new InventoryLayout(visit.logistics.world, visit.logistics.profile);
            List<Node> slots = new ArrayList<>();
            for (int slot = 0; slot < 5; slot++) {
                slots.add(EquipmentTree.prepareHeld(layout, layout.filter(slot), slot,
                    visit.logistics.outputs, visit.logistics.mission));
            }
            slots.add(LogisticsOperations.unless("no shelter block available",
                () -> layout.find(layout.filter(5), 0) < 0,
                EquipmentTree.prepareHeld(layout, layout.filter(5), 5, visit.logistics.outputs, visit.logistics.mission)));
            slots.add(EquipmentTree.prepareHeld(layout, ItemStack::isEmpty, 8, visit.logistics.outputs, visit.logistics.mission));
            return new Sequence("hotbar-ready-and-slot-free", slots.toArray(Node[]::new));
        });
    }
}
