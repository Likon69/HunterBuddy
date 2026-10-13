package com.hunterbuddy.behavior.farmer;

import com.google.gson.Gson;
import com.hunterbuddy.behavior.core.TickContext;

public final class LogisticsMemory {
    private static final Gson GSON = new Gson();
    private final MissionJournal journal;
    private final String owner;
    private final String key;

    public LogisticsMemory(MissionJournal journal, String owner) {
        this(journal, owner, "logistics-facts");
    }

    public LogisticsMemory(MissionJournal journal, String owner, String key) {
        this.key = key;
        this.journal = journal;
        this.owner = owner;
    }

    public LogisticsFacts load(TickContext ctx) {
        String saved = journal.equipment(ctx, key);
        return saved.isEmpty() ? null : GSON.fromJson(saved, LogisticsFacts.class);
    }

    public void save(TickContext ctx, LogisticsFacts facts, String milestone) {
        journal.equipment(ctx, owner, key, GSON.toJson(facts));
        journal.milestone(ctx, owner, milestone);
    }

    public static boolean active(TickContext ctx, MissionJournal journal) {
        LogisticsFacts facts = new LogisticsMemory(journal, ctx.owner()).load(ctx);
        return facts != null && facts.departure == null;
    }
}
