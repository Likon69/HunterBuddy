package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.OperationFailure;
import com.hunterbuddy.behavior.mc.BaseVisitContext;
import com.hunterbuddy.behavior.mc.LogisticsInventory;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.RepeatUntil;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.util.Rockets;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

public final class BaseFuelTree {
    private BaseFuelTree() {}

    public static Node recoverStock(BaseVisitContext visit) {
        return new Sequence("préparer-fusées-déjà-transportées", BaseOperations.ender(visit),
            new RepeatUntil("budget-prêt-ou-stock-ender-épuisé", () -> ready(visit)
                || visit.enderContents().stream().noneMatch(Rockets::isPlain), 128,
                take(visit, () -> visit.logistics.facts.chest(), "carburant-transporté")),
            ContainerTree.close(visit.logistics.outputs));
    }

    public static Node build(BaseVisitContext visit) {
        return new Sequence("plein-de-départ-confirmé",
            LogisticsOperations.unless("budget de départ déjà prêt", () -> ready(visit),
                new Sequence("compléter-carburant-mesuré",
                    BaseOperations.open(visit, () -> visit.position(visit.facts.supply)),
                    new RepeatUntil("budget-prêt-pour-décoller", () -> ready(visit), 512,
                        take(visit, () -> visit.position(visit.facts.supply), "ravitailler-fusées")),
                    ContainerTree.close(visit.logistics.outputs))),
            new InventoryMilestone("plein-confirmé", ctx -> visit.save(ctx, "fusées confirmées pour l'aller-retour mesuré")));
    }

    private static boolean ready(BaseVisitContext visit) {
        return visit.logistics.world.countMatching(Rockets::isPlain) >= visit.facts.rocketBudget;
    }

    private static Node take(BaseVisitContext visit, Supplier<BlockPos> source, String purpose) {
        return new Sequence("prendre-une-pile-de-fusées",
            new InventoryMilestone("place-pour-fusées", ctx -> {
                if (LogisticsInventory.player(visit.logistics.world).stream().filter(ItemStack::isEmpty).count() < 3) {
                    throw new IllegalStateException("inventaire sans place pour les fusées, "
                        + "le ramassage de l'ender chest et le slot libre");
                }
            }), BaseOperations.transfer(visit, source, purpose, () -> {
                List<ItemStack> contents = visit.logistics.outputs.inventory().containerContents();
                for (int i = 0; i < contents.size(); i++) {
                    if (Rockets.isPlain(contents.get(i))) {
                        return visit.logistics.outputs.inventory().takeStack(i, contents.get(i));
                    }
                }
                throw new OperationFailure(FailureCode.MISSION_STOP, "base has no plain rockets in the supply chest");
            }));
    }
}
