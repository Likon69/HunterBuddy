package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.farmer.MissionJournal;
import com.hunterbuddy.behavior.mc.FlightPlan;
import com.hunterbuddy.behavior.mc.InventoryLayout;
import com.hunterbuddy.behavior.nodes.Deferred;
import com.hunterbuddy.behavior.nodes.FlightEquipmentSwap;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.SteadyFlight;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.util.GliderSwap;
import com.hunterbuddy.util.Rockets;
import java.util.Set;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

public final class FlightEquipmentTree {
    private FlightEquipmentTree() {}

    public static Node wear(FlightPlan plan, boolean glider) {
        return LogisticsOperations.unless(glider ? "elytra budget equipped" : "chestplate held after landing",
            () -> glider ? !plan.renewalNeeded()
                : GliderSwap.isChestPiece(plan.world.client().player.getEquippedStack(EquipmentSlot.CHEST)),
            new Deferred(glider ? "choose-replacement-elytra" : "hold-chestplate", () -> {
                ItemStack chosen = glider ? plan.bestGlider() : chestplate(plan);
                if (chosen.isEmpty()) throw new IllegalStateException("wearable missing for flight budget or landing");
                ItemStack expected = chosen.copy();
                Node exchange = new Timeout(600, new Sequence("prepare-and-equip-flight-item",
                    EquipmentTree.prepareHeld(new InventoryLayout(plan.world), stack -> ItemStack.areEqual(stack, expected),
                        8, plan.outputs, new MissionJournal(8)),
                    new WithLease(Set.of(Resource.SLOT, Resource.INTERACT), LeasePolicy.WAIT,
                        new FlightEquipmentSwap(expected, plan.world, plan.outputs))));
                return steady(plan, exchange);
            }));
    }

    public static Node rockets(FlightPlan plan) {
        return LogisticsOperations.unless("plain rocket hotbar ready", () -> plan.world.findSlot(Rockets::isPlain) >= 0,
            new Deferred("prepare-plain-rockets", () -> steady(plan,
                EquipmentTree.prepareHeld(new InventoryLayout(plan.world), Rockets::isPlain, 3, plan.outputs, new MissionJournal(8)))));
    }

    private static Node steady(FlightPlan plan, Node operation) {
        return plan.world.client().player.isGliding()
            ? new WithLease(Set.of(Resource.MOVEMENT, Resource.LOOK), LeasePolicy.WAIT, new SteadyFlight(plan, operation))
            : operation;
    }

    private static ItemStack chestplate(FlightPlan plan) {
        ItemStack best = ItemStack.EMPTY;
        for (int i = 0; i < plan.world.client().player.getInventory().size(); i++) {
            ItemStack stack = plan.world.client().player.getInventory().getStack(i);
            if (GliderSwap.isChestPiece(stack) && stack.getCount() == 1 && !stack.willBreakNextUse()
                && (best.isEmpty() || FlightPlan.remaining(stack) > FlightPlan.remaining(best))) best = stack.copy();
        }
        return best;
    }
}
