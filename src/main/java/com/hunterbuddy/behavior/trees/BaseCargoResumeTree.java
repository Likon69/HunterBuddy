package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
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
        return LogisticsOperations.unless("aucun objet de base transporté", () -> visit.facts.cargo.isEmpty(),
            new Selector("destination-de-l'objet-identifié",
                send(visit, "livraison", () -> visit.position(visit.facts.delivery), "livrer"),
                send(visit, "surplus-matériau", () -> visit.position(visit.facts.returns), "retour-surplus"),
                send(visit, "divers-contenu", () -> visit.position(visit.facts.returns), "retour-divers"),
                send(visit, "réserve-usée", () -> visit.position(visit.facts.returns), "retour-réserve"),
                send(visit, "réserve-préparée", () -> visit.logistics.facts.chest(), "ranger-réserve"),
                send(visit, "vides-excédentaires", () -> visit.position(visit.facts.empties), "retour-vides"),
                send(visit, "vides", () -> visit.logistics.facts.chest(), "ranger-vides"),
                send(visit, "divers-vide", () -> visit.logistics.facts.chest(), "ranger-divers"),
                new Fail(FailureCode.MISSION_STOP, "unknown cargo role: transfer cannot be reconciled")).propagateFailures());
    }

    private static Node send(BaseVisitContext visit, String role, Supplier<BlockPos> position, String purpose) {
        return new Guard(new Condition("rôle transporté " + role, sensors -> visit.facts.cargoRole.equals(role)),
            BaseCargoTree.send(visit, position, purpose))
            .stayWhile(new Condition("destination du transfert déjà choisie", sensors -> true));
    }
}
