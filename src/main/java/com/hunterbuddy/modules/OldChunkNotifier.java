package com.hunterbuddy.modules;

import com.google.gson.Gson;
import com.hunterbuddy.HunterBuddyAddon;
import static com.hunterbuddy.modules.regear.util.Utils.yawToDirection;
import static com.hunterbuddy.modules.regear.util.Utils.distancePointToDirection;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.map.mods.SupportMods;
import xaeroplus.XaeroPlus;
import xaeroplus.event.ChunkDataEvent;
import xaeroplus.module.ModuleManager;
import xaeroplus.module.impl.OldChunks;
import xaeroplus.module.impl.PaletteNewChunks;
import xaero.common.minimap.waypoints.Waypoint;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private final LinkedHashMap<ChunkKey, Boolean> processedChunks = new LinkedHashMap<>();
    private final LinkedHashMap<ChunkKey, ChunkState> trackedChunks = new LinkedHashMap<>();
    private final Map<Long, Delivery> webhookDeliveries = new HashMap<>();
    private final Map<Long, Integer> webhookDeliveryReferences = new HashMap<>();
    private final HttpClient discordHttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private ExecutorService discordExecutor;
    private long nextWebhookDeliveryId = 1;
    private int webhookRetryCooldown;
    private int debugCensusCooldown;

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

        DetectedChunkType detectedType;
        if (is112OldChunk && !is119NewChunk) {
            detectedType = DetectedChunkType.Old112Followed;
        } else if (is112OldChunk) {
            detectedType = DetectedChunkType.Old112Unfollowed;
        } else {
            detectedType = DetectedChunkType.Old119;
        }

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

        boolean markerAdded = false;
        for (ChunkKey chunk : pendingCluster) {
            ChunkState state = trackedChunks.get(chunk);
            state.confirmed = true;
            markerAdded |= createMarkerNotification(chunk);
        }

        // Permanent waypoints only survive a disconnect if the world is written to disk.
        // Save once for the whole cluster instead of once per chunk.
        if (markerAdded && !temporaryWaypoints.get()) saveWaypoints();

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

    private boolean createMarkerNotification(ChunkKey pos) {
        if (logType.get() != LogType.Both && logType.get() != LogType.Marker) return false;
        return createMapMarker(pos.x, pos.z);
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

    private boolean createMapMarker(int x, int z)
    {
        WaypointSet waypointSet = getWaypointSet();
        if (waypointSet == null) return false;

        int blockX = x * 16;
        int blockZ = z * 16;

        // Permanent waypoints are reloaded from disk on the next session, so a second
        // pass over the same area would stack duplicates without this check.
        for (Waypoint existing : waypointSet.getWaypoints()) {
            if (existing.getX() == blockX && existing.getZ() == blockZ) return false;
        }

        Waypoint waypoint = new Waypoint(
            blockX,
            70,
            blockZ,
            "Old Chunk",
            "O",
            5,
            0,
            temporaryWaypoints.get());
        waypointSet.add(waypoint);
        SupportMods.xaeroMinimap.requestWaypointsRefresh();
        return true;
    }

    private WaypointSet getWaypointSet()
    {
        MinimapSession minimapSession = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (minimapSession == null) return null;
        MinimapWorld currentWorld = minimapSession.getWorldManager().getCurrentWorld();
        if (currentWorld == null) return null;
        return currentWorld.getCurrentWaypointSet();
    }

    private void saveWaypoints()
    {
        try {
            MinimapSession minimapSession = BuiltInHudModules.MINIMAP.getCurrentSession();
            if (minimapSession == null) return;
            MinimapWorld currentWorld = minimapSession.getWorldManager().getCurrentWorld();
            if (currentWorld == null) return;
            minimapSession.getWorldManagerIO().saveWorld(currentWorld);
        } catch (Exception e) {
            error("Failed to save waypoints: " + e.getMessage(), new Object[0]);
        }
    }

    private enum DetectedChunkType {
        Old112Followed("1.12 followed in 1.19+"),
        Old112Unfollowed("1.12 not followed in 1.19+"),
        Old119("1.19+");

        private final String label;

        DetectedChunkType(String label) {
            this.label = label;
        }
    }

    private enum DeliveryStatus {
        Pending,
        Sent
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
