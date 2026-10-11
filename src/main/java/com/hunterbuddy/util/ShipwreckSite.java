package com.hunterbuddy.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;

public final class ShipwreckSite {
    public static final int MIN_PRESENT = 64;
    public record Key(String model, BlockRotation rotation, int originX, int originZ) {}
    public record ChunkScore(int chunkX, int chunkZ, int originY, int present, int missing, int contradictory) {
        public int total() { return present + missing + contradictory; }
        public boolean cut() { return total() > 0 && (long) present * 100 <= (long) total() * 10; }
    }
    public enum ChestStatus { PRESENT, ABSENT, CUT }
    public record Chest(String type, BlockPos pos, ChestStatus status) {}
    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}

    private Key key;
    private final Map<Long, ChunkScore> chunks = new LinkedHashMap<>();
    private final Set<Long> anchoredChunks = new HashSet<>();
    private List<Chest> chests = List.of();
    private int originY;
    private boolean found;
    private Bounds bounds;

    ShipwreckSite(Key key, int originY) {
        this.key = key;
        this.originY = originY;
    }

    void merge(ChunkScore score, boolean anchorChunk) {
        long chunkKey = chunkKey(score.chunkX, score.chunkZ);
        ChunkScore previous = chunks.get(chunkKey);
        boolean wasAnchored = anchoredChunks.contains(chunkKey);
        if (previous == null || (anchorChunk && !wasAnchored)
            || ((anchorChunk || !wasAnchored) && (long) score.present * previous.total() > (long) previous.present * score.total())) {
            chunks.put(chunkKey, score);
        }
        if (anchorChunk) anchoredChunks.add(chunkKey);
    }

    void setChests(List<Chest> chests) { this.chests = List.copyOf(chests); }
    void setBounds(ShipwreckModels.Variant variant) {
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (ChunkScore chunk : chunks.values()) {
            if (chunk.cut()) continue;
            minY = Math.min(minY, chunk.originY());
            maxY = Math.max(maxY, chunk.originY());
        }
        if (minY == Integer.MAX_VALUE) minY = maxY = originY;
        bounds = new Bounds(key.originX() + variant.minX(), minY + variant.minY(), key.originZ() + variant.minZ(),
            key.originX() + variant.maxX(), maxY + variant.maxY(), key.originZ() + variant.maxZ());
    }
    void markFound() { found = true; }
    void adopt(ShipwreckSite other) {
        key = other.key;
        originY = other.originY;
        chunks.clear();
        chunks.putAll(other.chunks);
        anchoredChunks.clear();
        anchoredChunks.addAll(other.anchoredChunks);
        chests = other.chests;
        bounds = other.bounds;
    }
    public Key key() { return key; }
    public BlockPos origin() { return new BlockPos(key.originX, originY, key.originZ); }
    public List<Chest> chests() { return chests; }
    public List<ChunkScore> chunks() { return new ArrayList<>(chunks.values()); }
    public boolean found() { return found; }
    public Bounds bounds() { return bounds; }
    public Chest nearestChest(double x, double y, double z, Predicate<BlockPos> visited) {
        Chest best = null;
        double distance = Double.MAX_VALUE;
        for (Chest chest : chests) {
            if (chest.status != ChestStatus.PRESENT || visited.test(chest.pos)) continue;
            double dx = chest.pos.getX() + 0.5 - x;
            double dy = chest.pos.getY() + 0.5 - y;
            double dz = chest.pos.getZ() + 0.5 - z;
            double next = dx * dx + dy * dy + dz * dz;
            if (next < distance) {
                best = chest;
                distance = next;
            }
        }
        return best;
    }
    ChunkScore chunk(int x, int z) { return chunks.get(chunkKey(x, z)); }

    private static long chunkKey(int x, int z) { return (x & 0xffffffffL) | ((long) z << 32); }

    public int present() { return count(0); }
    public int missing() { return count(1); }
    public int contradictory() { return count(2); }
    public int total() { return present() + missing() + contradictory(); }
    public boolean matchesModel() { return present() >= MIN_PRESENT && (long) present() * 100 >= (long) total() * 40; }

    private int count(int kind) {
        int total = 0;
        for (ChunkScore score : chunks.values()) {
            if (!score.cut()) total += kind == 0 ? score.present : kind == 1 ? score.missing : score.contradictory;
        }
        return total;
    }

    String describe() {
        StringBuilder text = new StringBuilder("model=").append(key.model).append(" rotation=").append(key.rotation)
            .append(" origin=").append(key.originX).append(',').append(originY).append(',').append(key.originZ)
            .append(" present=").append(present()).append(" missing=").append(missing())
            .append(" contradictory=").append(contradictory()).append(" minPresent=").append(MIN_PRESENT);
        for (ChunkScore score : chunks.values()) {
            if (score.cut()) text.append(" cut=").append(score.chunkX).append(',').append(score.chunkZ);
            text.append(" chunk=").append(score.chunkX).append(',').append(score.chunkZ)
                .append(" y=").append(score.originY).append(" counts=").append(score.present).append('/')
                .append(score.missing).append('/').append(score.contradictory);
        }
        for (Chest chest : chests) {
            text.append(" chest=").append(chest.type).append(',').append(chest.pos.getX()).append(',')
                .append(chest.pos.getY()).append(',').append(chest.pos.getZ()).append(',')
                .append(chest.status.name().toLowerCase(java.util.Locale.ROOT));
        }
        return text.toString();
    }
}
