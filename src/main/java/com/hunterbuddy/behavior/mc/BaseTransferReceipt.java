package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.OperationFailure;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.BaseTransferFacts;
import com.hunterbuddy.behavior.farmer.LogisticsFacts;
import com.hunterbuddy.behavior.farmer.ShulkerCycleFacts;
import com.hunterbuddy.behavior.farmer.TransferFacts;
import java.util.List;
import net.minecraft.block.ChestBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

public final class BaseTransferReceipt {
    private final BaseVisitContext visit;
    private final BlockPos position;
    private final String purpose;

    public BaseTransferReceipt(BaseVisitContext visit, BlockPos position, String purpose) {
        this.visit = visit;
        this.position = position;
        this.purpose = purpose;
    }

    public void before(TickContext ctx, InventoryTransaction transaction) {
        BaseTransferFacts facts = visit.facts.transfer;
        TransferFacts receipt = facts.receipt;
        InventoryBridge inventory = visit.logistics.outputs.inventory();
        if (receipt.pending || !inventory.at(position)) {
            throw new IllegalStateException("transfert de base déjà en attente");
        }
        receipt.stack = LogisticsInventory.encode(List.of(inventory.sourceStack(transaction)), visit.logistics.world);
        receipt.deposit = inventory.playerSource(transaction);
        receipt.player = LogisticsInventory.encode(LogisticsInventory.player(visit.logistics.world), visit.logistics.world);
        receipt.contents = LogisticsInventory.encode(inventory.containerContents(), visit.logistics.world);
        receipt.purpose = purpose;
        receipt.pending = true;
        facts.position = LogisticsFacts.coordinates(position);
        facts.size = inventory.containerContents().size();
        facts.deliveredBefore = visit.logistics.mission.delivered(ctx, visit.facts.step);
        visit.save(ctx, "intention " + purpose + " vers " + position.toShortString());
    }

    public static void accept(TickContext ctx, BaseVisitContext visit) {
        BaseTransferFacts facts = visit.facts.transfer;
        TransferFacts receipt = facts.receipt;
        InventoryBridge inventory = visit.logistics.outputs.inventory();
        if (!receipt.pending || !inventory.at(visit.position(facts.position))) {
            throw new IllegalStateException("confirmation sans intention ni coffre correspondant");
        }
        List<ItemStack> beforePlayer = LogisticsInventory.decode(receipt.player, 36, visit.logistics.world);
        List<ItemStack> before = LogisticsInventory.decode(receipt.contents, facts.size, visit.logistics.world);
        List<ItemStack> after = inventory.containerContents();
        ItemStack expected = LogisticsInventory.decode(receipt.stack, 1, visit.logistics.world).getFirst();
        if (!LogisticsInventory.authorizedTransfer(beforePlayer, before, LogisticsInventory.player(visit.logistics.world),
            after, expected, receipt.deposit)) {
            throw new OperationFailure(FailureCode.MISSION_STOP, "ambiguous transfer " + receipt.purpose + ": exact conservation cannot be confirmed");
        }
        if (receipt.purpose.equals("livrer")) {
            if (!receipt.deposit || expected.getCount() != 1 || !ShulkerSignature.isShulker(expected)
                || !LogisticsInventory.full(ShulkerSignature.of(expected, visit.logistics.world), visit.logistics.profile)
                || !visit.position(facts.position).equals(visit.position(visit.facts.delivery))
                || !visit.logistics.world.loaded(visit.position(facts.position))
                || !(visit.logistics.world.state(visit.position(facts.position)).getBlock() instanceof ChestBlock)
                || visit.logistics.mission.delivered(ctx, visit.facts.step) != facts.deliveredBefore) {
                throw new OperationFailure(FailureCode.MISSION_STOP, "delivery has no unique evidence for the profile, chest and counter");
            }

        }
        if (receipt.purpose.startsWith("prendre-")) {
            if (receipt.deposit || !visit.facts.cargo.isEmpty()) {
                throw new OperationFailure(FailureCode.MISSION_STOP, "ambiguous base cargo transfer");
            }
            visit.facts.cargo = receipt.stack;
            visit.facts.cargoRole = receipt.purpose.substring("prendre-".length());
            visit.facts.cargoBaseline = count(beforePlayer, expected);
        }
        if (receipt.purpose.equals("retour-réserve")) {
            visit.facts.reserveReturned = receipt.stack;
            visit.logistics.mission.equipment(ctx, "logistics-reserve", "");
            visit.logistics.mission.equipment(ctx, "logistics-reserve-slot", "");
            resetRole(ctx, visit, "reserve");
        }
        if (receipt.purpose.equals("ranger-réserve") || receipt.purpose.equals("ranger-divers")) {
            String role = receipt.purpose.substring("ranger-".length()).equals("réserve") ? "reserve" : "divers";
            ShulkerSignature signature = ShulkerSignature.of(expected, visit.logistics.world);
            int slot = receiver(before, after, signature);
            visit.logistics.mission.equipment(ctx, "logistics-" + role, signature.encoded());
            visit.logistics.mission.equipment(ctx, "logistics-" + role + "-slot", Integer.toString(slot));
            if (role.equals("reserve")) visit.facts.reserveSupplied = signature.encoded();
            resetRole(ctx, visit, role);
        }
        if (receipt.deposit && !visit.facts.cargo.isEmpty()) {
            ItemStack cargo = LogisticsInventory.decode(visit.facts.cargo, 1, visit.logistics.world).getFirst();
            int remaining = count(LogisticsInventory.player(visit.logistics.world), cargo);
            boolean partial = visit.facts.cargoRole.equals("divers-contenu");
            if (!ItemStack.areItemsAndComponentsEqual(expected, cargo)
                || remaining < visit.facts.cargoBaseline || !partial && remaining != visit.facts.cargoBaseline) {
                throw new OperationFailure(FailureCode.MISSION_STOP, "cargo receipt cannot be reconciled with the actual chest");
            }
            if (remaining == visit.facts.cargoBaseline) {
                visit.facts.cargo = "";
                visit.facts.cargoRole = "";
            } else {
                ItemStack rest = cargo.copy();
                rest.setCount(remaining - visit.facts.cargoBaseline);
                visit.facts.cargo = LogisticsInventory.encode(List.of(rest), visit.logistics.world);
            }
        }
        BlockPos pos = visit.position(facts.position);
        if (pos.equals(visit.logistics.facts.shulker())) {
            if (!receipt.purpose.equals("prendre-divers-contenu")) {
                throw new IllegalStateException("transfert sans rôle confirmé dans la shulker posée");
            }
            TemporaryEquipment equipment = visit.logistics.equipment("divers");
            equipment.contents(ctx, inventory);
            visit.logistics.facts.divers.signature = equipment.signature().encoded();
        }
        if (pos.equals(visit.logistics.facts.chest())) visit.observe(ctx);
        if (receipt.purpose.equals("livrer")) {
            visit.logistics.mission.delivered(ctx, visit.facts.step, facts.deliveredBefore + 1);
        }
        receipt.pending = false;
        visit.save(ctx, "transfert confirmé " + receipt.purpose);
    }

    private static void resetRole(TickContext ctx, BaseVisitContext visit, String role) {
        if (role.equals("reserve")) visit.logistics.facts.reserve = new ShulkerCycleFacts();
        if (role.equals("divers")) visit.logistics.facts.divers = new ShulkerCycleFacts();
        visit.logistics.forget(role);
        visit.logistics.mission.equipment(ctx, visit.logistics.equipmentName(role), "");
    }

    private static int count(List<ItemStack> stacks, ItemStack expected) {
        return stacks.stream().filter(stack -> ItemStack.areItemsAndComponentsEqual(stack, expected))
            .mapToInt(ItemStack::getCount).sum();
    }

    private static int receiver(List<ItemStack> before, List<ItemStack> after, ShulkerSignature signature) {
        int slot = -1;
        for (int i = 0; i < after.size(); i++) {
            if (!before.get(i).isEmpty() || !signature.matches(after.get(i))) continue;
            if (slot >= 0) {
                throw new OperationFailure(FailureCode.MISSION_STOP, "several slots received the role shulker");
            }
            slot = i;
        }
        if (slot < 0) {
            throw new OperationFailure(FailureCode.MISSION_STOP, "shulker receiving slot cannot be confirmed");
        }
        return slot;
    }
}
