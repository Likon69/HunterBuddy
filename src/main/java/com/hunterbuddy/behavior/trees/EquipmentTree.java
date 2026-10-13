package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.farmer.MissionJournal;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.InventoryLayout;
import com.hunterbuddy.behavior.mc.TemporaryEquipment;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.nodes.BreakEquipment;
import com.hunterbuddy.behavior.nodes.If;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.PickupItem;
import com.hunterbuddy.behavior.nodes.PlaceEquipment;
import com.hunterbuddy.behavior.nodes.PrepareInventory;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.SetValue;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.Set;
import net.minecraft.util.math.BlockPos;

public final class EquipmentTree {
    private EquipmentTree() {}

    public static Node place(TemporaryEquipment equipment, WorldReader world, Outputs outputs) {
        return new Timeout(160, new WithLease(Set.of(Resource.SLOT, Resource.LOOK, Resource.INTERACT), LeasePolicy.WAIT,
            new PlaceEquipment(equipment, world, outputs)));
    }

    public static Node recover(TemporaryEquipment equipment, WorldReader world, BaritoneBridge baritone, Outputs outputs, MissionJournal journal) {
        Key<BlockPos> key = new Key<>("temporary-break-" + equipment.pos().toShortString(), BlockPos.class, Scope.OWNER, false);
        return new Timeout(800, new Sequence("recover-equipment", new SetValue<>(key, ctx -> equipment.pos()),
            new WithLease(Set.of(Resource.MINING), LeasePolicy.WAIT, new BreakEquipment(equipment, key, world, outputs)),
            new PickupItem(equipment.pos(), stack -> equipment.filter().test(stack), equipment::beforePickup,
                equipment.enderChest() ? "ender-chest" : "shulker", world, baritone, outputs, journal),
            new InventoryMilestone("equipment-picked-up", ctx -> {
                equipment.pickedUp(ctx);
                outputs.mining().forgetTemporaryBlock(equipment.pos());
                if (equipment.enderChest()) outputs.mining().forgetTemporaryChest(equipment.pos());
            })));
    }

    public static Node prepareHeld(InventoryLayout layout, java.util.function.Predicate<net.minecraft.item.ItemStack> filter, int slot, Outputs outputs, MissionJournal journal) {
        return new Timeout(400, new WithLease(Set.of(Resource.INVENTORY), LeasePolicy.WAIT,
            new PrepareInventory(layout, outputs.inventory(), journal, filter, slot)));
    }
}
