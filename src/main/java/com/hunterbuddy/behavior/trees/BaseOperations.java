package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.OperationFailure;
import com.hunterbuddy.behavior.farmer.LogisticsRoles;
import com.hunterbuddy.behavior.mc.BaseTransferReceipt;
import com.hunterbuddy.behavior.mc.BaseVisitContext;
import com.hunterbuddy.behavior.mc.InventoryTransaction;
import com.hunterbuddy.behavior.mc.LogisticsInventory;
import com.hunterbuddy.behavior.nodes.Deferred;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.ReconcileBaseTransfer;
import com.hunterbuddy.behavior.nodes.ReconcileLogisticsTransfer;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.Succeed;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.TransferItems;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.block.ChestBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

public final class BaseOperations {
    private BaseOperations() {}

    public static Node open(BaseVisitContext visit, Supplier<BlockPos> position) {
        return new Deferred("reach-and-open-base-chest", () -> {
            BlockPos pos = position.get();
            if (visit.configuredChest(pos) && visit.logistics.world.loaded(pos)
                && !(visit.logistics.world.state(pos).getBlock() instanceof ChestBlock)) {
                throw new OperationFailure(pos.equals(visit.position(visit.facts.delivery))
                    ? FailureCode.MISSION_STOP : FailureCode.MISSING_ITEM, "expected chest missing at " + pos.toShortString());
            }
            if (visit.logistics.outputs.inventory().at(pos)) {
                return new Succeed();
            }
            return new Sequence("base-chest-confirmed", ContainerTree.close(visit.logistics.outputs),
                GroundTravel.build(pos, 2, visit.logistics.baritone, visit.logistics.outputs),
                new InventoryMilestone("expected-chest-present", ctx -> {
                    if (!visit.logistics.world.loaded(pos)) {
                        throw new OperationFailure(FailureCode.UNREACHABLE, "chest not loaded at " + pos);
                    }
                    if (visit.configuredChest(pos) && !(visit.logistics.world.state(pos).getBlock() instanceof ChestBlock)) {
                        throw new OperationFailure(pos.equals(visit.position(visit.facts.delivery))
                            ? FailureCode.MISSION_STOP : FailureCode.MISSING_ITEM, "expected chest missing at " + pos.toShortString());
                    }
                }), ContainerTree.open(pos, visit.logistics.world, visit.logistics.outputs, visit.logistics.mission));
        });
    }

    public static Node ender(BaseVisitContext visit) {
        return new Sequence("base-ender-chest-verified", open(visit, () -> visit.logistics.facts.chest()),
            new Deferred("reconcile-base-ender-chest-contents", () -> new ReconcileLogisticsTransfer(visit.logistics,
                visit.logistics.facts.transfer.pending ? visit.logistics.facts.transfer.role : LogisticsRoles.RESERVE, false)),
            new InventoryMilestone("observe-base-loading", visit::observe));
    }

    public static Node transfer(BaseVisitContext visit, Supplier<BlockPos> position, String purpose,
                                Supplier<InventoryTransaction> transaction) {
        return transfer(visit, position, purpose, transaction, 120);
    }

    public static Node transfer(BaseVisitContext visit, Supplier<BlockPos> position, String purpose,
                                Supplier<InventoryTransaction> transaction, int timeout) {
        return new Deferred("base-transfer-" + purpose, () -> {
            BaseTransferReceipt receipt = new BaseTransferReceipt(visit, position.get(), purpose);
            return new Timeout(timeout, new WithLease(Set.of(Resource.INVENTORY), LeasePolicy.WAIT,
                new TransferItems(visit.logistics.outputs.inventory(), () -> {
                    try {
                        return transaction.get();
                    } catch (OperationFailure ex) {
                        throw new OperationFailure(ex.code(), purpose + " at chest " + position.get().toShortString() + ": " + ex.getMessage());
                    } catch (IllegalStateException ex) {
                        throw new OperationFailure(FailureCode.NO_OPTION, purpose + " at chest " + position.get().toShortString() + ": " + ex.getMessage());
                    }
                }, null, visit.logistics.mission,
                    purpose, receipt::before, ctx -> BaseTransferReceipt.accept(ctx, visit))));
        });
    }

    public static Node pending(BaseVisitContext visit) {
        return LogisticsOperations.unless("no interrupted base transfer", () -> !visit.facts.transfer.receipt.pending,
            new Sequence("resume-base-transfer", open(visit, () -> visit.position(visit.facts.transfer.position)),
                new ReconcileBaseTransfer(visit), ContainerTree.close(visit.logistics.outputs)));
    }

    public static ItemStack cargo(BaseVisitContext visit) {
        if (visit.facts.cargo.isEmpty()) {
            throw new IllegalStateException("no confirmed carried item");
        }
        return LogisticsInventory.decode(visit.facts.cargo, 1, visit.logistics.world).getFirst();
    }
}
