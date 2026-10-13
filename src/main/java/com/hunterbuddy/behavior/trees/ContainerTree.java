package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.TargetPoint;
import com.hunterbuddy.behavior.farmer.MissionJournal;
import com.hunterbuddy.behavior.mc.InventoryTransaction;
import com.hunterbuddy.behavior.mc.TemporaryEquipment;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.nodes.CloseScreen;
import com.hunterbuddy.behavior.nodes.If;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.LookAt;
import com.hunterbuddy.behavior.nodes.OpenContainer;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.SetValue;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.TransferItems;
import com.hunterbuddy.behavior.nodes.WaitScreen;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

public final class ContainerTree {
    private ContainerTree() {}

    public static Node open(TemporaryEquipment equipment, WorldReader world, Outputs outputs, MissionJournal journal) {
        return new Sequence("open-tracked-equipment", open(equipment.pos(), world, outputs, journal),
            new InventoryMilestone("verify-opened-equipment", ctx -> {
                if (!equipment.tracked()) throw new IllegalStateException("temporary equipment position is not tracked");
                if (!equipment.enderChest() && !equipment.signature().matchesContents(outputs.inventory().containerContents()))
                    throw new IllegalStateException("placed shulker content differs from expected signature");
                equipment.milestone(ctx, "opened");
            }));
    }

    public static Node open(BlockPos pos, WorldReader world, Outputs outputs, MissionJournal journal) {
        Key<TargetPoint> target = new Key<>("container-look-" + pos.toShortString(), TargetPoint.class, Scope.OWNER, false);
        return new Timeout(160, new WithLease(Set.of(Resource.LOOK, Resource.SCREEN, Resource.INTERACT), LeasePolicy.WAIT,
            new Sequence("open-container", new SetValue<>(target, ctx -> new TargetPoint(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)),
                new LookAt(target, 90, world, outputs), new OpenContainer(pos, outputs.inventory()), new WaitScreen(outputs.inventory()),
                new InventoryMilestone("container-opened", ctx -> {
                    if (!outputs.inventory().at(pos)) throw new IllegalStateException("different container claimed");
                    journal.milestone(ctx, "opened at=" + pos.toShortString());
                }))));
    }

    public static Node close(Outputs outputs) {
        return new Timeout(100, new WithLease(Set.of(Resource.SCREEN), LeasePolicy.WAIT, new CloseScreen(outputs.inventory())));
    }

    public static Node transfer(Outputs outputs, Supplier<InventoryTransaction> transaction, MissionJournal journal, String label) {
        return new Timeout(120, new WithLease(Set.of(Resource.INVENTORY), LeasePolicy.WAIT,
            new TransferItems(outputs.inventory(), transaction, null, journal, label)));
    }

    public static Node deposit(Outputs outputs, Predicate<ItemStack> filter, MissionJournal journal, String label) {
        return new Timeout(1_200, new WithLease(Set.of(Resource.INVENTORY), LeasePolicy.WAIT,
            new TransferItems(outputs.inventory(), () -> outputs.inventory().firstDeposit(filter), filter, journal, label)));
    }
}
