package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.OperationFailure;
import com.hunterbuddy.behavior.mc.BaseVisitContext;
import com.hunterbuddy.behavior.mc.LogisticsInventory;
import com.hunterbuddy.behavior.mc.ShulkerSignature;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.RepeatShulkerCycle;
import com.hunterbuddy.behavior.nodes.RepeatUntil;
import com.hunterbuddy.behavior.nodes.Sequence;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;

public final class BasePackTree {
    private BasePackTree() {}

    public static Node resumeMaterial(BaseVisitContext visit) {
        return LogisticsOperations.unless("aucune shulker matériau de base interrompue",
            () -> visit.logistics.facts.material.signature.isEmpty() || visit.logistics.stored("material"),
            ShulkerCycleTree.build(visit.logistics, "material", () -> MaterialWorkTree.build(visit.logistics, "material")));
    }

    public static Node build(BaseVisitContext visit) {
        return new Sequence("pack-returned-material-and-return-miscellaneous",
            LogisticsOperations.unless("no loose accepted material",
                () -> visit.logistics.world.countMatching(LogisticsInventory.accepted(visit.logistics.profile)) == 0,
                cycle(visit)),
            LogisticsOperations.unless("no loose miscellaneous items", () -> miscellaneous(visit) == 0,
                new RepeatUntil("loose-miscellaneous-items-returned", () -> miscellaneous(visit) == 0, 72,
                    new Sequence("return-one-miscellaneous-stack",
                        BaseOperations.open(visit, () -> visit.position(visit.facts.returns)),
                        BaseOperations.transfer(visit, () -> visit.position(visit.facts.returns), "return-loose-miscellaneous",
                            () -> visit.logistics.outputs.inventory().firstDeposit(
                                LogisticsInventory.miscellaneous(visit.logistics.profile), FailureCode.MISSION_STOP))))),
            ContainerTree.close(visit.logistics.outputs),
            new InventoryMilestone("returned-items-packed-confirmed", ctx -> {
                if (visit.logistics.world.countMatching(LogisticsInventory.accepted(visit.logistics.profile)) > 0) {
                    throw new OperationFailure(FailureCode.MISSION_STOP, "base has no empty material shulker for the returned items: "
                        + visit.logistics.facts.material.unavailable);
                }
                visit.save(ctx, "returned material packed and miscellaneous items returned by confirmed transfers");
            }));
    }

    private static int miscellaneous(BaseVisitContext visit) {
        return visit.logistics.world.countMatching(LogisticsInventory.miscellaneous(visit.logistics.profile));
    }

    private static Node cycle(BaseVisitContext visit) {
        return new RepeatShulkerCycle(visit.logistics, "material", new Sequence("pack-with-empty-shulker-supply",
            BaseOperations.ender(visit),
            new InventoryMilestone("retry-packing-with-observed-stock", ctx -> {
                if (visit.logistics.cycle("material").signature.isEmpty()) {
                    visit.logistics.cycle("material").unavailable = "";
                }
            }),
            LogisticsOperations.unless("packing shulker capacity available", () -> capacity(visit),
                BaseEmptyShulkersTree.loadOne(visit)),
            ShulkerCycleTree.build(visit.logistics, "material", () -> MaterialWorkTree.build(visit.logistics, "material"))));
    }

    private static boolean capacity(BaseVisitContext visit) {
        List<ItemStack> carried = new ArrayList<>(LogisticsInventory.player(visit.logistics.world));
        carried.add(visit.logistics.world.client().player.getOffHandStack());
        for (int i = 0; i < visit.enderContents().size(); i++) {
            ItemStack stack = visit.enderContents().get(i);
            if (!ShulkerSignature.isShulker(stack) || visit.roleSlot(i, "reserve") || visit.roleSlot(i, "divers")) continue;
            ShulkerSignature signature = ShulkerSignature.of(stack, visit.logistics.world);
            if (signature.empty() || LogisticsInventory.pure(signature, visit.logistics.profile) && signature.contents().stream()
                .anyMatch(destination -> destination.isEmpty() || destination.getCount() < destination.getMaxCount()
                    && carried.stream().anyMatch(item -> LogisticsInventory.accepted(visit.logistics.profile).test(item)
                        && ItemStack.areItemsAndComponentsEqual(item, destination)))) return true;
        }
        return false;
    }
}
