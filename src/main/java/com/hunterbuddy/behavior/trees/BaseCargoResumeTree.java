package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.farmer.LogisticsRoles;
import com.hunterbuddy.behavior.farmer.TransferPurposes;
import com.hunterbuddy.behavior.mc.BaseVisitContext;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Fail;
import com.hunterbuddy.behavior.nodes.Guard;
import com.hunterbuddy.behavior.nodes.Selector;
import java.util.function.Supplier;
import net.minecraft.util.math.BlockPos;

public final class BaseCargoResumeTree {
    private BaseCargoResumeTree() {}

    public static Node build(BaseVisitContext visit) {
        return LogisticsOperations.unless("no base item carried", () -> visit.facts.cargo.isEmpty(),
            new Selector("identified-item-destination",
                send(visit, LogisticsRoles.DELIVERY, () -> visit.position(visit.facts.delivery), TransferPurposes.DELIVER),
                send(visit, LogisticsRoles.MATERIAL_SURPLUS, () -> visit.position(visit.facts.returns), TransferPurposes.RETURN_SURPLUS),
                send(visit, LogisticsRoles.MISCELLANEOUS_CONTENTS, () -> visit.position(visit.facts.returns), TransferPurposes.RETURN_MISCELLANEOUS),
                send(visit, LogisticsRoles.USED_RESERVE, () -> visit.position(visit.facts.returns), TransferPurposes.RETURN_RESERVE),
                send(visit, LogisticsRoles.PREPARED_RESERVE, () -> visit.logistics.facts.chest(), TransferPurposes.STORE_RESERVE),
                send(visit, LogisticsRoles.EXCESS_EMPTIES, () -> visit.position(visit.facts.empties), TransferPurposes.RETURN_EMPTIES),
                send(visit, LogisticsRoles.EMPTIES, () -> visit.logistics.facts.chest(), TransferPurposes.STORE_EMPTIES),
                send(visit, LogisticsRoles.EMPTY_MISCELLANEOUS, () -> visit.logistics.facts.chest(), TransferPurposes.STORE_MISCELLANEOUS),
                new Fail(FailureCode.MISSION_STOP, "unknown cargo role: transfer cannot be reconciled")).propagateFailures());
    }

    private static Node send(BaseVisitContext visit, String role, Supplier<BlockPos> position, String purpose) {
        return new Guard(new Condition("carried role " + role, sensors -> visit.facts.cargoRole.equals(role)),
            BaseCargoTree.send(visit, position, purpose))
            .stayWhile(new Condition("transfer destination already selected", sensors -> true));
    }
}
