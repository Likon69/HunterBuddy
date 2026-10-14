package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.mc.InventoryLayout;
import com.hunterbuddy.behavior.mc.LogisticsContext;
import com.hunterbuddy.behavior.mc.TemporaryEquipment;
import com.hunterbuddy.behavior.nodes.BreakEquipment;
import com.hunterbuddy.behavior.nodes.ConfirmEquipmentPlacement;
import com.hunterbuddy.behavior.nodes.Deferred;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.PickupItem;
import com.hunterbuddy.behavior.nodes.Selector;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.SetValue;
import com.hunterbuddy.behavior.nodes.Succeed;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.Set;
import net.minecraft.util.math.BlockPos;

public final class TrackedEquipmentTree {
    private TrackedEquipmentTree() {}

    public static Node place(LogisticsContext logistics, String role) {
        return new Selector("posé-" + role,
            LogisticsOperations.guard("bloc suivi présent " + role, () -> logistics.placed(role)),
            new ConfirmEquipmentPlacement(logistics, role),
            new Deferred("poser-" + role, () -> new Sequence("préparer-et-poser-" + role,
                role.equals("chest") ? LogisticsOperations.prepare(logistics, InventoryLayout::silkPickaxe, 1) : new Succeed(),
                LogisticsOperations.prepare(logistics, logistics.equipment(role).filter(), role.equals("chest") ? 4 : 8),
                EquipmentTree.place(logistics.equipment(role), logistics.world, logistics.outputs)))).propagateFailures();
    }

    public static Node recover(LogisticsContext logistics, String role) {
        return LogisticsOperations.unless("repris-" + role, () -> logistics.returned(role),
            new Deferred("casser-et-ramasser-" + role, () -> recovery(logistics, role)));
    }

    private static Node recovery(LogisticsContext logistics, String role) {
        TemporaryEquipment item = logistics.equipment(role);
        Key<BlockPos> key = new Key<>("logistics-recover-" + role, BlockPos.class, Scope.OWNER, false);
        Node breaking = LogisticsOperations.unless("casse déjà constatée " + role,
            () -> (item.broken() || item.breakRequested()) && logistics.world.cleared(item.pos()),
            new WithLease(Set.of(Resource.MINING), LeasePolicy.WAIT,
                new BreakEquipment(item, key, logistics.world, logistics.outputs)));
        Node tool = item.enderChest()
            ? LogisticsOperations.prepare(logistics, InventoryLayout::silkPickaxe, 1)
            : new Succeed();
        return new Timeout(800, new Sequence("reprendre-matériel-" + role,
            ContainerTree.close(logistics.outputs), tool, new SetValue<>(key, ctx -> item.pos()), breaking,
            new InventoryMilestone("casse-confirmée-" + role, ctx -> {
                if (!item.breakRequested()) throw new IllegalStateException("missing break intent for " + role);
                item.broken(ctx);
            }),
            new PickupItem(item.pos(), item.filter(), item::beforePickup, role,
                logistics.world, logistics.baritone, logistics.outputs, logistics.mission),
            new InventoryMilestone("ramassage-vérifié-" + role, ctx -> {
                item.pickedUp(ctx);
                logistics.outputs.mining().forgetTemporaryBlock(item.pos());
                if (item.enderChest()) logistics.outputs.mining().forgetTemporaryChest(item.pos());
                logistics.save(ctx, "ramassé " + role + " at=" + item.pos().toShortString());
            })));
    }
}
