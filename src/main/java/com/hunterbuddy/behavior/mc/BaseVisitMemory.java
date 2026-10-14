package com.hunterbuddy.behavior.mc;

import com.google.gson.Gson;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.BaseVisitFacts;
import com.hunterbuddy.behavior.farmer.MissionJournal;

public final class BaseVisitMemory {
    private static final Gson GSON = new Gson();
    private final MissionJournal journal;

    public BaseVisitMemory(MissionJournal journal) {
        this.journal = journal;
    }

    public BaseVisitFacts load(TickContext ctx) {
        String saved = journal.equipment(ctx, "base-visit-facts");
        if (saved.isEmpty()) {
            return null;
        }
        BaseVisitFacts facts = GSON.fromJson(saved, BaseVisitFacts.class);
        if (facts == null || facts.world == null || facts.profile == null || facts.transfer == null) {
            throw new IllegalStateException("invalid base visit journal, resume cannot be verified");
        }
        return facts;
    }

    public void save(TickContext ctx, BaseVisitFacts facts) {
        journal.equipment(ctx, "base-visit-facts", GSON.toJson(facts));
    }
}
