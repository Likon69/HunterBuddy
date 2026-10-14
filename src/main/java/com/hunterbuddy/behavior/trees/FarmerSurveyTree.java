package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.farmer.FarmerSettings;
import com.hunterbuddy.behavior.mc.FarmerMissionContext;
import com.hunterbuddy.behavior.mc.SurveyContext;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.Timeout;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class FarmerSurveyTree {
    private FarmerSurveyTree() {}

    public static Node build(FarmerMissionContext mission) {
        SurveyContext survey = new SurveyContext(mission.world, mission.baritone, mission.outputs, mission.catalog,
            mission.lookup, mission.settings.steps(), CompletableFuture.completedFuture(Map.of()),
            mission.step.deliveryChest(), mission.world.storageKey());
        survey.missionMode = true;
        survey.eligible = mission::eligible;
        survey.returnRequested = () -> mission.fuelDue()
            || (mission.players() || mission.facts.changeRequested) && mission.best() == null;
        return new Sequence("survey-then-return-control-to-mission",
            new Timeout(FarmerSettings.ACTIVITY_TIMEOUT_TICKS, SurveyTree.build(survey)),
            new InventoryMilestone("mission-survey-confirmed", ctx -> {
                mission.select(ctx, survey.interrupted ? mission.best() : survey.selected);
                if (survey.interrupted) {
                    mission.facts.baseRequested = mission.fuelDue() || mission.selected == null;
                    mission.facts.waitAtBase = mission.selected == null && mission.occupiedOnly();
                    mission.save(ctx, "survey interrupted; next site or base resupply selected");
                }
            }));
    }
}
