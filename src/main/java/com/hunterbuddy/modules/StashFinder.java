package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.stash.BlockEntityClassifier;
import com.hunterbuddy.modules.stash.ChunkScanner;
import com.hunterbuddy.modules.stash.EntityDetections;
import com.hunterbuddy.modules.stash.EntityWatch;
import com.hunterbuddy.modules.stash.FrameClassifier;
import com.hunterbuddy.modules.stash.OldChunkGate;
import com.hunterbuddy.modules.stash.PackingTest;
import com.hunterbuddy.modules.stash.PendingEntities;
import com.hunterbuddy.modules.stash.SpawnGate;
import com.hunterbuddy.modules.stash.StashAnnouncer;
import com.hunterbuddy.modules.stash.StashDetectionSettings;
import com.hunterbuddy.modules.stash.StashDimensions;
import com.hunterbuddy.modules.stash.StashDiscord;
import com.hunterbuddy.modules.stash.StashDiscordSettings;
import com.hunterbuddy.modules.stash.StashFilterSettings;
import com.hunterbuddy.modules.stash.StashGeneralSettings;
import com.hunterbuddy.modules.stash.StashGroups;
import com.hunterbuddy.modules.stash.StashLedger;
import com.hunterbuddy.modules.stash.StashNotifier;
import com.hunterbuddy.modules.stash.StashPrivacySettings;
import com.hunterbuddy.modules.stash.StashQualifier;
import com.hunterbuddy.modules.stash.StashRecorder;
import com.hunterbuddy.modules.stash.StashRenderSettings;
import com.hunterbuddy.modules.stash.StashThresholdSettings;
import com.hunterbuddy.modules.stash.StashType;
import com.hunterbuddy.modules.stash.StashWaypointSettings;
import com.hunterbuddy.modules.stash.StashWaypoints;
import com.hunterbuddy.modules.stash.SupportBlockFilter;
import com.hunterbuddy.modules.stash.TracerBoard;
import com.hunterbuddy.modules.stash.TracerMarker;
import com.hunterbuddy.modules.stash.TracerRenderer;
import com.hunterbuddy.modules.stash.ui.CoordinateListScreen;
import com.hunterbuddy.modules.stash.ui.StashServices;
import com.hunterbuddy.modules.stash.ui.StashWidget;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;

public class StashFinder extends Module {
    public enum NotificationMode {
        Chat,
        Toast,
        Sound,
        Both,
        All,
        Silent
    }

    public static class StashChunk {
        private static final int CHUNK_SIZE = 16;
        private static final int CHUNK_CENTER = 8;
        private static final String[] COUNTER_NAMES = {
            "chests", "trappedChests", "barrels", "shulkers", "enderChests", "furnaces", "dispensersDroppers",
            "hoppers", "brewingStands", "crafters", "decoratedPots", "banners", "signs", "hangingSigns",
            "mapItemFrames", "itemFrames", "enderPearls", "stackedMinecarts", "namedEntities"
        };
        private static final Field[] COUNTER_FIELDS = counterFields();

        public ChunkPos chunkPos;
        public transient int x;
        public transient int z;
        public String dimension = StashDimensions.OVERWORLD;
        public int chests;
        public int trappedChests;
        public int barrels;
        public int shulkers;
        public int enderChests;
        public int furnaces;
        public int dispensersDroppers;
        public int hoppers;
        public int brewingStands;
        public int crafters;
        public int decoratedPots;
        public int banners;
        public int signs;
        public int hangingSigns;
        public int mapItemFrames;
        public int itemFrames;
        public int enderPearls;
        public int namedEntities;
        public int stackedMinecarts;

        public StashChunk() {
        }

        public StashChunk(ChunkPos chunkPos) {
            this.chunkPos = chunkPos;
            calculatePos();
        }

        public void calculatePos() {
            x = chunkPos.x * CHUNK_SIZE + CHUNK_CENTER;
            z = chunkPos.z * CHUNK_SIZE + CHUNK_CENTER;
        }

        public int getTotal() {
            int total = 0;
            for (StashType type : StashType.values()) {
                if (type != StashType.STACKED_MINECART) total += countOf(type);
            }
            return total;
        }

        public int getHighValueTotal() {
            return countOf(StashType.CHEST) + countOf(StashType.TRAPPED_CHEST)
                + countOf(StashType.SHULKER) + countOf(StashType.BARREL);
        }

        public boolean countsEqual(StashChunk other) {
            if (other == null) return false;
            for (StashType type : StashType.values()) {
                if (countOf(type) != other.countOf(type)) return false;
            }
            return true;
        }

        public StashChunk copyOfCounters() {
            StashChunk copy = new StashChunk(chunkPos);
            copy.dimension = dimension;
            for (StashType type : StashType.values()) copy.setCount(type, countOf(type));
            return copy;
        }

        public void increment(StashType type) {
            setCount(type, countOf(type) + 1);
        }

        public int countOf(StashType type) {
            try {
                return COUNTER_FIELDS[type.ordinal()].getInt(this);
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException(failure);
            }
        }

        public void setCount(StashType type, int count) {
            try {
                COUNTER_FIELDS[type.ordinal()].setInt(this, count);
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException(failure);
            }
        }

        private static Field[] counterFields() {
            Field[] fields = new Field[COUNTER_NAMES.length];
            try {
                for (int index = 0; index < fields.length; index++) {
                    fields[index] = StashChunk.class.getField(COUNTER_NAMES[index]);
                }
            } catch (NoSuchFieldException failure) {
                throw new ExceptionInInitializerError(failure);
            }
            return fields;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof StashChunk chunk
                && Objects.equals(dimension, chunk.dimension) && chunkPos.equals(chunk.chunkPos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dimension, chunkPos);
        }
    }

    private final StashGroups groups = StashGroups.create(settings);
    private final StashGeneralSettings general = new StashGeneralSettings(groups.general());
    private final StashDetectionSettings detection = new StashDetectionSettings(groups.detection());
    private final StashFilterSettings filter = new StashFilterSettings(groups.detection());
    private final StashThresholdSettings thresholds = new StashThresholdSettings(groups.thresholds());
    private final StashPrivacySettings privacy = new StashPrivacySettings(groups.privacy());
    private final StashRenderSettings render = new StashRenderSettings(groups.render());
    private final StashWaypointSettings waypointSettings = new StashWaypointSettings(groups.waypoints());
    private final StashDiscordSettings discordSettings = new StashDiscordSettings(groups.discord());

    private final StashLedger ledger = new StashLedger();
    private final TracerBoard tracers = new TracerBoard();
    private final OldChunkGate oldChunks = new OldChunkGate(filter);
    private final SpawnGate spawnGate = new SpawnGate(general);
    private final StashQualifier qualifier = new StashQualifier(general, thresholds);
    private final StashWaypoints waypoints = new StashWaypoints(general, thresholds, waypointSettings, ledger);
    private final StashAnnouncer announcer = new StashAnnouncer(
        general, waypoints, new StashNotifier(general, privacy, () -> title), new StashDiscord(discordSettings));
    private final StashRecorder recorder = new StashRecorder(ledger, qualifier, new TracerMarker(tracers, render), announcer);
    private final ChunkScanner scanner = new ChunkScanner(
        spawnGate, new BlockEntityClassifier(detection), new SupportBlockFilter(filter), new PackingTest(filter));
    private final EntityWatch entities = new EntityWatch(
        detection,
        thresholds,
        new EntityDetections(ledger, recorder, spawnGate, oldChunks),
        new PendingEntities(new FrameClassifier(detection)));
    private final TracerRenderer tracerRenderer = new TracerRenderer(render, tracers);
    private final StashServices services = new StashServices(ledger, tracers, privacy);

    public List<StashChunk> chunks = ledger.chunks();

    public StashFinder() {
        super(
            HunterBuddyAddon.HUNT_CATEGORY,
            "stash-finder",
            "Enhanced stash detection with privacy-focused coordinate management."
        );
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return StashWidget.build(theme, services);
    }

    @Override
    public String getInfoString() {
        return String.valueOf(chunks.size());
    }

    @Override
    public void onActivate() {
        chunks = ledger.reset();
        waypoints.clearClusters();
        oldChunks.reset();
        entities.clear();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        entities.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        entities.tickPending();
        handleKeybinds();
        scanAcceptedChunks();
        entities.tickSweep();
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (oldChunks.defersScans()) oldChunks.defer(event.chunk().getPos());
        else scan(event.chunk());
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        entities.onEntityAdded(event.entity);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        tracerRenderer.render(event);
    }

    private void handleKeybinds() {
        if (render.clearTraces.get().isPressed()) tracers.clear();
        if (privacy.openCoordinateList.get().isPressed() && mc.currentScreen == null) {
            mc.setScreen(new CoordinateListScreen(GuiThemes.get(), services));
        }
    }

    private void scanAcceptedChunks() {
        List<ChunkPos> accepted = oldChunks.drainAccepted();
        if (mc.world == null || mc.player == null) return;
        for (ChunkPos position : accepted) {
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(position.x, position.z);
            if (chunk != null) scan(chunk);
        }
    }

    private void scan(WorldChunk chunk) {
        StashChunk fresh = scanner.scan(chunk);
        if (fresh != null) recorder.commit(fresh, ledger.find(fresh.dimension, fresh.chunkPos));
    }
}
