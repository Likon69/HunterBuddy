package com.hunterbuddy.behavior.mc;

import com.google.gson.Gson;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.MineSiteFacts;
import com.hunterbuddy.behavior.farmer.MissionJournal;

public final class MineSiteMemory {
    private static final Gson GSON = new Gson();
    private static final String NAME = "mine-site";
    private final MissionJournal journal;

    public MineSiteMemory(MissionJournal journal) {
        this.journal = journal;
    }

    public MineSiteFacts load(TickContext ctx) {
        String data = journal.equipment(ctx, NAME);
        if (data.isEmpty()) return null;
        MineSiteFacts facts = GSON.fromJson(data, MineSiteFacts.class);
        if (facts == null || facts.world == null || facts.profile == null || facts.stairs == null || facts.gallery == null
            || facts.supports == null || facts.before == null || facts.entities == null || facts.drops == null
            || facts.dropCounts == null || facts.blocks == null || facts.siteState == null || facts.changedChunks == null
            || facts.mined < 0 || facts.excavated < 0) {
            throw new IllegalStateException("invalid saved mining facts");
        }
        return facts;
    }

    public void save(TickContext ctx, MineSiteFacts facts, String milestone) {
        journal.equipment(ctx, NAME, GSON.toJson(facts));
        journal.milestone(ctx, milestone);
    }
}
