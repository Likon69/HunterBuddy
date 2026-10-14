package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.BaseTransferFacts;
import com.hunterbuddy.behavior.mc.BaseTransferReceipt;
import com.hunterbuddy.behavior.mc.BaseVisitContext;
import com.hunterbuddy.behavior.mc.LogisticsInventory;

public final class ReconcileBaseTransfer extends Node {
    private final BaseVisitContext visit;

    public ReconcileBaseTransfer(BaseVisitContext visit) {
        super("reconcile-base-transfer-evidence");
        this.visit = visit;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        BaseTransferFacts facts = visit.facts.transfer;
        if (!facts.receipt.pending) {
            return Success.INSTANCE;
        }
        if (!visit.logistics.outputs.inventory().at(visit.position(facts.position))) {
            throw new IllegalStateException("wrong chest for reconciling the base transfer");
        }
        boolean playerUnchanged = LogisticsInventory.equal(LogisticsInventory.decode(facts.receipt.player, 36,
            visit.logistics.world), LogisticsInventory.player(visit.logistics.world));
        boolean containerUnchanged = LogisticsInventory.equal(LogisticsInventory.decode(facts.receipt.contents, facts.size,
            visit.logistics.world), visit.logistics.outputs.inventory().containerContents());
        if (playerUnchanged && containerUnchanged) {
            facts.receipt.pending = false;
            visit.save(ctx, "transfer not applied: both inventories unchanged");
        } else {
            BaseTransferReceipt.accept(ctx, visit);
        }
        return Success.INSTANCE;
    }
}
