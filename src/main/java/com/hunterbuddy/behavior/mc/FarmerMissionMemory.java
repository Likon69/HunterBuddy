package com.hunterbuddy.behavior.mc;

import com.google.gson.Gson;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.FarmerMissionFacts;
import com.hunterbuddy.behavior.farmer.MissionJournal;

public final class FarmerMissionMemory {
    private static final Gson GSON = new Gson();
    private final MissionJournal journal;

    public FarmerMissionMemory(MissionJournal journal) {
        this.journal = journal;
    }

    public FarmerMissionFacts load(TickContext ctx) {
        String data = journal.equipment(ctx, "mission-facts");
        FarmerMissionFacts facts = data.isEmpty() ? new FarmerMissionFacts() : GSON.fromJson(data, FarmerMissionFacts.class);
        if (facts == null || facts.world == null || facts.profile == null || facts.occupied == null) {
            throw new IllegalStateException("faits de mission invalides, reprise ambiguë");
        }
        return facts;
    }

    public void save(TickContext ctx, FarmerMissionFacts facts, String milestone) {
        journal.equipment(ctx, "mission-facts", GSON.toJson(facts));
        journal.milestone(ctx, milestone);
    }
}
