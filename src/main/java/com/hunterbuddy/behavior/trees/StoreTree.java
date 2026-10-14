package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.farmer.FarmerProfile;
import com.hunterbuddy.behavior.farmer.FarmerZone;
import com.hunterbuddy.behavior.farmer.MissionJournal;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.LogisticsContext;
import com.hunterbuddy.behavior.mc.LogisticsInventory;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.LogisticsRun;
import com.hunterbuddy.behavior.nodes.RepeatShulkerCycle;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.TaskScope;
import com.hunterbuddy.behavior.resource.Outputs;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class StoreTree {
    private StoreTree() {}

    public static Node build(Supplier<FarmerProfile> profile, Supplier<FarmerZone> zone, BooleanSupplier regear,
                             IntSupplier step, WorldReader world, BaritoneBridge baritone, Outputs outputs, MissionJournal journal) {
        return build(profile, zone, regear, () -> false, step, world, baritone, outputs, journal);
    }

    public static Node build(Supplier<FarmerProfile> profile, Supplier<FarmerZone> zone, BooleanSupplier regear,
                             BooleanSupplier exitTop, IntSupplier step, WorldReader world,
                             BaritoneBridge baritone, Outputs outputs, MissionJournal journal) {
        LogisticsContext logistics = new LogisticsContext(profile, zone, true, regear, exitTop,
            step, world, baritone, outputs, journal);
        return build(logistics);
    }

    public static Node build(LogisticsContext logistics) {
        return new TaskScope(new LogisticsRun(logistics, new Sequence("rangement",
            new InventoryMilestone("load-logistics-facts", logistics::initialize),
            LogisticsOperations.unless("ender chest recovery already started", logistics::finishing,
                new Sequence("prepare-and-work-field-storage", LogisticsSiteTree.build(logistics),
                    TrackedEquipmentTree.place(logistics, "chest"),
                    LogisticsOperations.pendingTransfer(logistics), material(logistics, "material"), material(logistics, "divers"),
                    LogisticsOperations.unless("regear is not due", () -> !logistics.facts.regear
                        || logistics.evacuationRequested.getAsBoolean() && logistics.cycle("reserve").signature.isEmpty(),
                        RegearTree.protectedCycle(logistics)))),
            LogisticsFinishTree.build(logistics))));
    }

    private static Node material(LogisticsContext logistics, String role) {
        return LogisticsOperations.unless("aucun objet " + role, () -> logistics.cycle(role).signature.isEmpty()
            && (logistics.evacuationRequested.getAsBoolean() || logistics.world.countMatching(role.equals("material")
                ? LogisticsInventory.accepted(logistics.profile) : LogisticsInventory.miscellaneous(logistics.profile)) == 0),
            new RepeatShulkerCycle(logistics, role, ShulkerCycleTree.build(logistics, role,
                () -> MaterialWorkTree.build(logistics, role))));
    }
}
