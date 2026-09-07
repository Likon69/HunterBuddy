package com.hunterbuddy.modules;

import com.google.gson.Gson;
import com.hunterbuddy.HunterBuddyAddon;
import static com.hunterbuddy.modules.regear.util.Utils.yawToDirection;
import static com.hunterbuddy.modules.regear.util.Utils.distancePointToDirection;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.registry.RegistryKey;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.map.mods.SupportMods;
import xaero.common.minimap.waypoints.Waypoint;
import xaeroplus.feature.waypoint.WaypointAPI;
import xaeroplus.XaeroPlus;
import xaeroplus.event.ChunkDataEvent;
import xaeroplus.module.ModuleManager;
import xaeroplus.module.impl.OldChunks;
import xaeroplus.module.impl.PaletteNewChunks;
import xaeroplus.settings.Settings;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;




public class OldChunkNotifier extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum DimensionMode {
        Overworld,
        Nether,
        End,
        All



    }

    public enum ChunkTypeMode {
        ONLY_112("1.12 Only"),
        ONLY_119("1.19+ Only"),
        BOTH("Both");

        private final String displayName;

        ChunkTypeMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private final Setting<Boolean> notifyAnyChunks = sgGeneral.add(new BoolSetting.Builder()
        .name("notify-any-chunks")
        .description("Whether to notify you of any old chunks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> notifyOffHighway = sgGeneral.add(new BoolSetting.Builder()
        .name("notify-trails-off-highway")
        .description("Whether to notify you of old chunks off the highway.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> directionOfTravel = sgGeneral.add(new DoubleSetting.Builder()
        .name("direction-of-travel")
        .description("The direction of travel (yaw) in degrees.")
        .defaultValue(0)
        .min(-180)
        .max(180)
        .visible(notifyOffHighway::get)
        .build()
    );

    private final Setting<Double> distanceOffAxis = sgGeneral.add(new DoubleSetting.Builder()
        .name("distance-off-axis")
        .description("The distance in chunks off the axis of movement from the player to check for old chunks.")
        .defaultValue(13)
        .sliderRange(0, 15)
        .visible(notifyOffHighway::get)
        .build()
    );

    private final Setting<ChunkTypeMode> chunkTypeMode = sgGeneral.add(new EnumSetting.Builder<ChunkTypeMode>()
        .name("chunk-type")
        .description("Which type of old chunks to detect.")
        .defaultValue(ChunkTypeMode.BOTH)
        .build()
    );

    private final Setting<Integer> minClusterSize = sgGeneral.add(new IntSetting.Builder()
        .name("min-cluster-size")
        .description("Nombre minimum de chunks adjacents avant de notifier (filtre les faux positifs isolés).")
        .defaultValue(2)
        .min(1)
        .sliderMax(6)
        .build()
    );

    private final Setting<LogType> logType = sgGeneral.add(new EnumSetting.Builder<LogType>()
        .name("log-type")
        .description("What to do when an old chunk is detected.")
        .defaultValue(LogType.Marker)
        .build()
    );

    private final Setting<Boolean> temporaryWaypoints = sgGeneral.add(new BoolSetting.Builder()
        .name("temporary-waypoints")
        .description("Waypoints are removed when you disconnect.")
        .defaultValue(true)
        .visible(() -> logType.get() == LogType.Marker || logType.get() == LogType.Both)
        .build()
    );

    private final Setting<Boolean> markersFollowXaeroDimension = sgGeneral.add(new BoolSetting.Builder()
        .name("markers-follow-xaero-dimension")
        .description("When XaeroPlus's own \"Prefer Overworld Waypoints\" is on, put a Nether chunk's marker in the Overworld waypoint world at ×8 coordinates instead of the Nether one — the same trick XaeroPlus uses for its own spawn point (and Hunt_ waypoints already use), so it shows on the map without switching dimension. Off, markers go where they do today.")
        .defaultValue(true)
        .visible(() -> logType.get() == LogType.Marker || logType.get() == LogType.Both)
        .build()
    );

    private final Setting<String> webhookLink = sgGeneral.add(new StringSetting.Builder()
        .name("webhook-link")
        .description("A discord webhook link. Looks like this: https://discord.com/api/webhooks/webhookUserId/webHookTokenOrSomething")
        .defaultValue("")
        .visible(() -> logType.get() == LogType.Webhook || logType.get() == LogType.Both)
        .build()
    );

    private final Setting<Boolean> ping = sgGeneral.add(new BoolSetting.Builder()
        .name("ping")
        .description("Whether to ping you or not.")
        .defaultValue(false)
        .visible(() -> logType.get() == LogType.Webhook || logType.get() == LogType.Both)
        .build()
    );

    private final Setting<String> discordId = sgGeneral.add(new StringSetting.Builder()
        .name("discord-ID")
        .description("Your discord ID")
        .defaultValue("")
        .visible(() -> ping.get() && (logType.get() == LogType.Webhook || logType.get() == LogType.Both))
        .build()
    );

    public final Setting<DimensionMode> dimensionMode = sgGeneral.add(new EnumSetting.Builder<DimensionMode>()
        .name("dimension-mode")
        .description("Choose where the module will detect old chunks.")
        .defaultValue(DimensionMode.All)
        .build()
    );

    private final Setting<Boolean> netherRoads = sgGeneral.add(new BoolSetting.Builder()
        .name("nether-roads")
        .description("In the Nether, classify chunks with a marker/biome scan of our own instead of XaeroPlus's overworld-shaped old/new verdict, and look for a one-chunk-wide line of them instead of a 4-connected cluster. XaeroPlus's own nether rule calls a chunk new on a single block, which a neighbouring ore vein spilling across the border does constantly — a real trail reads as a dotted line with holes instead of one. Off, or outside the Nether, nothing here runs.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> netherMarkerMargin = sgGeneral.add(new IntSetting.Builder()
        .name("nether-marker-margin")
        .description("Border band ignored when counting 1.16+ marker blocks, in blocks. A vein from a neighbouring new chunk spills a few blocks past its own edge; the margin keeps that spill from reading as proof this chunk is new.")
        .defaultValue(4)
        .min(0)
        .max(7)
        .sliderRange(0, 7)
        .visible(netherRoads::get)
        .build()
    );

    private final Setting<Integer> netherMarkerMin = sgGeneral.add(new IntSetting.Builder()
        .name("nether-marker-min")
        .description("Marker blocks inside the margin (the chunk's core) at or above which the chunk counts as new.")
        .defaultValue(3)
        .min(1)
        .max(64)
        .sliderRange(1, 20)
        .visible(netherRoads::get)
        .build()
    );

    private final Setting<Integer> roadMinLength = sgGeneral.add(new IntSetting.Builder()
        .name("road-min-length")
        .description("Aligned candidate chunks needed before a Nether road is announced.")
        .defaultValue(4)
        .min(2)
        .max(32)
        .sliderRange(2, 16)
        .visible(netherRoads::get)
        .build()
    );

    private final Setting<Integer> roadMaxGap = sgGeneral.add(new IntSetting.Builder()
        .name("road-max-gap")
        .description("Consecutive gaps tolerated in the line — the chunks the classifier still gets wrong.")
        .defaultValue(1)
        .min(0)
        .max(3)
        .visible(netherRoads::get)
        .build()
    );

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug")
        .description("Écrit dans le chat le détail de la détection, des clusters et des envois webhook.")
        .defaultValue(false)
        .build()
    );

    // Cluster detection and Discord delivery state
    private static final int MAX_PROCESSED_CHUNKS = 1000;
    private static final int MAX_TRACKED_CHUNKS = 10000;
    private static final int MAX_WEBHOOK_ATTEMPTS = 3;
    private static final int WEBHOOK_RETRY_DELAY_TICKS = 20 * 30;
    private static final int WEBHOOK_EDIT_DELAY_TICKS = 20 * 10;
    private static final int DEBUG_CENSUS_TICKS = 20 * 10;
    private static final int[][] NEIGHBOR_OFFSETS = {{1,0},{-1,0},{0,1},{0,-1}};
    private static final Gson GSON = new Gson();

    // nether-roads (spec §3.5) — all four marked [proposé], meant to be recalibrated against the
    // user's promised example via the per-chunk/per-road debug lines rather than exposed as settings.
    private static final int ROAD_SEARCH_RADIUS_CHUNKS = 8;
    private static final double ROAD_ELONGATION_MAX = 0.35;
    private static final double ROAD_SIDE_OTHER_MIN_RATIO = 0.6;
    private static final double ROAD_OWN_ROUTE_ANGLE_DEG = 10.0;

    private final LinkedHashMap<ChunkKey, Boolean> processedChunks = new LinkedHashMap<>();
    private final LinkedHashMap<ChunkKey, ChunkState> trackedChunks = new LinkedHashMap<>();

    /**
     * nether-roads: every Nether chunk classified so far, candidate or not — bounded the same as
     * {@link #trackedChunks}. {@link #trackedChunks} only ever holds chunks worth announcing;
     * this holds everything, because the "one chunk wide" test in {@link #tryNotifyRoad} needs to
     * know the class of a candidate's own perpendicular neighbours, most of which are ordinary
     * fresh Nether and were never candidates themselves.
     */
    private final LinkedHashMap<ChunkKey, NetherClassification> netherSeen = new LinkedHashMap<>();
    private final Map<Long, Delivery> webhookDeliveries = new HashMap<>();
    private final Map<Long, Integer> webhookDeliveryReferences = new HashMap<>();
    private final HttpClient discordHttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private ExecutorService discordExecutor;
    private long nextWebhookDeliveryId = 1;
    private int webhookRetryCooldown;
    private int debugCensusCooldown;

    /** Worlds that gained a permanent waypoint and still need writing to disk. */
    private final Set<MinimapWorld> pendingWaypointSaves = Collections.newSetFromMap(new IdentityHashMap<>());
    private long lastWaypointSaveMs;
    private static final long WAYPOINT_SAVE_INTERVAL_MS = 4000L;

    public OldChunkNotifier() {
        super(HunterBuddyAddon.HUNT_CATEGORY, "old-chunk-notifier", "Sends a webhook message and optionally pings you when an old chunk is detected.");
    }

    @Override
    public void onActivate()
    {
        processedChunks.clear();
        trackedChunks.clear();
        webhookDeliveries.clear();
        webhookDeliveryReferences.clear();
        webhookRetryCooldown = 0;
        debugCensusCooldown = 0;
        if (discordExecutor == null || discordExecutor.isShutdown()) {
            discordExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "OldChunkNotifier-Discord");
                thread.setDaemon(true);
                return thread;
            });
        }
        XaeroPlus.EVENT_BUS.register(this);
    }

    @Override
    public void onDeactivate()
    {
        flushWaypointSaves(true);
        XaeroPlus.EVENT_BUS.unregister(this);
        if (discordExecutor != null) {
            discordExecutor.shutdownNow();
            discordExecutor = null;
        }
    }

    @net.lenni0451.lambdaevents.EventHandler(priority = -1)
    public void onChunkData(ChunkDataEvent event)
    {
        if (event.seenChunk() || mc.player == null || mc.world == null) return;

        // avoid 2b2t end loading screen
        if (mc.player.getAbilities().allowFlying) return;

        RegistryKey<World> dimension = event.chunk().getWorld().getRegistryKey();
        if ((dimensionMode.get() == DimensionMode.Nether && !dimension.equals(World.NETHER)) ||
            (dimensionMode.get() == DimensionMode.End && !dimension.equals(World.END)) ||
            (dimensionMode.get() == DimensionMode.Overworld && !dimension.equals(World.OVERWORLD))) return;

        ChunkPos chunkPos = event.chunk().getPos();
        ChunkKey key = new ChunkKey(dimension, chunkPos.x, chunkPos.z);
        if (!markProcessed(key)) return;

        boolean is119NewChunk = ModuleManager.getModule(PaletteNewChunks.class)
            .isNewChunk(key.x, key.z, key.dimension);

        boolean is112OldChunk = ModuleManager.getModule(OldChunks.class)
            .isOldChunk(key.x, key.z, key.dimension);

        boolean shouldNotify = switch (chunkTypeMode.get()) {
            case ONLY_112 -> is112OldChunk;
            case ONLY_119 -> !is119NewChunk && !is112OldChunk;
            case BOTH -> !is119NewChunk || is112OldChunk;
        };

        // Computed ahead of the nether-roads branch below purely so its own debug line can report
        // what this, the normal path, made of the same chunk — the two are independent verdicts on
        // purpose (spec §3.2.4) and neither gates the other any more (see the branch below).
        DetectedChunkType detectedType = null;
        if (shouldNotify) {
            if (is112OldChunk && !is119NewChunk) {
                detectedType = DetectedChunkType.Old112Followed;
            } else if (is112OldChunk) {
                detectedType = DetectedChunkType.Old112Unfollowed;
            } else {
                detectedType = DetectedChunkType.Old119;
            }
        }

        // nether-roads is an addition, not a replacement: it used to `return` here, which shut off
        // every ordinary old-chunk verdict (XaeroPlus, chunk-type, the 4-neighbour cluster, markers,
        // webhook) for the whole Nether the moment the box was checked -- a base, or any non-linear
        // old structure, went completely unnotified. Both paths run now; each guards its own
        // confirm/announce state and neither one skips the other (spec §3, "les deux doivent tourner").
        if (netherRoads.get() && dimension.equals(World.NETHER)) {
            handleNetherRoadChunk(key, event.chunk(), detectedType);
        }

        if (!shouldNotify) return;

        boolean offHighway = false;
        if (notifyOffHighway.get()) {
            Vec3d direction = yawToDirection(directionOfTravel.get());
            ChunkPos playerChunkPos = mc.player.getChunkPos();
            double distance = distancePointToDirection(
                new Vec3d(chunkPos.x, 0, chunkPos.z),
                direction,
                new Vec3d(playerChunkPos.x, 0, playerChunkPos.z)
            );
            offHighway = distance > distanceOffAxis.get();
        }

        if (!notifyAnyChunks.get() && !offHighway) return;

        ChunkState state = trackChunk(key, detectedType, offHighway);
        dbg("detect " + key.x + "," + key.z + " type=" + detectedType.label
            + (offHighway ? " off-highway" : "") + (state.confirmed ? " (deja confirme)" : ""));
        if (!state.confirmed) tryNotifyCluster(key);
    }

    private void dbg(String message) {
        if (debug.get()) info(message, new Object[0]);
    }

    private boolean markProcessed(ChunkKey key) {
        if (processedChunks.containsKey(key)) return false;
        processedChunks.put(key, Boolean.TRUE);

        while (processedChunks.size() > MAX_PROCESSED_CHUNKS) {
            Iterator<ChunkKey> iterator = processedChunks.keySet().iterator();
            iterator.next();
            iterator.remove();
        }

        return true;
    }

    /**
     * Whether this chunk sits in a confirmed old-chunk cluster.
     *
     * <p>For the heat-map HUD, which asks about the grid around the player once a second. Only
     * confirmed states count: the pending ones are exactly the noise the cluster threshold
     * exists to hold back.
     */
    public boolean isConfirmedOld(RegistryKey<World> dimension, int chunkX, int chunkZ) {
        ChunkState state = trackedChunks.get(new ChunkKey(dimension, chunkX, chunkZ));
        return state != null && state.confirmed;
    }

    private ChunkState trackChunk(ChunkKey key, DetectedChunkType type, boolean offHighway) {
        ChunkState state = trackedChunks.get(key);
        if (state == null) {
            state = new ChunkState(type, offHighway);
            trackedChunks.put(key, state);
            trimTrackedChunks();
        } else {
            state.type = type;
            state.offHighway |= offHighway;
        }
        return state;
    }

    private void trimTrackedChunks() {
        while (trackedChunks.size() > MAX_TRACKED_CHUNKS) {
            Iterator<Map.Entry<ChunkKey, ChunkState>> iterator = trackedChunks.entrySet().iterator();
            Map.Entry<ChunkKey, ChunkState> removed = iterator.next();
            long deliveryId = removed.getValue().webhookDeliveryId;
            iterator.remove();
            releaseDeliveryReference(deliveryId);
        }
    }

    private void trimNetherSeen() {
        while (netherSeen.size() > MAX_TRACKED_CHUNKS) {
            Iterator<ChunkKey> iterator = netherSeen.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    /**
     * 1.16+ only, none of them ever added retroactively to a chunk that already existed before
     * the Nether update — see the class doc on {@link NetherAge}.
     */
    private static final Set<Block> NETHER_MARKERS = Set.of(
        Blocks.NETHER_GOLD_ORE, Blocks.ANCIENT_DEBRIS, Blocks.BLACKSTONE, Blocks.BASALT,
        Blocks.CRIMSON_NYLIUM, Blocks.WARPED_NYLIUM, Blocks.SOUL_SOIL, Blocks.IRON_CHAIN
    );

    /**
     * Whether the chunk actually arrived far enough to classify. The Nether's whole height is
     * y 0-127, so — unlike the overworld rule this mirrors, which only needs the one section at
     * y 0 — every section here can carry a marker, and an incomplete delivery answers every test
     * the way an old chunk does for no reason at all.
     */
    static boolean netherReadable(WorldChunk chunk) {
        for (ChunkSection section : chunk.getSectionArray()) {
            if (section != null && !section.isEmpty()) return true;
        }
        return false;
    }

    /**
     * The pre-1.16 half of {@link #classifyNether} on its own, for other modules: the biome and
     * marker rule with the border margin, no freshness involved. TrailFollower uses it so a
     * pre-1.16 trail chunk stays a trail chunk when XaeroPlus's one-block nether rule, or a
     * palette false positive, would have dropped it.
     */
    static boolean isPreSixteenNetherChunk(WorldChunk chunk, int margin, int minCore) {
        if (!netherReadable(chunk)) return false;
        if (!biomeUniform(chunk)) return false;
        return scanNetherMarkers(chunk, margin)[1] < minCore;
    }

    /**
     * True only if every populated section's biome container is entirely {@code nether_wastes} —
     * the one biome the pre-1.16 Nether had. A single block of any other biome anywhere in the
     * chunk is proof the chunk postdates the update, whatever the marker count says; this is the
     * cheapest and hardest test in {@link #classifyNether}, and is checked first for that reason.
     *
     * <p>The least certain claim in this whole feature — see project memory on the subject before
     * trusting it past a first look at real chunks.
     */
    static boolean biomeUniform(WorldChunk chunk) {
        for (ChunkSection section : chunk.getSectionArray()) {
            if (section == null) continue;
            if (section.getBiomeContainer().hasAny(entry -> !entry.matchesKey(BiomeKeys.NETHER_WASTES))) return false;
        }
        return true;
    }

    /**
     * Marker counts for one chunk: {@code total} across the whole chunk (cheap — palette counts
     * only, no per-block walk), {@code core} restricted to the {@code margin}-block border band
     * on every side (the one positional pass, and only for a section whose palette already has a
     * marker in it at all — most don't). A vein spilling from a neighbouring new chunk lands in
     * the border band almost every time; the core is what survives that.
     */
    static int[] scanNetherMarkers(WorldChunk chunk, int margin) {
        int total = 0;
        int core = 0;

        for (ChunkSection section : chunk.getSectionArray()) {
            if (section == null || section.isEmpty()) continue;

            int[] sectionTotal = {0};
            section.getBlockStateContainer().count((state, count) -> {
                if (NETHER_MARKERS.contains(state.getBlock())) sectionTotal[0] += count;
            });
            if (sectionTotal[0] == 0) continue;
            total += sectionTotal[0];

            for (int y = 0; y < 16; y++) {
                for (int x = margin; x < 16 - margin; x++) {
                    for (int z = margin; z < 16 - margin; z++) {
                        if (NETHER_MARKERS.contains(section.getBlockState(x, y, z).getBlock())) core++;
                    }
                }
            }
        }

        return new int[]{total, core};
    }

    /**
     * The Nether-specific age verdict, independent of XaeroPlus entirely. {@code age} is
     * {@code NEW} the instant any biome other than {@code nether_wastes} shows up, or once the
     * border-margined core count reaches {@link #netherMarkerMin}; otherwise {@code OLD}. Fresh-
     * ness ({@link NetherClassification#fresh}) is read separately, at the call site, from
     * XaeroPlus's own {@code PaletteNewChunks} — the two signals are independent on purpose (a
     * pre-1.16 chunk seen by someone else yesterday is still {@code OLD} and not fresh; a chunk
     * generated for us this instant is fresh regardless of what {@code age} says).
     */
    private NetherClassification classifyNether(WorldChunk chunk, boolean fresh) {
        if (!netherReadable(chunk)) return new NetherClassification(NetherAge.UNREADABLE, 0, 0, true, fresh);

        boolean uniform = biomeUniform(chunk);
        int[] counts = scanNetherMarkers(chunk, netherMarkerMargin.get());
        int total = counts[0];
        int core = counts[1];

        NetherAge age;
        if (!uniform) age = NetherAge.NEW;
        else if (core >= netherMarkerMin.get()) age = NetherAge.NEW;
        else age = NetherAge.OLD;

        return new NetherClassification(age, core, total, uniform, fresh);
    }

    /**
     * Runs the block/biome classifier and the line detector for a Nether chunk when
     * {@link #netherRoads} is on — <em>alongside</em> the XaeroPlus-cache path in
     * {@link #onChunkData}, not instead of it (both used to be mutually exclusive; that was a spec
     * mistake, not an implementation one — see the call site). Classifies with
     * {@link #classifyNether}, remembers the verdict in {@link #netherSeen} (every chunk, not just
     * candidates — the line detector needs the class of a candidate's neighbours, most of which
     * never are one), and hands off to {@link #tryNotifyRoad} the same way the normal path hands an
     * old chunk to {@code tryNotifyCluster}: only once, only while unconfirmed.
     *
     * @param normalType what the ordinary XaeroPlus-cache path decided for this same chunk this
     *                    same tick, or {@code null} if it did not consider it notify-worthy at all —
     *                    reported for debug only (spec §3.2.4); never influences the verdict here.
     */
    private void handleNetherRoadChunk(ChunkKey key, WorldChunk chunk, DetectedChunkType normalType) {
        boolean fresh = ModuleManager.getModule(PaletteNewChunks.class).isNewChunk(key.x, key.z, key.dimension);
        NetherClassification classification = classifyNether(chunk, fresh);
        netherSeen.put(key, classification);
        trimNetherSeen();

        dbg(String.format(Locale.ROOT, "nether %d,%d age=%s core=%d total=%d biomeUniform=%b fresh=%b candidate=%b normal=%s",
            key.x, key.z, classification.age, classification.core, classification.total,
            classification.biomeUniform, classification.fresh, classification.isCandidate(),
            normalType != null ? normalType.label : "none"));

        if (classification.age == NetherAge.UNREADABLE || !classification.isCandidate()) return;

        DetectedChunkType type = classification.age == NetherAge.OLD
            ? DetectedChunkType.NetherRoadOld
            : DetectedChunkType.NetherRoadSeen;
        ChunkState state = trackChunk(key, type, false);
        if (!state.confirmed) tryNotifyRoad(key);
    }

    /** Every candidate (§3.4) known so far within Chebyshev distance {@link #ROAD_SEARCH_RADIUS_CHUNKS} of {@code c}. */
    private List<ChunkKey> nearbyNetherCandidates(ChunkKey c) {
        List<ChunkKey> result = new ArrayList<>();
        for (Map.Entry<ChunkKey, NetherClassification> entry : netherSeen.entrySet()) {
            if (!entry.getValue().isCandidate()) continue;
            ChunkKey key = entry.getKey();
            if (Math.max(Math.abs(key.x - c.x), Math.abs(key.z - c.z)) <= ROAD_SEARCH_RADIUS_CHUNKS) result.add(key);
        }
        return result;
    }

    /**
     * Option A of spec §3.5: fits a line (PCA) through the candidates near {@code c}, requires it
     * elongated and covered enough, requires it one chunk wide, and only then announces — once per
     * road, same "confirmed" gate the 4-connected cluster path uses.
     */
    private void tryNotifyRoad(ChunkKey c) {
        List<ChunkKey> nearby = nearbyNetherCandidates(c);
        if (nearby.size() < roadMinLength.get()) return;

        int n = nearby.size();
        double mx = 0, mz = 0;
        for (ChunkKey p : nearby) { mx += p.x; mz += p.z; }
        mx /= n; mz /= n;

        double sxx = 0, szz = 0, sxz = 0;
        for (ChunkKey p : nearby) {
            double dx = p.x - mx, dz = p.z - mz;
            sxx += dx * dx;
            szz += dz * dz;
            sxz += dx * dz;
        }
        sxx /= n; szz /= n; sxz /= n;

        double theta = 0.5 * Math.atan2(2 * sxz, sxx - szz);
        double trace = sxx + szz;
        double det = sxx * szz - sxz * sxz;
        double disc = Math.sqrt(Math.max(0, (trace * trace) / 4 - det));
        double lambda2 = trace / 2 - disc;

        if (lambda2 > ROAD_ELONGATION_MAX) {
            dbg(String.format(Locale.ROOT, "  road candidat %d,%d: rejete, lambda2=%.3f > %.3f (pas assez aligne)",
                c.x, c.z, lambda2, ROAD_ELONGATION_MAX));
            return;
        }

        // Bucket by the integer coordinate on the major axis (x or z, whichever the line leans
        // closer to) rather than the rounded projection onto theta: a 45° road advances 1.41 per
        // chunk in projection, so rounding it opens a fake hole every other slot and eats the real
        // road-max-gap tolerance on geometry instead of on the chunks the classifier actually gets
        // wrong (Fable QC). Along its own major axis a chunk trail advances by exactly 1 per step
        // at any heading, so this key needs no rounding.
        double cosT = Math.cos(theta), sinT = Math.sin(theta);
        boolean xMajor = Math.abs(cosT) >= Math.abs(sinT);
        TreeMap<Integer, List<ChunkKey>> slots = new TreeMap<>();
        for (ChunkKey p : nearby) {
            int slot = xMajor ? p.x : p.z;
            slots.computeIfAbsent(slot, k -> new ArrayList<>()).add(p);
        }

        List<Integer> slotKeys = new ArrayList<>(slots.keySet());
        int bestFrom = 0, bestTo = -1, bestCount = 0;
        int from = 0, count = slots.get(slotKeys.get(0)).size();
        for (int i = 1; i < slotKeys.size(); i++) {
            int gap = slotKeys.get(i) - slotKeys.get(i - 1) - 1;
            if (gap > roadMaxGap.get()) {
                if (count > bestCount) { bestCount = count; bestFrom = from; bestTo = i - 1; }
                from = i;
                count = 0;
            }
            count += slots.get(slotKeys.get(i)).size();
        }
        if (count > bestCount) { bestCount = count; bestFrom = from; bestTo = slotKeys.size() - 1; }

        if (bestTo < bestFrom || bestCount < roadMinLength.get()) {
            dbg(String.format(Locale.ROOT, "  road candidat %d,%d: rejete, plus longue suite=%d < %d",
                c.x, c.z, bestCount, roadMinLength.get()));
            return;
        }

        Set<ChunkKey> runSet = new HashSet<>();
        for (int i = bestFrom; i <= bestTo; i++) runSet.addAll(slots.get(slotKeys.get(i)));

        // The chunk that triggered this call (c) is not necessarily part of the run the fit
        // settled on — pick a stable member of the run itself for the coordinates/age the
        // announcement reports (Fable QC).
        ChunkKey roadAnchor = null;
        for (ChunkKey key : runSet) {
            if (roadAnchor == null || key.x < roadAnchor.x || (key.x == roadAnchor.x && key.z < roadAnchor.z)) roadAnchor = key;
        }

        // "One chunk wide": the run's perpendicular neighbours must mostly belong to some other
        // class (fresh, or new-and-not-a-candidate) — a wide old zone fails this, its neighbours
        // are the same class as itself, and stays available to the existing cluster mode instead.
        int px = (int) Math.round(-sinT), pz = (int) Math.round(cosT);
        int seenNeighbors = 0, otherClassNeighbors = 0;
        for (ChunkKey p : runSet) {
            for (int sign = -1; sign <= 1; sign += 2) {
                ChunkKey neighbor = p.offset(px * sign, pz * sign);
                NetherClassification neighborClass = netherSeen.get(neighbor);
                if (neighborClass == null || neighborClass.age == NetherAge.UNREADABLE) continue;
                seenNeighbors++;
                if (!neighborClass.isCandidate()) otherClassNeighbors++;
            }
        }
        double sideOtherRatio = seenNeighbors == 0 ? 1.0 : (double) otherClassNeighbors / seenNeighbors;
        if (seenNeighbors > 0 && sideOtherRatio < ROAD_SIDE_OTHER_MIN_RATIO) {
            dbg(String.format(Locale.ROOT, "  road candidat %d,%d: rejete, sideOther=%.0f%% sur %d voisin(s) vu(s) (zone, pas une piste)",
                c.x, c.z, sideOtherRatio * 100, seenNeighbors));
            return;
        }
        // seenNeighbors == 0 lets the run through unchecked (no data yet either side) — rare in
        // flight, but real; sideOtherN below is 0 exactly in that case (Fable QC, flagged not fixed).

        double thetaDeg = Math.toDegrees(theta);
        boolean ownRoute = false;
        if (notifyOffHighway.get() && mc.player != null) {
            Vec3d travelDir = yawToDirection(directionOfTravel.get());
            double travelAngleDeg = Math.toDegrees(Math.atan2(travelDir.z, travelDir.x));
            if (angleDiffDegMod180(thetaDeg, travelAngleDeg) <= ROAD_OWN_ROUTE_ANGLE_DEG) {
                ChunkPos playerChunkPos = mc.player.getChunkPos();
                double distanceFromPlayer = distancePointToDirection(
                    new Vec3d(playerChunkPos.x, 0, playerChunkPos.z),
                    new Vec3d(cosT, 0, sinT),
                    new Vec3d(mx, 0, mz)
                );
                ownRoute = distanceFromPlayer <= distanceOffAxis.get();
            }
        }

        boolean joinsConfirmedRoad = false;
        List<ChunkKey> freshlyConfirmed = new ArrayList<>();
        for (ChunkKey key : runSet) {
            ChunkState state = trackedChunks.get(key);
            if (state == null) continue;
            if (state.confirmed) joinsConfirmedRoad = true;
            else freshlyConfirmed.add(key);
        }

        dbg(String.format(Locale.ROOT, "road n=%d theta=%.1f lambda2=%.3f sideOther=%.0f%% sideOtherN=%d ownRoute=%b extension=%b",
            runSet.size(), thetaDeg, lambda2, sideOtherRatio * 100, seenNeighbors, ownRoute, joinsConfirmedRoad));

        for (ChunkKey key : freshlyConfirmed) {
            trackedChunks.get(key).confirmed = true;
        }

        // Own route: mark confirmed so it stops being re-tested every arrival, but announce
        // nothing — this is the highway we are ourselves flying, not a find (spec §3.5 step 5).
        if (ownRoute) return;

        boolean wantsMarkers = logType.get() == LogType.Marker || logType.get() == LogType.Both;
        if (wantsMarkers) {
            for (ChunkKey key : freshlyConfirmed) createMapMarker(key);
        }

        // Extension of a road already announced: new markers only, no repeat webhook/feed post —
        // mirrors joinsConfirmedCluster (:598-).
        if (joinsConfirmedRoad) return;

        // Not trackedChunks.get(roadAnchor).type: now that the normal XaeroPlus-cache path also runs
        // on every Nether chunk (see onChunkData), it calls trackChunk on the same key whenever it
        // finds its own verdict notify-worthy, and that overwrites the shared ChunkState.type this
        // road detector wrote — every road would report "generated before us" (Fable QC, spec §3.2.3).
        // netherSeen's own classification is never touched by that other path.
        NetherClassification anchorClassification = netherSeen.get(roadAnchor);
        boolean preSixteen = anchorClassification != null && anchorClassification.age == NetherAge.OLD;
        String ageLabel = preSixteen ? "pre-1.16" : "generated before us";
        double heading = normalizeHeading(thetaDeg);

        com.hunterbuddy.util.HuntFeed.get().publish(
            com.hunterbuddy.util.HuntFeed.Type.OLD_CHUNK,
            String.format(Locale.ROOT, "Nether road · %d chunks · cap %.0f° · %s · %d %d (nether) ~ %d %d (overworld)",
                runSet.size(), heading, ageLabel, roadAnchor.x, roadAnchor.z, roadAnchor.x * 8, roadAnchor.z * 8),
            new net.minecraft.util.math.BlockPos(roadAnchor.x << 4, 0, roadAnchor.z << 4));

        if (logType.get() == LogType.Webhook || logType.get() == LogType.Both) {
            sendRoadWebhook(roadAnchor, runSet, heading, ageLabel);
        }
    }

    private static double angleDiffDegMod180(double a, double b) {
        double diff = Math.abs(a - b) % 180.0;
        return diff > 90.0 ? 180.0 - diff : diff;
    }

    private static double normalizeHeading(double deg) {
        double normalized = deg % 180.0;
        if (normalized < 0) normalized += 180.0;
        return normalized;
    }

    /**
     * One-shot POST for a newly confirmed road — deliberately not wired into {@link #webhookDeliveries}
     * / {@link #processDeliveryEdits}: those grow a reported count by re-walking a 4-connected
     * cluster ({@link #collectCluster}), which a diagonal or gapped road is not. A road that keeps
     * growing gets new markers (see {@link #tryNotifyRoad}) but does not edit its Discord message.
     */
    private void sendRoadWebhook(ChunkKey anchor, Set<ChunkKey> runSet, double heading, String ageLabel) {
        String rawUrl = webhookLink.get().trim();
        if (rawUrl.isEmpty()) {
            error("Discord webhook URL is empty.", new Object[0]);
            return;
        }

        URI webhookUri = parseDiscordWebhookUrl(rawUrl);
        if (webhookUri == null) {
            error("Invalid Discord webhook URL. Use a channel webhook URL, not a server invite.", new Object[0]);
            return;
        }

        String pingId = null;
        if (ping.get() && !discordId.get().isBlank()) {
            pingId = discordId.get().trim();
            if (!pingId.matches("\\d+")) {
                error("Invalid Discord user ID. It must contain digits only.", new Object[0]);
                return;
            }
        }

        ExecutorService executor = discordExecutor;
        if (executor == null || executor.isShutdown()) {
            error("Discord webhook executor is not available.", new Object[0]);
            return;
        }

        String playerName = mc.player != null ? mc.player.getGameProfile().name() : "Unknown";
        String json = buildRoadWebhookJson(anchor, runSet, heading, ageLabel, playerName, pingId);
        URI createUri = URI.create(webhookUri.toString() + "?wait=true");

        dbg("  POST piste nether " + anchor.x + "," + anchor.z + " avec " + runSet.size() + " chunk(s)");

        try {
            executor.submit(() -> {
                WebhookResult result = sendWebhookRequest("POST", createUri, json);
                mc.execute(() -> {
                    if (!result.success) error("Discord webhook failed: " + result.error, new Object[0]);
                });
            });
        } catch (RejectedExecutionException e) {
            error("Discord webhook could not be queued.", new Object[0]);
        }
    }

    private String buildRoadWebhookJson(ChunkKey anchor, Set<ChunkKey> runSet, double heading, String ageLabel, String playerName, String pingId) {
        Map<String, Object> footer = Map.of("text", "From: " + playerName);
        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("title", "Nether Road Detected");
        embed.put("description", String.format(Locale.ROOT,
            "**Chunks:** %d\n**Heading:** %.0f°\n**Age:** %s\n**Chunk coordinates:** `%d, %d`\n**Overworld chunk coordinates:** `%d, %d`",
            runSet.size(), heading, ageLabel, anchor.x, anchor.z, anchor.x * 8, anchor.z * 8));
        embed.put("color", 15258703);
        embed.put("footer", footer);

        Map<String, Object> payload = new LinkedHashMap<>();
        if (pingId != null) {
            payload.put("content", "<@" + pingId + ">");
            payload.put("allowed_mentions", Map.of("users", List.of(pingId)));
        }
        payload.put("embeds", List.of(embed));
        return GSON.toJson(payload);
    }

    private void assignDelivery(ChunkState state, long deliveryId) {
        if (state.webhookDeliveryId == deliveryId) return;
        releaseDeliveryReference(state.webhookDeliveryId);
        state.webhookDeliveryId = deliveryId;
        if (deliveryId != 0) webhookDeliveryReferences.merge(deliveryId, 1, Integer::sum);
    }

    private void releaseDeliveryReference(long deliveryId) {
        if (deliveryId == 0) return;
        Integer references = webhookDeliveryReferences.get(deliveryId);
        if (references == null || references <= 1) {
            webhookDeliveryReferences.remove(deliveryId);
            webhookDeliveries.remove(deliveryId);
        } else {
            webhookDeliveryReferences.put(deliveryId, references - 1);
        }
    }

    private Set<ChunkKey> collectCluster(ChunkKey start, boolean confirmed) {
        Set<ChunkKey> cluster = new HashSet<>();
        ArrayDeque<ChunkKey> queue = new ArrayDeque<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            ChunkKey current = queue.removeFirst();
            ChunkState state = trackedChunks.get(current);
            if (state == null || state.confirmed != confirmed || !cluster.add(current)) continue;

            for (int[] offset : NEIGHBOR_OFFSETS) {
                queue.addLast(current.offset(offset[0], offset[1]));
            }
        }

        return cluster;
    }

    private void tryNotifyCluster(ChunkKey pos) {
        Set<ChunkKey> pendingCluster = collectCluster(pos, false);
        boolean joinsConfirmedCluster = false;

        for (ChunkKey chunk : pendingCluster) {
            for (int[] offset : NEIGHBOR_OFFSETS) {
                ChunkState neighbor = trackedChunks.get(chunk.offset(offset[0], offset[1]));
                if (neighbor != null && neighbor.confirmed) {
                    joinsConfirmedCluster = true;
                    break;
                }
            }
            if (joinsConfirmedCluster) break;
        }

        if (!joinsConfirmedCluster && pendingCluster.size() < minClusterSize.get()) {
            dbg("  en attente: " + pendingCluster.size() + " chunk(s) < min " + minClusterSize.get());
            return;
        }

        boolean wantsMarkers = logType.get() == LogType.Marker || logType.get() == LogType.Both;
        if (!wantsMarkers) dbg("  marker[A]: ignore, log-type=" + logType.get() + " (aucun waypoint par construction)");

        for (ChunkKey chunk : pendingCluster) {
            ChunkState state = trackedChunks.get(chunk);
            state.confirmed = true;
            if (wantsMarkers) createMapMarker(chunk);
        }

        // Once per new cluster, not per chunk: an extension of a cluster already announced is
        // the same trail, and the feed line would otherwise repeat for every chunk it grows by.
        if (!joinsConfirmedCluster) {
            com.hunterbuddy.util.HuntFeed.get().publish(
                com.hunterbuddy.util.HuntFeed.Type.OLD_CHUNK,
                "Old chunks · cluster of " + pendingCluster.size(),
                new net.minecraft.util.math.BlockPos(pos.x() << 4, 0, pos.z() << 4));
        }

        Set<ChunkKey> confirmedCluster = collectCluster(pos, true);
        dbg("  confirme +" + pendingCluster.size() + " -> cluster de " + confirmedCluster.size()
            + " (ancre " + pos.x + "," + pos.z + ")");
        long existingDeliveryId = findExistingDelivery(confirmedCluster);
        if (existingDeliveryId != 0) {
            for (ChunkKey chunk : confirmedCluster) {
                ChunkState state = trackedChunks.get(chunk);
                if (state.webhookDeliveryId == 0) assignDelivery(state, existingDeliveryId);
            }
            // The cluster keeps growing after the first message went out, so remember the
            // new size and let the tick handler edit the message with the real count.
            Delivery delivery = webhookDeliveries.get(existingDeliveryId);
            if (delivery != null) {
                delivery.clusterSize = confirmedCluster.size();
                dbg("  rattache a la livraison #" + existingDeliveryId + ", annonce "
                    + delivery.reportedSize + " -> reel " + delivery.clusterSize);
            }
        } else {
            scheduleClusterWebhook(pos, confirmedCluster);
        }
    }

    private long findExistingDelivery(Set<ChunkKey> cluster) {
        long pendingDeliveryId = 0;

        for (ChunkKey chunk : cluster) {
            ChunkState state = trackedChunks.get(chunk);
            if (state.webhookDeliveryId == 0) continue;

            Delivery delivery = webhookDeliveries.get(state.webhookDeliveryId);
            DeliveryStatus status = delivery == null ? null : delivery.status;
            if (status == DeliveryStatus.Sent) return state.webhookDeliveryId;
            if (status == DeliveryStatus.Pending) pendingDeliveryId = state.webhookDeliveryId;
            else assignDelivery(state, 0);
        }

        return pendingDeliveryId;
    }

    // Place the waypoint in the waypoint world of the dimension the chunk was DETECTED in, not the
    // one currently displayed. Xaero shows one dimension at a time; adding to getCurrentWorld()
    // (which the user can pin to the Overworld) dropped Nether chunks into the Overworld set where
    // they either landed 8x off or mixed dimensions. WaypointAPI.getMinimapWorld resolves the
    // right per-dimension world regardless of what is displayed, so a Nether chunk lands in the
    // Nether waypoints at real coords and shows when you view the Nether. No explicit saveWorld —
    // Xaero persists permanent waypoints itself; saving here is what froze the game.
    //
    // markers-follow-xaero-dimension overrides this on purpose: when XaeroPlus's own "Prefer
    // Overworld Waypoints" is on, XaeroPlus writes its own spawn point (and Hunt_ already writes its
    // waypoints) into the Overworld world at ×8 coordinates instead, and Xaero's own renderer knows
    // to divide by 8 when the Nether map is showing — so the point still lands in the right spot
    // there without ever switching worlds. That is the whole point of the option: seeing it from
    // the Overworld map without touching the dimension menu.
    private void createMapMarker(ChunkKey chunk) {
        MinimapSession minimapSession = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (minimapSession == null) return;

        MinimapWorld targetWorld = WaypointAPI.getMinimapWorld(chunk.dimension);
        if (targetWorld == null) targetWorld = minimapSession.getWorldManager().getCurrentWorld();
        if (targetWorld == null) return;

        int blockX = chunk.x * 16;
        int blockZ = chunk.z * 16;

        if (markersFollowXaeroDimension.get() && chunk.dimension.equals(World.NETHER) && isPreferOverworldWaypointsEnabled()) {
            MinimapWorld overworld = WaypointFollower.resolveOverworldWaypointWorld();
            if (overworld != null) {
                targetWorld = overworld;
                blockX *= 8;
                blockZ *= 8;
            }
        }

        WaypointSet waypointSet = targetWorld.getCurrentWaypointSet();
        if (waypointSet == null) return;

        // Skip if a waypoint already sits here, so re-scanning an area (or a reconnect reloading
        // permanent waypoints) does not stack duplicates.
        for (Waypoint existing : waypointSet.getWaypoints()) {
            if (existing.getX() == blockX && existing.getZ() == blockZ) return;
        }

        waypointSet.add(new Waypoint(blockX, 70, blockZ, "Old Chunk", "O", 5, 0, temporaryWaypoints.get()));
        SupportMods.xaeroMinimap.requestWaypointsRefresh();

        // A permanent waypoint only lives in memory until the world is written to
        // disk, so without this it vanishes on disconnect exactly like a temporary
        // one. Queued rather than written here: saveWorld rewrites the whole file,
        // and doing that once per confirmed cluster is what used to freeze the game.
        if (!temporaryWaypoints.get()) pendingWaypointSaves.add(targetWorld);
    }

    /** Mirrors {@code WaypointFollower.isPreferOverworldWaypointsEnabled()} — same XaeroPlus setting. */
    private boolean isPreferOverworldWaypointsEnabled() {
        try {
            return Settings.REGISTRY.owAutoWaypointDimension.get();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Writes the worlds that gained a permanent waypoint, at most once every
     * {@link #WAYPOINT_SAVE_INTERVAL_MS}. Forced when the module stops or the
     * server is left, so a marker placed seconds before quitting still lands.
     */
    private void flushWaypointSaves(boolean force) {
        if (pendingWaypointSaves.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (!force && now - lastWaypointSaveMs < WAYPOINT_SAVE_INTERVAL_MS) return;
        lastWaypointSaveMs = now;

        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null) {
            pendingWaypointSaves.clear();
            return;
        }

        for (MinimapWorld world : pendingWaypointSaves) {
            try {
                session.getWorldManagerIO().saveWorld(world);
            } catch (Exception e) {
                error("Failed to save waypoints: " + e.getMessage(), new Object[0]);
            }
        }

        dbg("  waypoints ecrits pour " + pendingWaypointSaves.size() + " monde(s)");
        pendingWaypointSaves.clear();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        flushWaypointSaves(true);
    }

    private void scheduleClusterWebhook(ChunkKey anchor, Set<ChunkKey> cluster) {
        if (logType.get() != LogType.Both && logType.get() != LogType.Webhook) return;

        String rawUrl = webhookLink.get().trim();
        if (rawUrl.isEmpty()) {
            error("Discord webhook URL is empty.", new Object[0]);
            return;
        }

        URI webhookUri = parseDiscordWebhookUrl(rawUrl);
        if (webhookUri == null) {
            error("Invalid Discord webhook URL. Use a channel webhook URL, not a server invite.", new Object[0]);
            return;
        }

        String pingId = null;
        if (ping.get() && !discordId.get().isBlank()) {
            pingId = discordId.get().trim();
            if (!pingId.matches("\\d+")) {
                error("Invalid Discord user ID. It must contain digits only.", new Object[0]);
                return;
            }
        }

        ExecutorService executor = discordExecutor;
        if (executor == null || executor.isShutdown()) {
            error("Discord webhook executor is not available.", new Object[0]);
            return;
        }

        String playerName = mc.player != null ? mc.player.getGameProfile().name() : "Unknown";
        String json = buildWebhookJson(anchor, cluster, playerName, pingId);
        long deliveryId = nextWebhookDeliveryId++;
        Delivery delivery = new Delivery(anchor, cluster.size());
        webhookDeliveries.put(deliveryId, delivery);
        for (ChunkKey chunk : cluster) {
            assignDelivery(trackedChunks.get(chunk), deliveryId);
        }

        // wait=true makes Discord return the created message, which gives us the id we
        // need to edit the chunk count later.
        URI createUri = URI.create(webhookUri.toString() + "?wait=true");

        dbg("  POST livraison #" + deliveryId + " avec " + cluster.size() + " chunk(s)");

        try {
            executor.submit(() -> {
                WebhookResult result = sendWebhookRequest("POST", createUri, json);
                mc.execute(() -> completeWebhookDelivery(deliveryId, result));
            });
        } catch (RejectedExecutionException e) {
            clearDelivery(deliveryId);
            error("Discord webhook could not be queued.", new Object[0]);
        }
    }

    private void processDeliveryEdits() {
        for (Map.Entry<Long, Delivery> entry : webhookDeliveries.entrySet()) {
            Delivery delivery = entry.getValue();
            if (delivery.editCooldown > 0) {
                delivery.editCooldown--;
                continue;
            }
            if (delivery.status != DeliveryStatus.Sent || delivery.messageId == null) continue;
            if (delivery.editInFlight || delivery.clusterSize <= delivery.reportedSize) continue;
            scheduleClusterEdit(entry.getKey(), delivery);
        }
    }

    private void scheduleClusterEdit(long deliveryId, Delivery delivery) {
        Set<ChunkKey> cluster = collectCluster(delivery.anchor, true);
        if (cluster.isEmpty()) {
            // The anchor was trimmed out of the tracker; nothing left to report.
            delivery.reportedSize = delivery.clusterSize;
            return;
        }

        URI webhookUri = parseDiscordWebhookUrl(webhookLink.get().trim());
        if (webhookUri == null) return;

        ExecutorService executor = discordExecutor;
        if (executor == null || executor.isShutdown()) return;

        String pingId = null;
        if (ping.get() && discordId.get().trim().matches("\\d+")) pingId = discordId.get().trim();

        String playerName = mc.player != null ? mc.player.getGameProfile().name() : "Unknown";
        String json = buildWebhookJson(delivery.anchor, cluster, playerName, pingId);
        URI editUri = URI.create(webhookUri.toString() + "/messages/" + delivery.messageId);
        int size = cluster.size();

        dbg("  PATCH #" + deliveryId + " : " + delivery.reportedSize + " -> " + size + " chunk(s)");

        delivery.editInFlight = true;
        try {
            executor.submit(() -> {
                WebhookResult result = sendWebhookRequest("PATCH", editUri, json);
                mc.execute(() -> completeWebhookEdit(deliveryId, size, result));
            });
        } catch (RejectedExecutionException e) {
            delivery.editInFlight = false;
        }
    }

    private void completeWebhookEdit(long deliveryId, int size, WebhookResult result) {
        Delivery delivery = webhookDeliveries.get(deliveryId);
        if (delivery == null) return;

        delivery.editInFlight = false;
        delivery.editCooldown = WEBHOOK_EDIT_DELAY_TICKS;

        if (result.success) {
            delivery.reportedSize = Math.max(delivery.reportedSize, size);
            dbg("  PATCH #" + deliveryId + " ok, annonce " + delivery.reportedSize);
        } else if (result.messageGone) {
            dbg("  PATCH #" + deliveryId + " : message supprime, renvoi prevu");
            // Someone deleted the message; drop the delivery so the retry pass posts a
            // fresh one with the full count.
            clearDelivery(deliveryId);
        } else {
            dbg("  PATCH #" + deliveryId + " echoue: " + result.error);
        }
    }

    /**
     * Counts every confirmed cluster currently tracked. This is the number to compare
     * against what Discord reported: if one cluster holds far more chunks than the
     * message says, the growth is not reaching the delivery.
     */
    private void logClusterCensus() {
        Set<ChunkKey> visited = new HashSet<>();
        List<Integer> sizes = new ArrayList<>();
        int confirmed = 0;

        for (Map.Entry<ChunkKey, ChunkState> entry : trackedChunks.entrySet()) {
            if (!entry.getValue().confirmed) continue;
            confirmed++;
            if (visited.contains(entry.getKey())) continue;
            Set<ChunkKey> cluster = collectCluster(entry.getKey(), true);
            visited.addAll(cluster);
            sizes.add(cluster.size());
        }

        sizes.sort((a, b) -> b - a);
        StringBuilder biggest = new StringBuilder();
        for (int i = 0; i < Math.min(5, sizes.size()); i++) {
            if (i > 0) biggest.append(", ");
            biggest.append(sizes.get(i));
        }

        dbg("bilan: " + trackedChunks.size() + " chunk(s) suivis, " + confirmed + " confirme(s), "
            + sizes.size() + " cluster(s), plus gros: [" + biggest + "], "
            + webhookDeliveries.size() + " livraison(s)");

        for (Map.Entry<Long, Delivery> entry : webhookDeliveries.entrySet()) {
            Delivery delivery = entry.getValue();
            dbg("  livraison #" + entry.getKey() + " " + delivery.status
                + " annonce=" + delivery.reportedSize + " reel=" + delivery.clusterSize
                + (delivery.messageId == null ? " SANS message id" : ""));
        }
    }

    private URI parseDiscordWebhookUrl(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl);
            String host = uri.getHost();
            String path = uri.getPath();
            boolean validHost = host != null && host.matches("(?i)(.+\\.)?discord(app)?\\.com");
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !validHost || path == null || !path.startsWith("/api/webhooks/")) return null;
            // Drop any query, fragment or trailing slash so "?wait=true" and "/messages/<id>"
            // can be appended safely.
            while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            return URI.create("https://" + host + path);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String buildWebhookJson(ChunkKey anchor, Set<ChunkKey> cluster, String playerName, String pingId) {
        Map<String, Object> footer = Map.of("text", "From: " + playerName);
        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("title", "Old Chunk Cluster Detected");
        embed.put("description", buildClusterMessage(anchor, cluster));
        embed.put("color", 15258703);
        embed.put("footer", footer);

        Map<String, Object> payload = new LinkedHashMap<>();
        if (pingId != null) {
            payload.put("content", "<@" + pingId + ">");
            payload.put("allowed_mentions", Map.of("users", List.of(pingId)));
        }
        payload.put("embeds", List.of(embed));
        return GSON.toJson(payload);
    }

    private String buildClusterMessage(ChunkKey anchor, Set<ChunkKey> cluster) {
        EnumSet<DetectedChunkType> types = EnumSet.noneOf(DetectedChunkType.class);
        boolean containsOffHighwayChunk = false;

        for (ChunkKey chunk : cluster) {
            ChunkState state = trackedChunks.get(chunk);
            types.add(state.type);
            containsOffHighwayChunk |= state.offHighway;
        }

        List<String> typeNames = new ArrayList<>();
        for (DetectedChunkType type : types) typeNames.add(type.label);

        long blockX = (long) anchor.x * 16;
        long blockZ = (long) anchor.z * 16;
        return "**Chunk types:** " + String.join(", ", typeNames) +
            "\n**Dimension:** " + dimensionName(anchor.dimension) +
            "\n**Chunks marked:** " + cluster.size() +
            "\n**Chunk coordinates:** `" + anchor.x + ", " + anchor.z + "`" +
            "\n**Block coordinates:** `" + blockX + ", " + blockZ + "`" +
            "\n**Contains off-highway chunks:** " + (containsOffHighwayChunk ? "Yes" : "No");
    }

    private String dimensionName(RegistryKey<World> dimension) {
        if (dimension.equals(World.OVERWORLD)) return "Overworld";
        if (dimension.equals(World.NETHER)) return "Nether";
        if (dimension.equals(World.END)) return "The End";
        return dimension.getValue().toString();
    }

    private WebhookResult sendWebhookRequest(String method, URI webhookUri, String json) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(webhookUri)
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(json))
            .build();
        String lastError = "Unknown error";

        for (int attempt = 1; attempt <= MAX_WEBHOOK_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = discordHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status >= 200 && status < 300) return new WebhookResult(true, null, readMessageId(response.body()), false);

                lastError = "Discord returned HTTP " + status;
                if (status == 404 || status == 410) return new WebhookResult(false, lastError, null, true);
                if (status != 429 && status < 500) break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new WebhookResult(false, "Webhook delivery was interrupted", null, false);
            } catch (Exception e) {
                lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }

            if (attempt < MAX_WEBHOOK_ATTEMPTS) {
                try {
                    Thread.sleep(attempt * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new WebhookResult(false, "Webhook retry was interrupted", null, false);
                }
            }
        }

        return new WebhookResult(false, lastError, null, false);
    }

    private static String readMessageId(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            Map<?, ?> parsed = GSON.fromJson(body, Map.class);
            Object id = parsed == null ? null : parsed.get("id");
            return id == null ? null : id.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void completeWebhookDelivery(long deliveryId, WebhookResult result) {
        Delivery delivery = webhookDeliveries.get(deliveryId);
        if (delivery == null || delivery.status != DeliveryStatus.Pending) return;

        if (result.success) {
            delivery.status = DeliveryStatus.Sent;
            delivery.messageId = result.messageId;
            delivery.editCooldown = WEBHOOK_EDIT_DELAY_TICKS;
            dbg("  POST #" + deliveryId + " ok, message id="
                + (result.messageId == null ? "INTROUVABLE (edition impossible)" : result.messageId));
        } else {
            clearDelivery(deliveryId);
            error("Discord webhook failed: " + result.error, new Object[0]);
        }
    }

    private void clearDelivery(long deliveryId) {
        webhookDeliveries.remove(deliveryId);
        webhookDeliveryReferences.remove(deliveryId);
        for (ChunkState state : trackedChunks.values()) {
            if (state.webhookDeliveryId == deliveryId) state.webhookDeliveryId = 0;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        flushWaypointSaves(false);

        if (mc.player == null) return;

        if (debug.get()) {
            if (debugCensusCooldown > 0) debugCensusCooldown--;
            else {
                debugCensusCooldown = DEBUG_CENSUS_TICKS;
                logClusterCensus();
            }
        }

        if (logType.get() != LogType.Both && logType.get() != LogType.Webhook) return;

        processDeliveryEdits();

        if (webhookRetryCooldown > 0) {
            webhookRetryCooldown--;
            return;
        }

        webhookRetryCooldown = WEBHOOK_RETRY_DELAY_TICKS;
        retryUnsentClusters();
    }

    private void retryUnsentClusters() {
        Set<ChunkKey> visited = new HashSet<>();

        for (ChunkKey key : trackedChunks.keySet()) {
            ChunkState state = trackedChunks.get(key);
            if (state == null || !state.confirmed || !visited.add(key)) continue;

            Set<ChunkKey> cluster = collectCluster(key, true);
            visited.addAll(cluster);
            if (findExistingDelivery(cluster) == 0) {
                scheduleClusterWebhook(key, cluster);
            }
        }
    }

    private enum DetectedChunkType {
        Old112Followed("1.12 followed in 1.19+"),
        Old112Unfollowed("1.12 not followed in 1.19+"),
        Old119("1.19+"),
        NetherRoadOld("nether road, pre-1.16"),
        NetherRoadSeen("nether road, generated before us");

        private final String label;

        DetectedChunkType(String label) {
            this.label = label;
        }
    }

    private enum DeliveryStatus {
        Pending,
        Sent
    }

    /**
     * nether-roads's own verdict, read from blocks and biomes rather than XaeroPlus's caches.
     * {@code UNREADABLE} means the chunk hasn't actually arrived far enough to say either way —
     * see {@link #netherReadable} — and is never treated as a candidate.
     */
    private enum NetherAge {
        OLD,
        NEW,
        UNREADABLE
    }

    /** One chunk's nether-roads verdict, kept in {@link #netherSeen} for as long as it fits. */
    private record NetherClassification(NetherAge age, int core, int total, boolean biomeUniform, boolean fresh) {
        /**
         * A chunk worth building a road out of: pre-1.16 by the block/biome rule, or simply not
         * generated for us just now (§1.4 of the spec this implements — palette freshness is the
         * only signal that reaches a post-1.16 trail at all).
         */
        private boolean isCandidate() {
            return age == NetherAge.OLD || !fresh;
        }
    }

    private record ChunkKey(RegistryKey<World> dimension, int x, int z) {
        private ChunkKey offset(int offsetX, int offsetZ) {
            return new ChunkKey(dimension, x + offsetX, z + offsetZ);
        }
    }

    private record WebhookResult(boolean success, String error, String messageId, boolean messageGone) {}

    private static class Delivery {
        private final ChunkKey anchor;
        private DeliveryStatus status = DeliveryStatus.Pending;
        private String messageId;
        private int reportedSize;
        private int clusterSize;
        private int editCooldown;
        private boolean editInFlight;

        private Delivery(ChunkKey anchor, int size) {
            this.anchor = anchor;
            this.reportedSize = size;
            this.clusterSize = size;
        }
    }

    private static class ChunkState {
        private DetectedChunkType type;
        private boolean offHighway;
        private boolean confirmed;
        private long webhookDeliveryId;

        private ChunkState(DetectedChunkType type, boolean offHighway) {
            this.type = type;
            this.offHighway = offHighway;
        }
    }

    private enum LogType
    {
        Webhook,
        Marker,
        Both
    }
}
