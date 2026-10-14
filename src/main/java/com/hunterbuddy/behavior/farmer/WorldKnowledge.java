package com.hunterbuddy.behavior.farmer;

import com.google.gson.Gson;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

public final class WorldKnowledge {
    public static final String OWNER = "bot:world-knowledge";
    private static final int MAX_BIOMES = 200_000;
    private static final Gson GSON = new Gson();
    private static final Key<String> DATA = new Key<>("world-knowledge", String.class, Scope.WORLD, true);
    private static final Key<WorldKnowledge> CACHE = new Key<>("knowledge-cache", WorldKnowledge.class, Scope.WORLD, false);
    public Map<String, BiomeObservation> biomes = new LinkedHashMap<>();
    public Map<String, ZoneKnowledge> zones = new LinkedHashMap<>();
    public int[] biomeBase;
    public int biomeRadius = 3000;
    private transient long savedAt = Long.MIN_VALUE;

    public static WorldKnowledge load(TickContext ctx) {
        WorldKnowledge cached = ctx.bb().get(OWNER, CACHE).orElse(null);
        if (cached != null) return cached;
        String data = ctx.bb().get(OWNER, DATA).orElse("");
        WorldKnowledge result = data.isEmpty() ? new WorldKnowledge() : GSON.fromJson(data, WorldKnowledge.class);
        if (result == null || result.biomes == null || result.zones == null) {
            throw new IllegalStateException("invalid saved world knowledge");
        }
        result.orderBiomes();
        ctx.bb().set(OWNER, CACHE, result);
        return result;
    }

    public ZoneKnowledge zone(FarmerZone zone, FarmerProfile profile) {
        StringBuilder key = new StringBuilder(profile.key());
        for (BlockPos corner : zone.corners()) key.append('/').append(corner.getX()).append(',').append(corner.getZ());
        ZoneKnowledge memory = zones.computeIfAbsent(key.toString(), ignored -> new ZoneKnowledge());
        memory.layer = profile.strategy().equals("layer");
        return memory;
    }

    public void save(TickContext ctx) {
        if (savedAt != Long.MIN_VALUE && ctx.nowMs() - savedAt < 60_000) return;
        saveNow(ctx);
    }

    public void saveNow(TickContext ctx) {
        ctx.bb().set(OWNER, DATA, GSON.toJson(this));
        ctx.checkpoint(OWNER);
        savedAt = ctx.nowMs();
    }

    public void biomeBounds(BlockPos home, int radius) {
        biomeBase = LogisticsFacts.coordinates(home);
        biomeRadius = radius;
    }

    private boolean acceptsBiome(ChunkPos chunk) {
        return biomeBase == null || Math.hypot(chunk.getCenterX() - biomeBase[0],
            chunk.getCenterZ() - biomeBase[2]) <= biomeRadius;
    }

    public void observeBiome(String key, BiomeObservation observation) {
        biomes.remove(key);
        biomes.put(key, observation);
        if (biomes.size() > MAX_BIOMES) biomes.remove(biomes.keySet().iterator().next());
    }

    private void orderBiomes() {
        Map<String, BiomeObservation> ordered = new LinkedHashMap<>();
        biomes.entrySet().stream().sorted(Comparator.comparingLong(entry -> entry.getValue().observedAt()))
            .skip(Math.max(0, biomes.size() - MAX_BIOMES))
            .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        biomes = ordered;
    }

    public List<ChunkPos> biomeHints(FarmerProfile profile, BlockPos from, int limit) {
        if (limit < 1 || profile.biomes().isEmpty()) return List.of();
        return biomes.entrySet().stream().filter(entry -> profile.biomes().contains(entry.getValue().biome()))
            .filter(entry -> acceptsBiome(chunk(entry.getKey())))
            .sorted(Comparator.comparingLong((Map.Entry<String, BiomeObservation> entry) -> entry.getValue().observedAt()).reversed())
            .limit(128).map(entry -> chunk(entry.getKey()))
            .sorted(Comparator.comparingDouble(pos -> Math.hypot(pos.getCenterX() - from.getX(), pos.getCenterZ() - from.getZ())))
            .limit(limit).toList();
    }

    private ChunkPos chunk(String key) {
        String[] coordinates = key.split(",");
        if (coordinates.length != 2) throw new IllegalStateException("invalid biome chunk identity");
        return new ChunkPos(Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1]));
    }
}
