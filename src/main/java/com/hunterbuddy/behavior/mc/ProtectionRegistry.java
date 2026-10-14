package com.hunterbuddy.behavior.mc;

import com.google.gson.Gson;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.LogisticsFacts;
import com.hunterbuddy.behavior.farmer.MissionJournal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.util.math.BlockPos;

public final class ProtectionRegistry {
    private static final Gson GSON = new Gson();
    private ProtectionRegistry() {}
    public static void restore(TickContext ctx, MissionJournal journal, MiningBridge mining) {
        for (int[] center : centers(ctx, journal)) mining.protectShelter(LogisticsFacts.position(center));
    }

    public static void remember(TickContext ctx, MissionJournal journal, MiningBridge mining, BlockPos center) {
        List<int[]> centers = centers(ctx, journal);
        int[] coordinates = LogisticsFacts.coordinates(center);
        if (centers.stream().noneMatch(saved -> Arrays.equals(saved, coordinates))) {
            centers.add(coordinates);
            journal.equipment(ctx, "protection-sites", GSON.toJson(centers));
            journal.milestone(ctx, "protected shelter tracked at=" + center.toShortString());
        }
        mining.protectShelter(center);
    }

    public static BlockPos last(TickContext ctx, MissionJournal journal, LogisticsFacts facts) {
        if (facts.materialSite.isEmpty()) return null;
        String saved = journal.equipment(ctx, siteKey(facts));
        if (saved.isEmpty()) return null;
        int[] center = GSON.fromJson(saved, int[].class);
        return centers(ctx, journal).stream().anyMatch(pos -> Arrays.equals(pos, center))
            ? LogisticsFacts.position(center) : null;
    }

    public static void rememberSite(TickContext ctx, MissionJournal journal, LogisticsFacts facts) {
        if (!facts.materialSite.isEmpty()) journal.equipment(ctx, siteKey(facts), GSON.toJson(facts.site));
    }

    private static String siteKey(LogisticsFacts facts) {
        return "protection-last-site-" + facts.dimension + "/" + facts.profile + "/" + facts.materialSite;
    }

    private static List<int[]> centers(TickContext ctx, MissionJournal journal) {
        String saved = journal.equipment(ctx, "protection-sites");
        return saved.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(GSON.fromJson(saved, int[][].class)));
    }
}
