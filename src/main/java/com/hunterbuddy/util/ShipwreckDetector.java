package com.hunterbuddy.util;

import com.hunterbuddy.HunterBuddyAddon;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

public final class ShipwreckDetector {
    private static final long BUDGET_NS = 1_000_000L;
    private static final long STEP_RESERVE_NS = 100_000L;
    private static final long RETRY_NS = 2_000_000_000L;
    private static final long COST_NS = 60_000_000_000L;
    private static final ShipwreckDetector INSTANCE = new ShipwreckDetector();
    private LongOpenHashSet seen = new LongOpenHashSet();
    private Long2ObjectOpenHashMap<Bucket> buckets = new Long2ObjectOpenHashMap<>();
    private ArrayDeque<Bucket> pruning = new ArrayDeque<>();
    private ArrayDeque<Job> pending = new ArrayDeque<>();
    private Map<ShipwreckSite.Key, ShipwreckSite> candidates = new LinkedHashMap<>();
    private LinkedHashSet<ShipwreckSite> sites = new LinkedHashSet<>();
    private Long2ObjectOpenHashMap<ShipwreckSite> chestSites = new Long2ObjectOpenHashMap<>();
    private Map<ShipwreckSite, LongOpenHashSet> registeredChests = new LinkedHashMap<>();
    private final ArrayDeque<CollectedChest> collected = new ArrayDeque<>();
    private final BlockPos.Mutable cursor = new BlockPos.Mutable();
    private ClientWorld world;
    private Object session;
    private ShipwreckModels models;
    private CompletableFuture<ShipwreckModels> loading;
    private Job active;
    private boolean disabled;
    private long costStart;
    private long tested;
    private long hypotheses;
    private long reads;
    private long totalNs;
    private long maxTickNs;
    private long tickNs;
    private Predicate<BlockPos> visited = pos -> false;

    private ShipwreckDetector() {}

    public static void collect(ClientWorld world, BlockPos chest) {
        long start = System.nanoTime();
        try {
            if (!INSTANCE.disabled && world.getRegistryKey() == World.OVERWORLD) {
                INSTANCE.collected.addLast(new CollectedChest(world, chest.toImmutable()));
            }
        } catch (RuntimeException e) {
            INSTANCE.disable(e);
        } finally {
            INSTANCE.recordTime(System.nanoTime() - start);
        }
    }

    public static void tick(ClientWorld world) {
        tick(world, pos -> false);
    }

    public static void tick(ClientWorld world, Predicate<BlockPos> visited) {
        long start = System.nanoTime();
        INSTANCE.tickNs = 0;
        try {
            INSTANCE.visited = visited;
            INSTANCE.process(world, start);
        } catch (RuntimeException e) {
            INSTANCE.disable(e);
        } finally {
            INSTANCE.recordTime(System.nanoTime() - start);
        }
    }

    public static Collection<ShipwreckSite> foundSites() {
        return Collections.unmodifiableSet(INSTANCE.sites);
    }

    private void disable(RuntimeException e) {
        if (disabled) return;
        disabled = true;
        resetPending();
        collected.clear();
        CompletableFuture.runAsync(() -> HunterBuddyAddon.LOG.warn("[SpawnerDetector/shipwreck] disabled", e));
    }

    private void resetPending() {
        seen = new LongOpenHashSet();
        buckets = new Long2ObjectOpenHashMap<>();
        pruning = new ArrayDeque<>();
        pending = new ArrayDeque<>();
        candidates = new LinkedHashMap<>();
        active = null;
    }

    private void useWorld(ClientWorld next, long now) {
        if (world == next) return;
        Object nextSession = MinecraftClient.getInstance().getNetworkHandler();
        Job finishing = session == nextSession && active != null && active.completion != null
            && active.completion.phase.ordinal() >= CompletionPhase.OVERLAPS.ordinal() ? active : null;
        if (session != nextSession) {
            sites = new LinkedHashSet<>();
            chestSites = new Long2ObjectOpenHashMap<>();
            registeredChests = new LinkedHashMap<>();
            session = nextSession;
        }
        world = next;
        resetPending();
        active = finishing;
        costStart = now;
        tested = hypotheses = reads = totalNs = maxTickNs = 0;
    }

    private void enqueue(BlockPos chest) {
        long key = chest.asLong();
        if (!seen.add(key)) return;
        long chunk = ChunkPos.toLong(chest.getX() >> 4, chest.getZ() >> 4);
        Bucket bucket = buckets.get(chunk);
        if (bucket == null) {
            bucket = new Bucket(chunk, chest.getX() >> 4, chest.getZ() >> 4);
            buckets.put(chunk, bucket);
            pruning.addLast(bucket);
        }
        bucket.positions.add(key);
        pending.addLast(new Job(key, chest.getX(), chest.getY(), chest.getZ()));
    }

    private static boolean hasBudget(long deadline) {
        return System.nanoTime() + STEP_RESERVE_NS < deadline;
    }

    private void process(ClientWorld next, long start) {
        if (disabled || next.getRegistryKey() != World.OVERWORLD) return;
        useWorld(next, start);
        if (loading == null) loading = ShipwreckModels.loadOnce();
        if (models == null) {
            if (!loading.isDone()) return;
            models = loading.join();
        }
        long deadline = start + BUDGET_NS;
        if (start - costStart >= COST_NS && hasBudget(deadline)) logCost(start);
        prune(Math.min(deadline, System.nanoTime() + 200_000L));
        int waiting = pending.size();
        while (hasBudget(deadline)) {
            if (!collected.isEmpty()) {
                CollectedChest chest = collected.removeFirst();
                if (chest.world() == world) {
                    enqueue(chest.pos());
                    waiting++;
                }
                continue;
            }
            if (active == null) {
                if (waiting-- <= 0 || pending.isEmpty()) break;
                Job job = pending.removeFirst();
                if (!world.getChunkManager().isChunkLoaded(job.x >> 4, job.z >> 4)) {
                    seen.remove(job.key);
                    continue;
                }
                if (job.notBefore > start) {
                    pending.addLast(job);
                    continue;
                }
                active = job;
            }
            if ((active.completion == null || active.completion.phase.ordinal() < CompletionPhase.OVERLAPS.ordinal())
                && !world.getChunkManager().isChunkLoaded(active.x >> 4, active.z >> 4)) {
                seen.remove(active.key);
                active = null;
                continue;
            }
            if (hasBudget(deadline)) step(active, deadline);
        }
    }

    private void logCost(long start) {
        long testedCount = tested, hypothesesCount = hypotheses, readCount = reads, elapsed = totalNs, maximum = maxTickNs;
        CompletableFuture.runAsync(() -> HunterBuddyAddon.LOG.info(
            "[SpawnerDetector/shipwreck] cost tested={} hypotheses={} reads={} totalMs={} maxTickMs={}",
            testedCount, hypothesesCount, readCount, elapsed / 1_000_000.0, maximum / 1_000_000.0));
        costStart = start;
        tested = hypotheses = reads = totalNs = maxTickNs = 0;
    }

    private void recordTime(long elapsed) {
        tickNs += elapsed;
        totalNs += elapsed;
        maxTickNs = Math.max(maxTickNs, tickNs);
    }

    private void prune(long deadline) {
        int remaining = pruning.size();
        while (remaining-- > 0 && !pruning.isEmpty() && hasBudget(deadline)) {
            Bucket bucket = pruning.removeFirst();
            if (world.getChunkManager().isChunkLoaded(bucket.x, bucket.z)) {
                pruning.addLast(bucket);
                continue;
            }
            while (bucket.removed < bucket.positions.size() && hasBudget(deadline)) {
                seen.remove(bucket.positions.getLong(bucket.removed++));
            }
            if (bucket.removed < bucket.positions.size()) pruning.addFirst(bucket);
            else buckets.remove(bucket.key);
        }
    }

    private void step(Job job, long deadline) {
        if (job.model == models.models.length) {
            tested++;
            active = null;
            return;
        }
        ShipwreckModels.Model model = models.models[job.model];
        ShipwreckModels.Variant variant = model.variants()[job.rotation];
        if (job.completion != null) {
            if (completeStep(job, variant)) advance(job, variant);
            return;
        }
        if (!job.prepared) {
            ShipwreckModels.Chest chest = variant.chests()[job.chest];
            job.ox = job.x - chest.pos().getX();
            job.oy = job.y - chest.pos().getY();
            job.oz = job.z - chest.pos().getZ();
            job.minCx = (job.ox + variant.minX()) >> 4;
            job.maxCx = (job.ox + variant.maxX()) >> 4;
            job.minCz = (job.oz + variant.minZ()) >> 4;
            job.maxCz = (job.oz + variant.maxZ()) >> 4;
            job.prepared = true;
        }
        if (!job.started) {
            int width = job.maxCz - job.minCz + 1;
            if (job.loadedChunk < (job.maxCx - job.minCx + 1) * width) {
                if (!world.getChunkManager().isChunkLoaded(job.minCx + job.loadedChunk / width, job.minCz + job.loadedChunk % width)) {
                    job.loadedChunk = 0;
                    job.notBefore = System.nanoTime() + RETRY_NS;
                    pending.addLast(job);
                    active = null;
                    return;
                }
                job.loadedChunk++;
                return;
            }
            hypotheses++;
            job.started = true;
        }
        int[] probes = variant.probes()[job.chest];
        if (job.probe < probes.length) {
            int cell = probes[job.probe++];
            int x = job.ox + variant.x()[cell], z = job.oz + variant.z()[cell];
            if ((x >> 4) != (job.x >> 4) || (z >> 4) != (job.z >> 4)) return;
            int result = read(x, job.oy + variant.y()[cell], z, variant.families()[cell]);
            if (result == 2) {
                advance(job, variant);
                return;
            }
            if (result == 0) job.probeMatches++;
            return;
        }
        if (job.probeMatches == 0) {
            advance(job, variant);
            return;
        }
        while (job.cell < variant.x().length && hasBudget(deadline)) {
            int i = job.cell;
            int x = job.ox + variant.x()[i], z = job.oz + variant.z()[i];
            if (!world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) {
                job.cell = 0;
                java.util.Arrays.fill(job.present, 0);
                java.util.Arrays.fill(job.missing, 0);
                java.util.Arrays.fill(job.contradictory, 0);
                job.notBefore = System.nanoTime() + RETRY_NS;
                pending.addLast(job);
                active = null;
                return;
            }
            int chunk = ((x >> 4) - job.minCx) * (job.maxCz - job.minCz + 1) + (z >> 4) - job.minCz;
            int result = read(x, job.oy + variant.y()[i], z, variant.families()[i]);
            if (result == 0) job.present[chunk]++;
            else if (result == 1) job.missing[chunk]++;
            else job.contradictory[chunk]++;
            job.cell++;
        }
        if (job.cell == variant.x().length && hasBudget(deadline)) {
            job.completion = new Completion(model, job);
        }
    }

    private int read(int x, int y, int z, int family) {
        reads++;
        BlockState state = world.getBlockState(cursor.set(x, y, z));
        if (models.family(state.getBlock()) == family) return 0;
        return state.isAir() || state.isOf(Blocks.WATER)
            || (state.isReplaceable() && state.getFluidState().isIn(FluidTags.WATER)) ? 1 : 2;
    }

    private boolean completeStep(Job job, ShipwreckModels.Variant variant) {
        Completion completion = job.completion;
        int width = job.maxCz - job.minCz + 1;
        int chunkCount = (job.maxCx - job.minCx + 1) * width;
        switch (completion.phase) {
            case SCORE -> {
                int anchorTotal = job.present[completion.anchor] + job.missing[completion.anchor] + job.contradictory[completion.anchor];
                if (anchorTotal == 0 || (long) job.present[completion.anchor] * 100 <= (long) anchorTotal * 10) return true;
                if (completion.index < chunkCount) {
                    int i = completion.index++;
                    int total = job.present[i] + job.missing[i] + job.contradictory[i];
                    if ((long) job.present[i] * 100 > (long) total * 10) {
                        completion.present += job.present[i];
                        completion.eligible += total;
                    }
                    return false;
                }
                if (completion.eligible == 0 || ((long) completion.present * 100 < (long) completion.eligible * 40
                    && (long) job.present[completion.anchor] * 100 < (long) anchorTotal * 40)) return true;
                ShipwreckSite.Key key = new ShipwreckSite.Key(completion.model.name(), variant.rotation(), job.ox, job.oz);
                completion.site = candidates.computeIfAbsent(key, k -> new ShipwreckSite(k, job.oy));
                completion.next(CompletionPhase.MERGE);
            }
            case MERGE -> {
                if (completion.index < chunkCount) {
                    int i = completion.index++;
                    completion.site.merge(new ShipwreckSite.ChunkScore(job.minCx + i / width, job.minCz + i % width,
                        job.oy, job.present[i], job.missing[i], job.contradictory[i]), i == completion.anchor);
                    return false;
                }
                completion.site.setBounds(variant);
                completion.next(CompletionPhase.CHESTS);
            }
            case CHESTS -> {
                if (completion.index < variant.chests().length) {
                    ShipwreckModels.Chest chest = variant.chests()[completion.index];
                    int x = job.ox + chest.pos().getX(), z = job.oz + chest.pos().getZ();
                    if (!world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) {
                        job.notBefore = System.nanoTime() + RETRY_NS;
                        pending.addLast(job);
                        active = null;
                        return false;
                    }
                    ShipwreckSite.ChunkScore chunk = completion.site.chunk(x >> 4, z >> 4);
                    int y = (chunk == null ? job.oy : chunk.originY()) + chest.pos().getY();
                    ShipwreckSite.ChestStatus status;
                    if (chunk != null && chunk.cut()) status = ShipwreckSite.ChestStatus.CUT;
                    else {
                        reads++;
                        status = world.getBlockState(cursor.set(x, y, z)).isOf(Blocks.CHEST)
                            ? ShipwreckSite.ChestStatus.PRESENT : ShipwreckSite.ChestStatus.ABSENT;
                    }
                    completion.chests.add(new ShipwreckSite.Chest(chest.type(), new BlockPos(x, y, z), status));
                    if (status == ShipwreckSite.ChestStatus.PRESENT) completion.hasChest = true;
                    completion.index++;
                    return false;
                }
                completion.site.setChests(completion.chests);
                if (!completion.site.matchesModel() || !completion.hasChest) return true;
                completion.next(CompletionPhase.OVERLAPS);
            }
            case OVERLAPS -> {
                if (completion.index < completion.chests.size()) {
                    ShipwreckSite.Chest chest = completion.chests.get(completion.index++);
                    if (chest.status() == ShipwreckSite.ChestStatus.PRESENT) {
                        ShipwreckSite existing = chestSites.get(chest.pos().asLong());
                        if (existing != null && !completion.overlaps.contains(existing)) completion.overlaps.add(existing);
                    }
                    return false;
                }
                completion.added = completion.overlaps.isEmpty();
                if (completion.added) {
                    completion.canonical = new ShipwreckSite(completion.site.key(), completion.site.origin().getY());
                    completion.canonical.adopt(completion.site);
                    completion.canonical.markFound();
                    sites.add(completion.canonical);
                    registeredChests.put(completion.canonical, new LongOpenHashSet());
                } else completion.canonical = completion.overlaps.getFirst();
                completion.next(CompletionPhase.DUPLICATES);
                completion.index = 1;
            }
            case DUPLICATES -> {
                if (completion.aliases != null && completion.aliases.hasNext()) {
                    long key = completion.aliases.nextLong();
                    chestSites.put(key, completion.canonical);
                    registeredChests.get(completion.canonical).add(key);
                    return false;
                }
                if (completion.duplicate != null) {
                    sites.remove(completion.duplicate);
                    registeredChests.remove(completion.duplicate);
                    completion.duplicate = null;
                }
                completion.aliases = null;
                if (completion.index < completion.overlaps.size()) {
                    ShipwreckSite duplicate = completion.overlaps.get(completion.index++);
                    if (duplicate.present() > completion.canonical.present()) completion.canonical.adopt(duplicate);
                    completion.duplicate = duplicate;
                    completion.aliases = registeredChests.get(duplicate).iterator();
                    return false;
                }
                if (completion.site.present() > completion.canonical.present()) completion.canonical.adopt(completion.site);
                completion.next(CompletionPhase.REGISTER);
            }
            case REGISTER -> {
                if (completion.index < completion.chests.size()) {
                    ShipwreckSite.Chest chest = completion.chests.get(completion.index++);
                    if (chest.status() == ShipwreckSite.ChestStatus.PRESENT) {
                        long key = chest.pos().asLong();
                        chestSites.put(key, completion.canonical);
                        registeredChests.get(completion.canonical).add(key);
                    }
                    return false;
                }
                if (!completion.added) return true;
                completion.next(CompletionPhase.VISITED);
            }
            case VISITED -> {
                if (completion.index == completion.canonical.chests().size()) return true;
                ShipwreckSite.Chest chest = completion.canonical.chests().get(completion.index++);
                if (chest.status() == ShipwreckSite.ChestStatus.PRESENT && !visited.test(chest.pos())) {
                    completion.next(CompletionPhase.LOG);
                }
            }
            case LOG -> {
                ShipwreckSite snapshot = new ShipwreckSite(completion.canonical.key(), completion.canonical.origin().getY());
                snapshot.adopt(completion.canonical);
                CompletableFuture.runAsync(() -> HunterBuddyAddon.LOG.info("[SpawnerDetector/shipwreck] found {}", snapshot.describe()));
                return true;
            }
        }
        return false;
    }

    private void advance(Job job, ShipwreckModels.Variant variant) {
        job.completion = null;
        job.prepared = false;
        job.loadedChunk = 0;
        job.started = false;
        job.probe = job.probeMatches = job.cell = 0;
        java.util.Arrays.fill(job.present, 0);
        java.util.Arrays.fill(job.missing, 0);
        java.util.Arrays.fill(job.contradictory, 0);
        if (++job.chest == variant.chests().length) {
            job.chest = 0;
            if (++job.rotation == 4) {
                job.rotation = 0;
                job.model++;
            }
        }
    }

    private static final class Bucket {
        final long key;
        final int x, z;
        final LongArrayList positions = new LongArrayList();
        int removed;
        Bucket(long key, int x, int z) { this.key = key; this.x = x; this.z = z; }
    }

    private static final class Job {
        final long key;
        final int x, y, z;
        final int[] present = new int[16], missing = new int[16], contradictory = new int[16];
        int model, rotation, chest, probe, probeMatches, cell;
        int ox, oy, oz, minCx, maxCx, minCz, maxCz;
        boolean started, prepared;
        int loadedChunk;
        Completion completion;
        long notBefore;
        Job(long key, int x, int y, int z) { this.key = key; this.x = x; this.y = y; this.z = z; }
    }

    private record CollectedChest(ClientWorld world, BlockPos pos) {}

    private enum CompletionPhase { SCORE, MERGE, CHESTS, OVERLAPS, DUPLICATES, REGISTER, VISITED, LOG }

    private static final class Completion {
        final ShipwreckModels.Model model;
        final int anchor;
        final List<ShipwreckSite.Chest> chests = new ArrayList<>();
        final List<ShipwreckSite> overlaps = new ArrayList<>();
        CompletionPhase phase = CompletionPhase.SCORE;
        int index, present, eligible;
        boolean hasChest, added;
        ShipwreckSite site, canonical, duplicate;
        LongIterator aliases;

        Completion(ShipwreckModels.Model model, Job job) {
            this.model = model;
            anchor = ((job.x >> 4) - job.minCx) * (job.maxCz - job.minCz + 1) + (job.z >> 4) - job.minCz;
        }

        void next(CompletionPhase next) {
            phase = next;
            index = 0;
        }
    }
}
