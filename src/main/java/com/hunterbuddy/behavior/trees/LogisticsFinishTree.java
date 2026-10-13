package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.mc.LogisticsContext;
import com.hunterbuddy.behavior.nodes.Deferred;
import com.hunterbuddy.behavior.nodes.Sequence;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.item.ItemStack;

public final class LogisticsFinishTree {
    private LogisticsFinishTree() {}

    public static Node build(LogisticsContext logistics) {
        return new Sequence("reprendre-ender-chest-et-sortir",
            ContainerTree.close(logistics.outputs), TrackedEquipmentTree.recover(logistics, "chest"),
            new Deferred("restaurer-barre-disponible", () -> {
                List<Node> nodes = new ArrayList<>();
                for (int slot = 0; slot < logistics.layout.size(); slot++) {
                    Predicate<ItemStack> filter = logistics.layout.filter(slot);
                    if (logistics.layout.find(filter, 0) >= 0) nodes.add(LogisticsOperations.prepare(logistics, filter, slot));
                }
                nodes.add(LogisticsOperations.prepare(logistics, ItemStack::isEmpty, 8));
                return new Sequence("barre-fixe-et-slot-libre", nodes.toArray(Node[]::new));
            }), ProtectionExitTree.build(logistics));
    }
}
