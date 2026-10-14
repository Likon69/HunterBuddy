package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.farmer.FarmerProfile;
import com.hunterbuddy.behavior.farmer.FarmerZone;
import com.hunterbuddy.behavior.farmer.LogisticsRoles;
import com.hunterbuddy.behavior.farmer.MissionJournal;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.LogisticsContext;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.LogisticsRun;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.TaskScope;
import com.hunterbuddy.behavior.resource.Outputs;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class RegearTree {
    private RegearTree() {}

    public static Node build(Supplier<FarmerProfile> profile, Supplier<FarmerZone> zone, IntSupplier step,
                             WorldReader world, BaritoneBridge baritone, Outputs outputs, MissionJournal journal) {
        return build(profile, zone, () -> false, step, world, baritone, outputs, journal);
    }

    public static Node build(Supplier<FarmerProfile> profile, Supplier<FarmerZone> zone, BooleanSupplier exitTop,
                             IntSupplier step, WorldReader world, BaritoneBridge baritone, Outputs outputs, MissionJournal journal) {
        LogisticsContext logistics = new LogisticsContext(profile, zone, false, () -> true, exitTop,
            step, world, baritone, outputs, journal);
        return build(logistics);
    }

    public static Node build(LogisticsContext logistics) {
        return new TaskScope(new LogisticsRun(logistics, new Sequence("regear",
            new InventoryMilestone("load-logistics-facts", logistics::initialize),
            LogisticsOperations.unless("ender chest recovery already started", logistics::finishing,
                new Sequence("prepare-and-work-field-regear", LogisticsSiteTree.build(logistics),
                    TrackedEquipmentTree.place(logistics, LogisticsRoles.CHEST),
                    LogisticsOperations.pendingTransfer(logistics), protectedCycle(logistics))),
            LogisticsFinishTree.build(logistics))));
    }

    public static Node protectedCycle(LogisticsContext logistics) {
        return ShulkerCycleTree.build(logistics, LogisticsRoles.RESERVE, () -> ReserveWorkTree.build(logistics));
    }
}
