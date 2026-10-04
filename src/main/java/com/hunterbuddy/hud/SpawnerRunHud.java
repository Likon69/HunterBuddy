package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.SpawnerDetector;
import com.hunterbuddy.util.HudPulse;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;

public class SpawnerRunHud extends HudElement {
    public static final HudElementInfo<SpawnerRunHud> INFO = new HudElementInfo<>(
        HunterBuddyAddon.HUD_GROUP, "SpawnerRun",
        "The spawner farm run's current step, in a choice of styles: a scrolling console log, a compact card, a single line, or the classic timeline band.",
        SpawnerRunHud::new);

    public enum HudStyle {
        CONSOLE,
        TIMELINE,
        CARD,
        LINE
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgPanel = settings.createGroup("Panel");

    private final Setting<HudStyle> style = sgGeneral.add(new EnumSetting.Builder<HudStyle>()
        .name("style")
        .description("CONSOLE is a panel with the current step, its last events, the last runs and the session totals. CARD is a compact readable status card. LINE is a single minimal line. TIMELINE is the classic time band with phase history.")
        .defaultValue(HudStyle.CONSOLE).build());

    private final Setting<Integer> logLines = sgGeneral.add(new IntSetting.Builder()
        .name("log-lines")
        .description("CONSOLE only: how many recent events the log keeps on screen.")
        .defaultValue(4).min(1).max(8).sliderRange(1, 8)
        .visible(() -> style.get() == HudStyle.CONSOLE).build());

    private final Setting<Integer> historyRuns = sgGeneral.add(new IntSetting.Builder()
        .name("history-runs")
        .description("CONSOLE only: how many finished runs to list, newest first, with their phases, time, loot and outcome. 0 hides the list.")
        .defaultValue(5).min(0).max(8).sliderRange(0, 8)
        .visible(() -> style.get() == HudStyle.CONSOLE).build());

    private final Setting<Boolean> sessionStats = sgGeneral.add(new BoolSetting.Builder()
        .name("session-stats")
        .description("CONSOLE only: rooms, spawners, chests and shulkers since the client started, with the average run time and rooms per hour of active time.")
        .defaultValue(true).visible(() -> style.get() == HudStyle.CONSOLE).build());

    private final Setting<Boolean> showHistory = sgGeneral.add(new BoolSetting.Builder()
        .name("show-history")
        .description("TIMELINE only: stack the last runs as thin bars below the live band, with a small legend.")
        .defaultValue(false).visible(() -> style.get() == HudStyle.TIMELINE).build());

    private final Setting<Double> size = sgGeneral.add(new DoubleSetting.Builder()
        .name("size")
        .description("Master scale for the whole element.")
        .defaultValue(1.0).min(0.6).max(2.0).sliderRange(0.6, 2.0).build());

    private final Setting<Boolean> animations = sgGeneral.add(new BoolSetting.Builder()
        .name("animations")
        .description("Motion: the phase word slides on change, the band grows smoothly, hatching scrolls, gauges ease toward their new value. Off draws the same thing standing still.")
        .defaultValue(true).build());

    private final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow").description("Render shadow behind text.").defaultValue(true).build());

    private final Setting<SettingColor> volColor = sgColors.add(new ColorSetting.Builder()
        .name("fly-color").description("Flight: PREPARE, APPROACH, FINAL.")
        .defaultValue(new SettingColor(110, 180, 250, 255)).build());

    private final Setting<SettingColor> poseColor = sgColors.add(new ColorSetting.Builder()
        .name("land-color").description("On the ground, walking to the shaft: WALK.")
        .defaultValue(new SettingColor(225, 195, 140, 255)).build());

    private final Setting<SettingColor> creuseColor = sgColors.add(new ColorSetting.Builder()
        .name("dig-color").description("Digging in: DIG, SIDE_DIG, TUNNEL.")
        .defaultValue(new SettingColor(205, 140, 60, 255)).build());

    private final Setting<SettingColor> salleColor = sgColors.add(new ColorSetting.Builder()
        .name("room-color").description("Working the room: ROOM, ROOM_CLIMB.")
        .defaultValue(new SettingColor(230, 190, 70, 255)).build());

    private final Setting<SettingColor> sortieColor = sgColors.add(new ColorSetting.Builder()
        .name("exit-color").description("Leaving the shaft: EXIT, SHAFT_ALIGN, SHAFT_FLY, SWIM_UP, WATER_JUMP, ASCEND, TAKEOFF_SPOT, TAKEOFF, OBSIDIAN_PLATFORM.")
        .defaultValue(new SettingColor(70, 205, 190, 255)).build());

    private final Setting<SettingColor> monteeColor = sgColors.add(new ColorSetting.Builder()
        .name("climb-color").description("Climbing back to height: CLIMB.")
        .defaultValue(new SettingColor(180, 155, 230, 255)).build());

    private final Setting<SettingColor> relaisColor = sgColors.add(new ColorSetting.Builder()
        .name("handoff-color").description("Handing over to Baritone: RESUME.")
        .defaultValue(new SettingColor(175, 175, 185, 255)).build());

    private final Setting<SettingColor> amberColor = sgColors.add(new ColorSetting.Builder()
        .name("amber-color").description("Something is taking longer than usual, no movement in 5s, an overflowing step on the band, or a combat pause.")
        .defaultValue(new SettingColor(255, 180, 60, 255)).build());

    private final Setting<SettingColor> badColor = sgColors.add(new ColorSetting.Builder()
        .name("bad-color").description("A real failure: FAILED, or a retry budget spent.")
        .defaultValue(new SettingColor(255, 80, 80, 255)).build());

    private final Setting<SettingColor> valueColor = sgColors.add(new ColorSetting.Builder()
        .name("value-color").description("Numbers and the run clock in normal range.")
        .defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private final Setting<SettingColor> mutedColor = sgColors.add(new ColorSetting.Builder()
        .name("muted-color").description("Track backgrounds, ghost ticks, a plain pause, the legend.")
        .defaultValue(new SettingColor(150, 150, 150, 255)).build());

    private final Setting<SettingColor> accentColor = sgColors.add(new ColorSetting.Builder()
        .name("accent-color").description("CONSOLE only: the progress dot and the log lines for a run starting or finishing.")
        .defaultValue(new SettingColor(143, 211, 207, 255)).visible(() -> style.get() == HudStyle.CONSOLE).build());

    private final Setting<SettingColor> fightColor = sgColors.add(new ColorSetting.Builder()
        .name("combat-color").description("CONSOLE only: the title and the log line while a combat pause is active.")
        .defaultValue(new SettingColor(230, 200, 255, 255)).visible(() -> style.get() == HudStyle.CONSOLE).build());

    private final Setting<Boolean> background = sgPanel.add(new BoolSetting.Builder()
        .name("background").description("A flat translucent panel behind the element. No glow, on purpose.")
        .defaultValue(true).build());

    private final Setting<SettingColor> backgroundColor = sgPanel.add(new ColorSetting.Builder()
        .name("background-color").defaultValue(new SettingColor(0, 0, 0, 120)).visible(background::get).build());

    private final Setting<Double> padding = sgPanel.add(new DoubleSetting.Builder()
        .name("padding").description("Space between the content and the edge of the panel.")
        .defaultValue(4.0).min(0.0).max(20.0).sliderRange(0.0, 12.0).build());


    private static final int G_VOL = 0, G_POSE = 1, G_CREUSE = 2, G_SALLE = 3, G_SORTIE = 4, G_MONTEE = 5, G_RELAIS = 6;
    private static final String[] GROUP_WORD = {"FLY", "LAND", "DIG", "ROOM", "EXIT", "CLIMB", "HANDOFF"};
    private static final String[] SHORT_WORDS = {
        "FLY", "LAND", "DIG", "ROOM", "EXIT", "CLIMB", "HANDOFF", "SWIM", "SINK", "SEARCH", "FIGHT", "FAILED", "PAUSED", "MMMMMMMM"
    };
    private static final String[] CARD_LABELS = {
        "PREPARING", "APPROACHING", "FINAL APPROACH", "WALKING", "SWIMMING TO HOLE", "SINKING",
        "DIGGING", "SIDE DIGGING", "TUNNELING", "IN ROOM", "ROOM CLIMB", "EXITING",
        "ALIGNING SHAFT", "SHAFT FLYING", "SWIMMING UP", "WATER JUMP", "ASCENDING",
        "FINDING TAKEOFF", "TAKING OFF", "BUILDING PLATFORM", "CLIMBING", "HANDING OFF",
        "SEARCHING", "FIGHTING", "PAUSED", "FAILED"
    };
    private static final long[] DEFAULT_AVG_MS = {25_000L, 8_000L, 40_000L, 45_000L, 30_000L, 15_000L, 5_000L};
    private static final int HIST_ROWS = 7;
    private static final long WORD_ANIM_MS = 220L;
    private static final long INSERT_ANIM_MS = 450L;
    private static final long IMMOBILE_MS = 5_000L;
    private static final long MAX_DISPLAY_MS = 99L * 60_000L + 59_000L;

    private static final String CHRONO_TEMPLATE = "00:00";
    private static final String DIST_TEMPLATE = "999.9 m";
    private static final String FRACTION_TEMPLATE = "999/999";
    private static final String RETRY_TEMPLATE = "retry 999/999";
    private static final String RANGE_TEMPLATE = "y -9999 -> -9999";
    private static final String RANGE_TEMPLATE_COMPACT = "y-9999->-9999";

    private static int groupOf(String state) {
        return switch (state) {
            case "PREPARE", "APPROACH", "FINAL" -> G_VOL;
            case "WALK" -> G_POSE;
            case "DIG", "SIDE_DIG", "TUNNEL" -> G_CREUSE;
            case "ROOM", "ROOM_CLIMB" -> G_SALLE;
            case "EXIT", "SHAFT_ALIGN", "SHAFT_FLY", "SWIM_UP", "WATER_JUMP", "ASCEND",
                 "TAKEOFF_SPOT", "TAKEOFF", "OBSIDIAN_PLATFORM" -> G_SORTIE;
            case "CLIMB" -> G_MONTEE;
            case "RESUME" -> G_RELAIS;
            default -> -1;
        };
    }

    private static boolean isPausableState(String state) {
        return switch (state) {
            case "DIG", "SIDE_DIG", "TUNNEL", "ROOM", "ROOM_CLIMB", "SHAFT_ALIGN" -> true;
            default -> false;
        };
    }


    private static final class Segment {
        final int group;
        final long startMs;
        long endMs = -1L;

        Segment(int group, long startMs) {
            this.group = group;
            this.startMs = startMs;
        }

        long durationMs(long now) {
            return (endMs >= 0L ? endMs : now) - startMs;
        }
    }

    private record Frozen(int group, long durationMs) {}

    private record RunRecord(List<Frozen> segments, long totalMs, boolean failed) {}

    private static final class Insertion {
        final List<Frozen> segments;
        final long totalMs;
        final boolean failed;
        final long startedMs;

        Insertion(List<Frozen> segments, long totalMs, boolean failed, long startedMs) {
            this.segments = segments;
            this.totalMs = totalMs;
            this.failed = failed;
            this.startedMs = startedMs;
        }
    }

    private enum Tone { OK, DIM, AMBER, RED, FIGHT, ACCENT }

    private record LogEntry(long seq, long whenMs, String tag, String text, Tone tone) {}

    private enum Kind { BAR, PIPS, DOTS, PULSE }

    private record StepInfo(
        String label, String shortWord, SettingColor labelColor,
        Kind kind, double fraction,
        String valueText, String valueTemplate,
        Integer rangeFrom, Integer rangeTo,
        int pillsFilled, int pillsTotal,
        SettingColor barColor, String note
    ) {}

    private record DistResult(double fraction, String valueText) {}

    private enum Outcome { DONE, ABANDONED, FAILED, CRUISE }

    private record RunSummary(int number, RunRecord run, int chests, int shulkers, Outcome outcome) {}

    private record ConsoleView(
        String label, Color titleColor, Tone tone, String detail, String chrono,
        String runTag, String runTotal,
        Kind kind, double fraction, int pillsFilled, int pillsTotal, Color barColor, String barKey,
        List<LogEntry> log, List<RunSummary> runs, double newestReveal,
        int rooms, int spawners, int chests, int shulkers, String avgRun, String perHour, String activeText
    ) {}

    private final List<Segment> runSegments = new ArrayList<>();
    private final List<RunRecord> history = new ArrayList<>();
    private final List<RunSummary> runSummaries = new ArrayList<>();
    private int runStartRooms;
    private int runStartChests;
    private int runStartShulkers;
    private boolean runCruise;
    private long summaryAddedMs;
    private String consoleBarKey = "";
    private double consoleBarShown;
    private int doneRunCount;
    private long doneRunSumMs;
    private long activeMs;
    private long lastLiveFrameMs;
    private double frameDt = 0.016;

    private String consoleLabel = "";
    private Color consoleLabelColor = new Color(255, 255, 255, 255);
    private String consoleOutLabel = "";
    private Color consoleOutLabelColor = new Color(255, 255, 255, 255);
    private long consoleLabelChangeMs;
    private final long[] groupAvgSum = new long[7];
    private final int[] groupAvgCount = new int[7];

    private Object lastSiteRef;
    private long runStartMs;
    private int currentGroup = -1;
    private boolean runFailedCap;
    private Insertion insertion;

    private String lastStateName = "";
    private long stepSinceMs;
    private double gaugeRefY;
    private double gaugeRefDist = Double.NaN;

    private boolean wasLive;
    private long moduleActivatedMs;
    private long lastRunEndMs;

    private final List<LogEntry> consoleLog = new ArrayList<>();
    private long logSeq;
    private int runIndex;
    private boolean combatPauseLogged;
    private long lastBottomLogKey;
    private long logSlideStartMs;

    private String displayedWord = "";
    private SettingColor displayedWordColor;
    private String outgoingWord = "";
    private SettingColor outgoingWordColor;
    private long wordChangeMs;

    private double lastPosX, lastPosY, lastPosZ;
    private long lastMovedMs;
    private boolean havePos;

    private final HudPulse.Tracker pulse = new HudPulse.Tracker();
    private long lastFrameNanos;
    private double animTime;

    public SpawnerRunHud() {
        super(INFO);
        displayedWordColor = new SettingColor(255, 255, 255, 255);
        outgoingWordColor = new SettingColor(255, 255, 255, 255);
    }

    @Override
    public void render(HudRenderer renderer) {
        long nowNanos = System.nanoTime();
        double dt = lastFrameNanos == 0L ? 0.016 : Math.min(0.25, (nowNanos - lastFrameNanos) / 1_000_000_000.0);
        lastFrameNanos = nowNanos;
        animTime += dt;
        frameDt = dt;

        SpawnerDetector detector = Modules.get().get(SpawnerDetector.class);
        boolean live = MeteorClient.mc.player != null && MeteorClient.mc.world != null
            && detector != null && detector.isActive();

        if (!live) {
            wasLive = false;
            lastLiveFrameMs = 0L;
            if (detector != null && runStartMs != 0L) {
                finishRun(detector, System.currentTimeMillis(), false);
                lastSiteRef = null;
            }
            if (isInEditor()) {
                renderEditorDemo(renderer);
                return;
            }
            setSize(0.0, 0.0);
            return;
        }

        long now = System.currentTimeMillis();
        if (!wasLive) {
            moduleActivatedMs = now;
            wasLive = true;
        }
        if (lastLiveFrameMs != 0L) activeMs += Math.min(2_000L, Math.max(0L, now - lastLiveFrameMs));
        lastLiveFrameMs = now;

        Object site = detector.getAutoSite();
        String stateName = detector.getAutoStateName();
        boolean idleNow = "IDLE".equals(stateName) && site == null;

        updateRun(detector, site, stateName, now);
        insertion = updateInsertion(now);

        StepInfo step = computeStep(detector, stateName);
        updateWordDisplay(step, now);
        trackCombatPauseLog(step, now);

        long elapsed = idleNow
            ? now - (lastRunEndMs != 0L ? lastRunEndMs : moduleActivatedMs)
            : now - stepSinceMs;

        switch (style.get()) {
            case CONSOLE -> renderConsole(renderer, detector, stateName, step, elapsed, now);
            case TIMELINE -> renderTimeline(renderer, detector, stateName, step, elapsed, now);
            case CARD -> renderCard(renderer, detector, stateName, step, elapsed, now);
            case LINE -> renderLine(renderer, detector, stateName, step, elapsed, now);
        }
    }

    private Insertion updateInsertion(long now) {
        if (insertion == null) return null;
        return now - insertion.startedMs < INSERT_ANIM_MS ? insertion : null;
    }


    private void updateRun(SpawnerDetector detector, Object site, String stateName, long now) {
        if (site != lastSiteRef) {
            if (lastSiteRef != null) finishRun(detector, now, site != null && !runCruise && !detector.isAutoSoloTransitOnly());
            if (site != null) {
                runSegments.clear();
                runStartMs = now;
                currentGroup = -1;
                runFailedCap = false;
                lastStateName = "";
                runIndex++;
                combatPauseLogged = false;
                runStartRooms = detector.getStatRoomsFarmed();
                runStartChests = detector.getStatChestsBroken();
                runStartShulkers = detector.getStatShulkersFound();
                runCruise = detector.isAutoSoloTransitOnly();
                pushLog(now, "RUN " + runIndex, runCruise ? "cruise leg" : "spawner found", Tone.ACCENT);
            }
            lastSiteRef = site;
        } else if (site == null && (runStartMs != 0L || !runSegments.isEmpty())) {
            finishRun(detector, now, false);
        }

        if (site == null) return;

        runCruise = runCruise && detector.isAutoSoloTransitOnly();

        boolean failedNow = "FAILED".equals(stateName);
        int group = groupOf(stateName);
        boolean stateChanged = !stateName.equals(lastStateName);

        if (failedNow) {
            if (!runFailedCap) {
                closeCurrentSegment(now);
                runFailedCap = true;
                pushLog(now, "×", "FAILED", Tone.RED);
            }
        } else if (group >= 0 && group != currentGroup) {
            if (currentGroup >= 0 && !runSegments.isEmpty()) {
                Segment closing = runSegments.get(runSegments.size() - 1);
                pushPhaseEndLog(closing.group, closing.durationMs(now), detector, now);
            }
            closeCurrentSegment(now);
            runSegments.add(new Segment(group, now));
            currentGroup = group;
        }

        if (stateChanged) {
            stepSinceMs = now;
            gaugeRefDist = Double.NaN;
            if ("CLIMB".equals(stateName)) gaugeRefY = MeteorClient.mc.player.getY();
            lastStateName = stateName;
        }

        double px = MeteorClient.mc.player.getX(), py = MeteorClient.mc.player.getY(), pz = MeteorClient.mc.player.getZ();
        if (!havePos) {
            lastPosX = px;
            lastPosY = py;
            lastPosZ = pz;
            lastMovedMs = now;
            havePos = true;
        } else if (Math.abs(px - lastPosX) + Math.abs(py - lastPosY) + Math.abs(pz - lastPosZ) > 0.001) {
            double moved = Math.sqrt((px - lastPosX) * (px - lastPosX) + (py - lastPosY) * (py - lastPosY) + (pz - lastPosZ) * (pz - lastPosZ));
            if (moved > 0.5) {
                lastPosX = px;
                lastPosY = py;
                lastPosZ = pz;
                lastMovedMs = now;
            }
        }
    }

    private void closeCurrentSegment(long now) {
        if (runSegments.isEmpty()) return;
        Segment last = runSegments.get(runSegments.size() - 1);
        if (last.endMs < 0L) {
            last.endMs = now;
            long dur = last.durationMs(now);
            if (dur > 0L) {
                groupAvgSum[last.group] += dur;
                groupAvgCount[last.group]++;
            }
        }
    }

    private void finishRun(SpawnerDetector detector, long now, boolean chained) {
        if (currentGroup >= 0 && !runFailedCap && !runSegments.isEmpty()) {
            Segment last = runSegments.get(runSegments.size() - 1);
            if (last.endMs < 0L) pushPhaseEndLog(last.group, last.durationMs(now), detector, now);
        }
        closeCurrentSegment(now);
        List<Frozen> frozen = new ArrayList<>();
        for (Segment s : runSegments) frozen.add(new Frozen(s.group, s.durationMs(now)));
        long total = Math.max(1L, now - runStartMs);
        RunRecord record = new RunRecord(frozen, total, runFailedCap);
        if (!runSegments.isEmpty()) {
            history.add(0, record);
            while (history.size() > 8) history.remove(history.size() - 1);
            insertion = new Insertion(frozen, total, runFailedCap, now);
        }
        if (runStartMs != 0L) {
            int chests = Math.max(0, detector.getStatChestsBroken() - runStartChests);
            int shulkers = Math.max(0, detector.getStatShulkersFound() - runStartShulkers);
            boolean farmed = chained || detector.getStatRoomsFarmed() > runStartRooms;
            Outcome outcome = runFailedCap ? Outcome.FAILED : farmed ? Outcome.DONE : runCruise ? Outcome.CRUISE : Outcome.ABANDONED;
            runSummaries.add(0, new RunSummary(runIndex, record, chests, shulkers, outcome));
            while (runSummaries.size() > 8) runSummaries.remove(runSummaries.size() - 1);
            summaryAddedMs = now;
            if (outcome == Outcome.DONE) {
                doneRunCount++;
                doneRunSumMs += total;
            }
            if (outcome != Outcome.FAILED) pushLog(now, "RUN " + runIndex, describeRunEnd(outcome, total, chests, shulkers), runEndTone(outcome));
        }
        runSegments.clear();
        runStartMs = 0L;
        currentGroup = -1;
        runFailedCap = false;
        lastRunEndMs = now;
    }

    private SettingColor phaseColor(int group) {
        return switch (group) {
            case G_VOL -> volColor.get();
            case G_POSE -> poseColor.get();
            case G_CREUSE -> creuseColor.get();
            case G_SALLE -> salleColor.get();
            case G_SORTIE -> sortieColor.get();
            case G_MONTEE -> monteeColor.get();
            default -> relaisColor.get();
        };
    }

    private long usualMs(int group) {
        return groupAvgCount[group] > 0 ? groupAvgSum[group] / groupAvgCount[group] : DEFAULT_AVG_MS[group];
    }


    private DistResult distanceResult(Double distance) {
        if (distance == null) return new DistResult(-1.0, "");
        if (Double.isNaN(gaugeRefDist) || gaugeRefDist < distance) gaugeRefDist = Math.max(1.0, distance);
        double eased = pulse.ease("gaugeDist", distance, 0.016, 0.3);
        double fraction = gaugeRefDist > 0.0 ? MathClampD(1.0 - eased / gaugeRefDist, 0.0, 1.0) : 1.0;
        return new DistResult(fraction, fmtDist(eased));
    }

    private StepInfo computeStep(SpawnerDetector detector, String stateName) {
        var player = MeteorClient.mc.player;
        int group = groupOf(stateName);
        SettingColor barColor = group >= 0 ? phaseColor(group) : mutedColor.get();

        String label = stateName;
        String shortWord = stateName.length() > 8 ? stateName.substring(0, 8) : stateName;
        Kind kind = Kind.PULSE;
        double fraction = 0.0;
        String valueText = "";
        String valueTemplate = "";
        Integer rangeFrom = null;
        Integer rangeTo = null;
        int pillsFilled = 0;
        int pillsTotal = 0;

        switch (stateName) {
            case "IDLE" -> {
                label = "SEARCHING";
                shortWord = "SEARCH";
            }
            case "PREPARE" -> {
                label = "PREPARING";
                shortWord = "FLY";
            }
            case "APPROACH", "FINAL" -> {
                label = "APPROACH".equals(stateName) ? "APPROACHING" : "FINAL APPROACH";
                shortWord = "FLY";
                Double dd = detector.hasAutoColumn()
                    ? hdist(player.getX(), player.getZ(), detector.getAutoColumnX() + 0.5, detector.getAutoColumnZ() + 0.5)
                    : null;
                DistResult dr = distanceResult(dd);
                if (dr.fraction() >= 0.0) {
                    kind = Kind.BAR;
                    fraction = dr.fraction();
                    valueText = dr.valueText();
                    valueTemplate = DIST_TEMPLATE;
                }
            }
            case "WALK" -> {
                label = "WALKING";
                shortWord = "LAND";
                if (player.isTouchingWater() && detector.hasAutoColumn()) {
                    double dist = hdist(player.getX(), player.getZ(), detector.getAutoColumnX() + 0.5, detector.getAutoColumnZ() + 0.5);
                    if (dist > 0.5) {
                        label = "SWIMMING TO HOLE";
                        shortWord = "SWIM";
                        DistResult dr = distanceResult(dist);
                        if (dr.fraction() >= 0.0) {
                            kind = Kind.BAR;
                            fraction = dr.fraction();
                            valueText = dr.valueText();
                            valueTemplate = DIST_TEMPLATE;
                        }
                    } else {
                        label = "SINKING";
                        shortWord = "SINK";
                        rangeFrom = (int) Math.round(player.getY());
                        rangeTo = detector.getAutoColumnStartY();
                    }
                } else {
                    BlockPos rim = detector.getAutoColumnRim();
                    Double dd = rim != null ? hdist(player.getX(), player.getZ(), rim.getX() + 0.5, rim.getZ() + 0.5)
                        : detector.hasAutoColumn() ? hdist(player.getX(), player.getZ(), detector.getAutoColumnX() + 0.5, detector.getAutoColumnZ() + 0.5) : null;
                    DistResult dr = distanceResult(dd);
                    if (dr.fraction() >= 0.0) {
                        kind = Kind.BAR;
                        fraction = dr.fraction();
                        valueText = dr.valueText();
                        valueTemplate = DIST_TEMPLATE;
                    }
                }
            }
            case "DIG" -> {
                int startY = detector.getAutoColumnStartY();
                int exitY = detector.getAutoColumnExitY();
                int planned = Math.max(1, detector.getAutoDigBlocksPlanned());
                int done = detector.hasAutoColumn() ? MathHelperClamp((int) Math.round(startY - player.getY()), 0, planned) : 0;
                label = "DIGGING";
                shortWord = "DIG";
                kind = Kind.BAR;
                fraction = done / (double) planned;
                valueText = done + "/" + planned;
                valueTemplate = FRACTION_TEMPLATE;
                rangeFrom = startY;
                rangeTo = exitY;
            }
            case "SIDE_DIG" -> {
                label = "SIDE DIGGING";
                shortWord = "DIG";
            }
            case "TUNNEL" -> {
                label = "TUNNELING";
                shortWord = "DIG";
                DistResult dr = distanceResult(tunnelTargetDist(detector, player));
                if (dr.fraction() >= 0.0) {
                    kind = Kind.BAR;
                    fraction = dr.fraction();
                    valueText = dr.valueText();
                    valueTemplate = DIST_TEMPLATE;
                }
            }
            case "ROOM" -> {
                label = "IN ROOM";
                shortWord = "ROOM";
                int total = detector.getAutoRoomContainerTotal();
                int idx = detector.getAutoRoomContainerIndex();
                if (total > 0) {
                    kind = Kind.PIPS;
                    fraction = MathClampD(idx / (double) total, 0.0, 1.0);
                    valueText = idx + "/" + total;
                    valueTemplate = FRACTION_TEMPLATE;
                    pillsFilled = MathHelperClamp(idx, 0, total);
                    pillsTotal = total;
                }
            }
            case "ROOM_CLIMB" -> {
                label = "ROOM CLIMB";
                shortWord = "ROOM";
                int count = detector.getAutoRoomClimbBaritoneCandidateCount();
                if (count > 0) {
                    kind = Kind.DOTS;
                    pillsFilled = MathHelperClamp(detector.getAutoRoomClimbBaritoneIndex() + 1, 0, count);
                    pillsTotal = count;
                    valueText = "retry " + pillsFilled + "/" + pillsTotal;
                    valueTemplate = RETRY_TEMPLATE;
                }
            }
            case "CLIMB" -> {
                label = "CLIMBING";
                shortWord = "CLIMB";
                if (detector.isAutoClimbStuck() || detector.getAutoClimbStuckAttempts() > 0) {
                    kind = Kind.DOTS;
                    pillsFilled = MathHelperClamp(detector.getAutoClimbStuckAttempts(), 0, 3);
                    pillsTotal = 3;
                    valueText = "retry " + pillsFilled + "/" + pillsTotal;
                    valueTemplate = RETRY_TEMPLATE;
                } else {
                    double targetY = detector.getAutoClimbTargetY();
                    double span = targetY - gaugeRefY;
                    double f = span > 0.1 ? MathClampD((player.getY() - gaugeRefY) / span, 0.0, 1.0) : 1.0;
                    int remaining = Math.max(0, (int) Math.round(targetY - player.getY()));
                    kind = Kind.BAR;
                    fraction = f;
                    valueText = fmtDist(remaining);
                    valueTemplate = DIST_TEMPLATE;
                    rangeFrom = (int) Math.round(gaugeRefY);
                    rangeTo = (int) Math.round(targetY);
                }
            }
            case "SHAFT_ALIGN", "SHAFT_FLY" -> {
                label = "SHAFT_ALIGN".equals(stateName) ? "ALIGNING SHAFT" : "SHAFT FLYING";
                shortWord = "EXIT";
                kind = Kind.DOTS;
                pillsFilled = MathHelperClamp(detector.getAutoShaftFlyRerouteAttempts(), 0, 5);
                pillsTotal = 5;
                valueText = "retry " + pillsFilled + "/" + pillsTotal;
                valueTemplate = RETRY_TEMPLATE;
            }
            case "WATER_JUMP" -> {
                label = "WATER JUMP";
                shortWord = "EXIT";
                kind = Kind.DOTS;
                pillsFilled = MathHelperClamp(detector.getAutoWaterJumpAttempts(), 0, 3);
                pillsTotal = 3;
                valueText = "retry " + pillsFilled + "/" + pillsTotal;
                valueTemplate = RETRY_TEMPLATE;
            }
            case "TAKEOFF" -> {
                label = "TAKING OFF";
                shortWord = "EXIT";
            }
            case "EXIT" -> {
                label = "EXITING";
                shortWord = "EXIT";
                DistResult dr = distanceResult(exitTargetDist(detector, player));
                if (dr.fraction() >= 0.0) {
                    kind = Kind.BAR;
                    fraction = dr.fraction();
                    valueText = dr.valueText();
                    valueTemplate = DIST_TEMPLATE;
                }
            }
            case "SWIM_UP" -> {
                label = "SWIMMING UP";
                shortWord = "EXIT";
                DistResult dr = distanceResult(swimUpTargetDist(detector, player));
                if (dr.fraction() >= 0.0) {
                    kind = Kind.BAR;
                    fraction = dr.fraction();
                    valueText = dr.valueText();
                    valueTemplate = DIST_TEMPLATE;
                }
            }
            case "ASCEND" -> {
                label = "ASCENDING";
                shortWord = "EXIT";
                DistResult dr = distanceResult(ascendTargetDist(detector, player));
                if (dr.fraction() >= 0.0) {
                    kind = Kind.BAR;
                    fraction = dr.fraction();
                    valueText = dr.valueText();
                    valueTemplate = DIST_TEMPLATE;
                }
            }
            case "TAKEOFF_SPOT" -> {
                label = "FINDING TAKEOFF";
                shortWord = "EXIT";
                BlockPos spot = detector.getAutoTakeoffSpot();
                Double dd = spot == null ? null : hdist(player.getX(), player.getZ(), spot.getX() + 0.5, spot.getZ() + 0.5);
                DistResult dr = distanceResult(dd);
                if (dr.fraction() >= 0.0) {
                    kind = Kind.BAR;
                    fraction = dr.fraction();
                    valueText = dr.valueText();
                    valueTemplate = DIST_TEMPLATE;
                }
            }
            case "OBSIDIAN_PLATFORM" -> {
                label = "BUILDING PLATFORM";
                shortWord = "EXIT";
                int attempts = detector.getAutoObsidianAttempts();
                if (attempts > 0) {
                    kind = Kind.DOTS;
                    pillsFilled = MathHelperClamp(attempts, 0, 3);
                    pillsTotal = 3;
                    valueText = "retry " + pillsFilled + "/" + pillsTotal;
                    valueTemplate = RETRY_TEMPLATE;
                } else {
                    BlockPos target = detector.getAutoObsidianTarget();
                    Double dd = target == null ? null : hdist(player.getX(), player.getZ(), target.getX() + 0.5, target.getZ() + 0.5);
                    DistResult dr = distanceResult(dd);
                    if (dr.fraction() >= 0.0) {
                        kind = Kind.BAR;
                        fraction = dr.fraction();
                        valueText = dr.valueText();
                        valueTemplate = DIST_TEMPLATE;
                    }
                }
            }
            case "RESUME" -> {
                label = "HANDING OFF";
                shortWord = "HANDOFF";
            }
            default -> {
            }
        }

        boolean failedNow = "FAILED".equals(stateName);
        SettingColor labelColor = failedNow ? badColor.get() : barColor;
        String note = "";

        if (!failedNow && isPausableState(stateName) && detector.isAutoPaused()) {
            String reason = detector.getAutoPauseReason();
            boolean combat = "combat".equalsIgnoreCase(reason);
            if (combat) {
                label = "FIGHTING";
                shortWord = "FIGHT";
                labelColor = amberColor.get();
                Entity target = detector.getAutoShaftChaseTarget();
                if (target != null && target.isAlive()) {
                    double dist = hdist(player.getX(), player.getZ(), target.getX(), target.getZ());
                    note = "chasing skeleton · " + fmtDist(dist);
                }
            } else if (reason != null && !reason.isEmpty()) {
                label = reason.toUpperCase(Locale.ROOT);
                shortWord = label.length() > 8 ? label.substring(0, 8) : label;
                labelColor = mutedColor.get();
            } else {
                label = "PAUSED";
                shortWord = "PAUSED";
                labelColor = mutedColor.get();
            }
        }

        return new StepInfo(label, shortWord, labelColor, kind, fraction, valueText, valueTemplate,
            rangeFrom, rangeTo, pillsFilled, pillsTotal, barColor, note);
    }

    private void updateWordDisplay(StepInfo step, long now) {
        if (!step.shortWord().equals(displayedWord)) {
            outgoingWord = displayedWord;
            outgoingWordColor = displayedWordColor;
            displayedWord = step.shortWord();
            wordChangeMs = now;
        }
        displayedWordColor = step.labelColor();
    }

    private Tone chronoTone(SpawnerDetector d, String state, long elapsed, long now) {
        if ("IDLE".equals(state)) return Tone.DIM;
        boolean failedNow = "FAILED".equals(state);
        boolean exhausted = isExhausted(d, state);
        boolean immobile = now - lastMovedMs > IMMOBILE_MS && currentGroup >= 0;
        long avg = currentGroup >= 0 ? usualMs(currentGroup) : DEFAULT_AVG_MS[0];
        boolean slow = elapsed > 2L * avg;
        return failedNow || exhausted ? Tone.RED : immobile || slow ? Tone.AMBER : Tone.OK;
    }

    private SettingColor chronoColor(SpawnerDetector d, String state, long elapsed, long now) {
        Tone t = chronoTone(d, state, elapsed, now);
        return t == Tone.RED ? badColor.get() : t == Tone.AMBER ? amberColor.get() : valueColor.get();
    }

    private Tone consoleTone(SpawnerDetector d, String state, StepInfo step, long elapsed, long now) {
        if ("FAILED".equals(state)) return Tone.RED;
        if ("FIGHTING".equals(step.label())) return Tone.FIGHT;
        if ("IDLE".equals(state)) return Tone.DIM;
        return chronoTone(d, state, elapsed, now);
    }

    private SettingColor toneColor(Tone t) {
        return switch (t) {
            case RED -> badColor.get();
            case AMBER -> amberColor.get();
            case DIM -> mutedColor.get();
            case FIGHT -> fightColor.get();
            case ACCENT -> accentColor.get();
            default -> valueColor.get();
        };
    }

    private SettingColor dotColor(Tone t) {
        return switch (t) {
            case AMBER -> amberColor.get();
            case RED -> badColor.get();
            case DIM -> mutedColor.get();
            default -> accentColor.get();
        };
    }

    private boolean dotBlinks(Tone t) {
        return t == Tone.OK || t == Tone.FIGHT;
    }

    private void trackCombatPauseLog(StepInfo step, long now) {
        if ("FIGHTING".equals(step.label())) {
            if (!combatPauseLogged) {
                pushLog(now, "PAUSE", "fighting · skeleton", Tone.FIGHT);
                combatPauseLogged = true;
            }
        } else {
            combatPauseLogged = false;
        }
    }

    private void pushLog(long whenMs, String tag, String text, Tone tone) {
        consoleLog.add(new LogEntry(++logSeq, whenMs, tag, text, tone));
        while (consoleLog.size() > 24) consoleLog.remove(0);
    }

    private void pushPhaseEndLog(int group, long durationMs, SpawnerDetector d, long now) {
        pushLog(now, GROUP_WORD[group], describePhaseEnd(group, d) + " · " + formatSpan(durationMs), Tone.DIM);
    }

    private static String describeRunEnd(Outcome outcome, long totalMs, int chests, int shulkers) {
        return switch (outcome) {
            case DONE -> "done · " + formatSpan(totalMs) + " · " + plural(chests, "chest")
                + (shulkers > 0 ? " · " + plural(shulkers, "shulker") : "");
            case ABANDONED -> "abandoned · " + formatSpan(totalMs);
            case CRUISE -> "cruise leg over · " + formatSpan(totalMs);
            default -> "failed · " + formatSpan(totalMs);
        };
    }

    private static Tone runEndTone(Outcome outcome) {
        return switch (outcome) {
            case DONE -> Tone.ACCENT;
            case ABANDONED -> Tone.AMBER;
            case FAILED -> Tone.RED;
            default -> Tone.DIM;
        };
    }

    private static String plural(int n, String word) {
        return n + " " + word + (n == 1 ? "" : "s");
    }

    private String describePhaseEnd(int group, SpawnerDetector d) {
        return switch (group) {
            case G_VOL -> "landed";
            case G_POSE -> "walked to hole";
            case G_CREUSE -> "y " + fmtY(d.getAutoColumnStartY()) + " " + ARROW + " " + fmtY(d.getAutoColumnExitY());
            case G_SALLE -> "chest " + d.getAutoRoomContainerIndex() + "/" + d.getAutoRoomContainerTotal();
            case G_SORTIE -> "shaft fly";
            case G_MONTEE -> "climb done";
            default -> "handed off";
        };
    }

    private String composeConsoleDetail(String stateName, StepInfo step, Tone tone, boolean idleNow, long elapsed, long now) {
        if (idleNow) return lastRunEndMs != 0L ? "last run " + formatSpan(elapsed) + " ago" : "no run yet";
        if (!step.note().isEmpty()) return step.note();
        StringBuilder sb = new StringBuilder();
        if (step.rangeFrom() != null) sb.append("y ").append(fmtY(step.rangeFrom())).append(' ').append(ARROW).append(' ').append(fmtY(step.rangeTo()));
        if (step.kind() == Kind.PIPS && step.pillsTotal() > 0) {
            if (sb.length() > 0) sb.append(" · ");
            int pct = (int) Math.round(100.0 * step.fraction());
            sb.append("chest ").append(step.valueText()).append(" · ").append(pct).append('%');
        } else if (step.kind() == Kind.DOTS) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("try ").append(step.pillsFilled()).append('/').append(step.pillsTotal());
            if (tone == Tone.AMBER) {
                long stalledS = Math.max(0L, (now - lastMovedMs) / 1000L);
                sb.append(" · no progress ").append(stalledS).append(" s");
            }
        } else if (!step.valueText().isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(step.valueText());
            if ("DIG".equals(stateName)) sb.append(" blocks");
        }
        return sb.toString();
    }

    private String buildStuckLogText(StepInfo step, long now) {
        long stalledS = Math.max(0L, (now - lastMovedMs) / 1000L);
        if (step.kind() == Kind.DOTS && step.pillsTotal() > 0) {
            return "no progress " + stalledS + " s · still try " + step.pillsFilled() + "/" + step.pillsTotal();
        }
        return "no progress " + stalledS + " s";
    }

    private List<LogEntry> buildDisplayLog(StepInfo step, Tone tone, long now) {
        LogEntry live = tone == Tone.AMBER ? new LogEntry(-2L, now, "!", buildStuckLogText(step, now), Tone.AMBER) : null;
        int show = Math.min(logLines.get(), consoleLog.size() + (live != null ? 1 : 0));
        List<LogEntry> out = new ArrayList<>(show);
        int fromPersisted = live != null ? show - 1 : show;
        int start = Math.max(0, consoleLog.size() - fromPersisted);
        for (int i = start; i < consoleLog.size(); i++) out.add(consoleLog.get(i));
        if (live != null) out.add(live);
        return out;
    }

    private static String formatClock(long epochMs) {
        var t = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalTime();
        return String.format(Locale.ROOT, "%02d:%02d:%02d", t.getHour(), t.getMinute(), t.getSecond());
    }

    private boolean isExhausted(SpawnerDetector d, String state) {
        return switch (state) {
            case "SHAFT_ALIGN", "SHAFT_FLY" -> d.getAutoShaftFlyRerouteAttempts() >= 5;
            case "WATER_JUMP" -> d.getAutoWaterJumpAttempts() >= 3;
            case "CLIMB" -> d.getAutoClimbStuckAttempts() >= 3;
            case "OBSIDIAN_PLATFORM" -> d.getAutoObsidianAttempts() >= 3;
            default -> false;
        };
    }

    private static Color faded(Color c, float alpha) {
        return new Color(c.r, c.g, c.b, (int) (c.a * MathClamp01(alpha)));
    }

    private static float MathClamp01(float v) {
        return v < 0.0f ? 0.0f : v > 1.0f ? 1.0f : v;
    }


    private static final double ROW1_H = 12.0;
    private static final double GAUGE_W = 66.0;
    private static final double GAP = 3.0;
    private static final double ROW2_H = 11.0;
    private static final double HIST_H = 2.0;
    private static final double HIST_GAP = 1.0;
    private static final double MIN_BAND_W = 200.0;
    private static final double CARD_LABEL_ROW_H = 12.0;
    private static final double CARD_BAR_H = 4.0;
    private static final double CARD_PIP_ROW_H = 10.0;
    private static final double MIN_CARD_W = 150.0;
    private static final double LINE_ROW_H = 12.0;
    private static final double LINE_BAR_H = 3.0;
    private static final double MIN_LINE_W = 160.0;

    private static final double CONSOLE_STRIPE_W = 2.0;
    private static final double CONSOLE_DOT_R = 2.4;
    private static final double CONSOLE_TITLE_SCALE = 1.15;
    private static final double CONSOLE_TEXT_SCALE = 0.85;
    private static final double CONSOLE_CLOCK_SCALE = 0.75;
    private static final double CONSOLE_MICRO_SCALE = 0.7;
    private static final double CONSOLE_BAR_H = 2.0;
    private static final double CONSOLE_ROW_H = 12.0;
    private static final double CONSOLE_SECTION_GAP = 6.0;
    private static final double CONSOLE_TRACKING = 0.8;
    private static final double MIN_CONSOLE_W = 270.0;
    private static final double CONSOLE_DETAIL_MIN_W = 110.0;
    private static final double CONSOLE_LOG_TEXT_MIN_W = 130.0;
    private static final double CONSOLE_HIST_BAND_MIN_W = 48.0;
    private static final double CONSOLE_BLINK_S = 1.6;
    private static final double CONSOLE_SHIMMER_S = 1.8;
    private static final long CONSOLE_LOG_SLIDE_MS = 350L;
    private static final long CONSOLE_LOG_FLASH_MS = 1400L;
    private static final long CONSOLE_LABEL_ANIM_MS = 260L;
    private static final long CONSOLE_VALUE_FLASH_MS = 1200L;
    private static final String ARROW = "→";
    private static final String[] CONSOLE_LOG_TAGS = {"FLY", "LAND", "DIG", "ROOM", "EXIT", "CLIMB", "HANDOFF", "PAUSE", "RUN 999", "!", "×"};
    private static final String CLOCK_TEMPLATE = "00:00:00";
    private static final String RUN_INFO_TEMPLATE = "RUN 999 · 00:00";
    private static final String RUN_NUM_TEMPLATE = "#999";
    private static final String CHESTS_TEMPLATE = "99 chests";
    private static final String SHULKERS_TEMPLATE = "99 shulkers";
    private static final String ACTIVE_TEMPLATE = "99h 59m active";
    private static final String TILE_VALUE_TEMPLATE = "8888";
    private static final String[] OUTCOME_WORDS = {"DONE", "ABANDONED", "FAILED", "CRUISE"};
    private static final String[] TILE_LABELS = {"ROOMS", "SPAWNERS", "CHESTS", "SHULKERS", "AVG RUN", "ROOMS/H"};


    private double maxTextWidth(HudRenderer renderer, String[] arr, boolean shadow, double s) {
        double m = 0.0;
        for (String w : arr) m = Math.max(m, renderer.textWidth(w, shadow, s));
        return m;
    }

    private void drawClipped(HudRenderer renderer, String text, double x, double y, double maxWidth, Color color, boolean shadow, double s) {
        String t = text;
        while (!t.isEmpty() && renderer.textWidth(t, shadow, s) > maxWidth) {
            t = t.substring(0, t.length() - 1);
        }
        if (!t.isEmpty()) renderer.text(t, x, y, color, shadow, s);
    }


    private void renderConsole(HudRenderer renderer, SpawnerDetector detector, String stateName, StepInfo step, long elapsed, long now) {
        boolean idleNow = "IDLE".equals(stateName) && detector.getAutoSite() == null;
        Tone tone = consoleTone(detector, stateName, step, elapsed, now);
        String detail = composeConsoleDetail(stateName, step, tone, idleNow, elapsed, now);
        boolean inRun = runStartMs > 0L;
        double reveal = animations.get()
            ? smoothstep((now - summaryAddedMs) / (double) INSERT_ANIM_MS)
            : 1.0;
        String avgRun = doneRunCount > 0 ? formatSpan(doneRunSumMs / doneRunCount) : "--";
        String perHour = doneRunCount > 0 && activeMs >= 60_000L
            ? String.format(Locale.ROOT, "%.1f", doneRunCount * 3_600_000.0 / activeMs)
            : "--";

        ConsoleView view = new ConsoleView(
            step.label(), consoleTitleColor(tone, step), tone, detail, formatSpan(elapsed),
            inRun ? "RUN " + runIndex : "", inRun ? formatSpan(now - runStartMs) : "",
            step.kind(), step.fraction(), step.pillsFilled(), step.pillsTotal(), step.barColor(),
            runIndex + ":" + stepSinceMs + ":" + step.label(),
            buildDisplayLog(step, tone, now), runSummaries, reveal,
            detector.getStatRoomsFarmed(), detector.getStatSpawnersBroken(), detector.getStatChestsBroken(), detector.getStatShulkersFound(),
            avgRun, perHour, formatActive(activeMs));
        drawConsole(renderer, view, now);
    }

    private Color consoleTitleColor(Tone tone, StepInfo step) {
        return switch (tone) {
            case RED -> badColor.get();
            case FIGHT -> fightColor.get();
            case DIM -> valueColor.get();
            default -> step.labelColor();
        };
    }

    private void updateConsoleLabel(String label, Color color, long now) {
        if (!label.equals(consoleLabel)) {
            consoleOutLabel = consoleLabel;
            consoleOutLabelColor = consoleLabelColor;
            consoleLabel = label;
            consoleLabelChangeMs = now;
        }
        consoleLabelColor = color;
    }

    private void drawConsole(HudRenderer renderer, ConsoleView v, long now) {
        boolean shadow = textShadow.get();
        boolean anim = animations.get();
        double s = size.get();
        double pad = padding.get();
        double gap = GAP * s;

        double titleScale = s * CONSOLE_TITLE_SCALE;
        double textScale = s * CONSOLE_TEXT_SCALE;
        double clockScale = s * CONSOLE_CLOCK_SCALE;
        double microScale = s * CONSOLE_MICRO_SCALE;
        double tracking = CONSOLE_TRACKING * s;

        double stripeW = CONSOLE_STRIPE_W * s;
        double dotR = CONSOLE_DOT_R * s;
        double rowH = CONSOLE_ROW_H * s;
        double barH = Math.max(1.0, CONSOLE_BAR_H * s);
        double sectionGap = CONSOLE_SECTION_GAP * s;
        double pillPadX = 3.0 * s;

        double titleH = renderer.textHeight(shadow, titleScale);
        double textH = renderer.textHeight(shadow, textScale);
        double microH = renderer.textHeight(shadow, microScale);
        double valueH = renderer.textHeight(shadow, s);
        double pillH = microH + 2.0 * s;

        double dotLeft = stripeW + 4.0 * s;
        double titleX = dotLeft + dotR * 2.0 + 5.0 * s;
        double titleW = maxTextWidth(renderer, CARD_LABELS, shadow, titleScale);
        double chronoW = renderer.textWidth(CHRONO_TEMPLATE, shadow, titleScale);
        double runInfoW = renderer.textWidth(RUN_INFO_TEMPLATE, shadow, textScale);
        double clockW = renderer.textWidth(CLOCK_TEMPLATE, shadow, clockScale);
        double tagW = maxTextWidth(renderer, CONSOLE_LOG_TAGS, shadow, microScale) + pillPadX * 2.0;
        double numW = renderer.textWidth(RUN_NUM_TEMPLATE, shadow, textScale);
        double durW = renderer.textWidth(CHRONO_TEMPLATE, shadow, textScale);
        double chestsW = renderer.textWidth(CHESTS_TEMPLATE, shadow, textScale);
        double shulkW = renderer.textWidth(SHULKERS_TEMPLATE, shadow, textScale);
        double badgeW = maxTextWidth(renderer, OUTCOME_WORDS, shadow, microScale) + pillPadX * 2.0;
        double activeW = renderer.textWidth(ACTIVE_TEMPLATE, shadow, microScale);
        double tileContentW = Math.max(
            Math.max(renderer.textWidth(TILE_VALUE_TEMPLATE, shadow, s), renderer.textWidth(CHRONO_TEMPLATE, shadow, s)),
            maxTrackedWidth(renderer, TILE_LABELS, shadow, microScale, tracking));

        boolean showHist = historyRuns.get() > 0;
        boolean showSession = sessionStats.get();
        int histShown = Math.min(historyRuns.get(), v.runs().size());
        int logRows = logLines.get();

        double headerNeed = titleX + Math.max(titleW + gap * 2.0 + chronoW, CONSOLE_DETAIL_MIN_W * s + gap * 2.0 + runInfoW);
        double logNeed = clockW + gap * 2.0 + tagW + gap * 2.0 + CONSOLE_LOG_TEXT_MIN_W * s;
        double histNeed = showHist ? numW + durW + chestsW + shulkW + badgeW + gap * 10.0 + CONSOLE_HIST_BAND_MIN_W * s : 0.0;
        double tilesNeed = showSession ? (tileContentW + gap * 2.0) * TILE_LABELS.length : 0.0;
        double width = Math.max(MIN_CONSOLE_W * s, Math.max(Math.max(headerNeed, logNeed), Math.max(histNeed, tilesNeed)));

        double line1H = Math.max(titleH, dotR * 2.0) + 3.0 * s;
        double line2H = textH + 2.0 * s;
        double headerH = 2.0 * s + line1H + line2H + 3.0 * s + barH + 2.0 * s;
        double captionH = microH + 4.0 * s;
        double tilesH = valueH + 2.0 * s + microH;
        double height = headerH
            + sectionGap + captionH + logRows * rowH
            + (showHist ? sectionGap + captionH + historyRuns.get() * rowH : 0.0)
            + (showSession ? sectionGap + captionH + tilesH : 0.0);

        double fullW = width + pad * 2.0;
        double fullH = height + pad * 2.0;
        double x0 = this.x + pad, y0 = this.y + pad;

        updateConsoleLabel(v.label(), v.titleColor(), now);
        boolean labelMoving = anim && !consoleOutLabel.isEmpty();
        double labelProgress = labelMoving ? smoothstep((now - consoleLabelChangeMs) / (double) CONSOLE_LABEL_ANIM_MS) : 1.0;
        Color headColor = labelProgress < 1.0 ? mix(consoleOutLabelColor, v.titleColor(), (float) labelProgress) : v.titleColor();

        if (background.get()) {
            renderer.quad(this.x, this.y, fullW, fullH, backgroundColor.get());
            Color tintL = faded(headColor, 0.22f);
            Color tintR = faded(headColor, 0.0f);
            renderer.quad(this.x, this.y, fullW, pad + headerH, tintL, tintR, tintR, tintL);
        }
        renderer.quad(x0, y0, stripeW, headerH - 2.0 * s, headColor);

        double line1Y = y0 + 2.0 * s;
        drawBeatDot(renderer, x0 + dotLeft + dotR, line1Y + line1H / 2.0, dotR, dotColor(v.tone()), anim && dotBlinks(v.tone()), s);

        double titleY = line1Y + (line1H - titleH) / 2.0;
        double titleAvail = Math.max(0.0, width - titleX - gap * 2.0 - chronoW);
        double slide = titleH * 0.6;
        double outSlide = Math.min(slide, Math.max(0.0, titleY - this.y));
        if (labelProgress < 1.0) {
            drawClipped(renderer, consoleOutLabel, x0 + titleX, titleY - labelProgress * outSlide, titleAvail,
                faded(consoleOutLabelColor, (float) (1.0 - labelProgress)), shadow, titleScale);
        }
        drawClipped(renderer, v.label(), x0 + titleX, titleY + (1.0 - labelProgress) * slide, titleAvail,
            faded(v.titleColor(), (float) labelProgress), shadow, titleScale);

        double chronoTextW = renderer.textWidth(v.chrono(), shadow, titleScale);
        renderer.text(v.chrono(), x0 + width - chronoTextW, titleY, toneColor(v.tone()), shadow, titleScale);

        double line2Y = line1Y + line1H;
        double detailY = line2Y + (line2H - textH) / 2.0;
        boolean hasRun = !v.runTag().isEmpty();
        double detailAvail = Math.max(0.0, width - titleX - (hasRun ? runInfoW + gap * 2.0 : 0.0));
        drawClipped(renderer, v.detail(), x0 + titleX, detailY, detailAvail, mutedColor.get(), shadow, textScale);
        if (hasRun) {
            String sep = " · ";
            double totalX = x0 + width - renderer.textWidth(v.runTotal(), shadow, textScale);
            double sepX = totalX - renderer.textWidth(sep, shadow, textScale);
            double tagX = sepX - renderer.textWidth(v.runTag(), shadow, textScale);
            renderer.text(v.runTag(), tagX, detailY, accentColor.get(), shadow, textScale);
            renderer.text(sep, sepX, detailY, faded(mutedColor.get(), 0.7f), shadow, textScale);
            renderer.text(v.runTotal(), totalX, detailY, valueColor.get(), shadow, textScale);
        }

        double barY = line2Y + line2H + 3.0 * s;
        drawConsoleProgress(renderer, v, x0 + dotLeft, barY, width - dotLeft, barH, s, anim);

        double y = y0 + headerH + sectionGap;
        drawCaption(renderer, "EVENTS", "", 0.0, x0, y, width, captionH, shadow, microScale, tracking, s);
        y += captionH;
        drawConsoleLog(renderer, v.log(), x0, y, width, rowH, clockW, tagW, pillH, this.y + fullH, shadow, textScale, clockScale, microScale, gap, s, anim, now);
        y += logRows * rowH;

        if (showHist) {
            y += sectionGap;
            drawCaption(renderer, "LAST RUNS", "", 0.0, x0, y, width, captionH, shadow, microScale, tracking, s);
            y += captionH;
            drawConsoleHistory(renderer, v.runs(), histShown, v.newestReveal(), x0, y, width, rowH,
                numW, durW, chestsW, shulkW, badgeW, pillH, this.y + fullH, shadow, textScale, microScale, gap, s);
            y += historyRuns.get() * rowH;
        }

        if (showSession) {
            y += sectionGap;
            drawCaption(renderer, "SESSION", v.activeText(), activeW, x0, y, width, captionH, shadow, microScale, tracking, s);
            y += captionH;
            drawConsoleTiles(renderer, v, x0, y, width, tilesH, shadow, s, microScale, tracking, gap, anim);
        }

        setSize(fullW, fullH);
    }

    private void drawBeatDot(HudRenderer renderer, double cx, double cy, double r, Color color, boolean beat, double s) {
        if (!beat) {
            disc(renderer, cx, cy, r, color);
            return;
        }
        double phase = (animTime / CONSOLE_BLINK_S) % 1.0;
        double second = phase - 0.22;
        double env = Math.max(0.0, 1.0 - phase * 5.0) + (second > 0.0 ? 0.6 * Math.max(0.0, 1.0 - second * 5.0) : 0.0);
        ring(renderer, cx, cy, r * (1.2 + 1.2 * phase), Math.max(0.8, 0.8 * s), faded(color, (float) (0.5 * (1.0 - phase))));
        disc(renderer, cx, cy, r * (1.0 + 0.22 * env), faded(color, (float) (0.8 + 0.2 * Math.min(1.0, env))));
    }

    private void drawConsoleProgress(HudRenderer renderer, ConsoleView v, double x, double y, double w, double h, double s, boolean anim) {
        Color track = faded(mutedColor.get(), 0.22f);
        Color fill = v.tone() == Tone.RED ? badColor.get() : v.barColor();
        int total = v.pillsTotal();
        boolean segmented = (v.kind() == Kind.PIPS || v.kind() == Kind.DOTS) && total > 0 && total <= 24;
        double segGap = Math.max(1.0, 1.5 * s);
        double segW = segmented ? (w - segGap * (total - 1)) / total : 0.0;
        if (segmented && segW >= 2.0) {
            int filled = MathHelperClamp(v.pillsFilled(), 0, total);
            float current = anim ? (float) (0.3 + 0.35 * (0.5 + 0.5 * Math.sin(animTime * 2.0 * Math.PI / 1.1))) : 0.45f;
            for (int i = 0; i < total; i++) {
                Color c = i < filled ? fill : i == filled && v.kind() == Kind.PIPS ? faded(fill, current) : track;
                renderer.quad(x + i * (segW + segGap), y, segW, h, c);
            }
            return;
        }
        renderer.quad(x, y, w, h, track);
        if (v.kind() == Kind.BAR || v.kind() == Kind.PIPS) {
            double f = MathClampD(v.fraction(), 0.0, 1.0);
            if (!anim || !v.barKey().equals(consoleBarKey)) {
                consoleBarKey = v.barKey();
                consoleBarShown = f;
            } else {
                consoleBarShown += (1.0 - Math.exp(-frameDt / 0.25)) * (f - consoleBarShown);
            }
            f = MathClampD(consoleBarShown, 0.0, 1.0);
            double fw = w * f;
            if (fw > 0.3) {
                renderer.quad(x, y, fw, h, fill);
                double headW = Math.min(fw, Math.max(1.0, s));
                renderer.quad(x + fw - headW, y, headW, h, faded(valueColor.get(), 0.85f));
            }
        } else if (anim) {
            double cometW = w * 0.22;
            double head = x - cometW + (w + cometW) * smoothstep((animTime / CONSOLE_SHIMMER_S) % 1.0);
            double a = Math.max(x, head), b = Math.min(x + w, head + cometW);
            if (b > a) {
                Color tail = faded(fill, 0.0f);
                Color tip = faded(fill, 0.9f);
                renderer.quad(a, y, b - a, h, tail, tip, tip, tail);
            }
        }
    }

    private void drawCaption(HudRenderer renderer, String caption, String right, double rightW, double x, double y, double w, double h,
                             boolean shadow, double scale, double tracking, double s) {
        double ty = y + (h - renderer.textHeight(shadow, scale)) / 2.0;
        double end = drawTracked(renderer, caption, x, ty, faded(mutedColor.get(), 0.9f), shadow, scale, tracking);
        double ruleEnd = x + w;
        if (!right.isEmpty()) {
            renderer.text(right, x + w - renderer.textWidth(right, shadow, scale), ty, faded(mutedColor.get(), 0.75f), shadow, scale);
            ruleEnd = x + w - rightW - 4.0 * s;
        }
        double ruleX = end + 4.0 * s;
        if (ruleEnd - ruleX > 1.0) {
            double ruleH = Math.max(0.6, 0.8 * s);
            Color a = faded(mutedColor.get(), 0.4f);
            Color b = faded(mutedColor.get(), 0.05f);
            renderer.quad(ruleX, y + (h - ruleH) / 2.0, ruleEnd - ruleX, ruleH, a, b, b, a);
        }
    }

    private void drawConsoleLog(HudRenderer renderer, List<LogEntry> lines, double x, double y, double w, double rowH,
                                double clockW, double tagW, double pillH, double limitY, boolean shadow,
                                double textScale, double clockScale, double microScale, double gap, double s, boolean anim, long now) {
        long bottomKey = lines.isEmpty() ? 0L : lines.get(lines.size() - 1).seq();
        if (bottomKey != lastBottomLogKey) {
            logSlideStartMs = now;
            lastBottomLogKey = bottomKey;
        }
        double textH = renderer.textHeight(shadow, textScale);
        double clockH = renderer.textHeight(shadow, clockScale);
        double tagX = x + clockW + gap * 2.0;
        double textX = tagX + tagW + gap * 2.0;
        double textAvail = Math.max(0.0, x + w - textX);
        double drawnH = Math.max(pillH, Math.max(textH, clockH));
        if (lines.isEmpty()) {
            renderer.text("waiting for the first event", textX, y + (rowH - textH) / 2.0, faded(mutedColor.get(), 0.6f), shadow, textScale);
            return;
        }
        int total = lines.size();
        for (int i = 0; i < total; i++) {
            LogEntry e = lines.get(i);
            double rowAlpha = total <= 1 ? 1.0 : 0.4 + 0.6 * i / (double) (total - 1);
            double dy = 0.0;
            double enter = 1.0;
            double flash = 0.0;
            if (i == total - 1 && anim) {
                double p = smoothstep((now - logSlideStartMs) / (double) CONSOLE_LOG_SLIDE_MS);
                dy = Math.min((1.0 - p) * 4.0 * s, Math.max(0.0, limitY - (y + i * rowH) - (rowH + drawnH) / 2.0));
                enter = p;
                flash = 1.0 - MathClampD((now - logSlideStartMs) / (double) CONSOLE_LOG_FLASH_MS, 0.0, 1.0);
            }
            float a = (float) (rowAlpha * enter);
            if (e.seq() == -2L && anim) a *= (float) (0.6 + 0.4 * Math.cos(animTime * 2.0 * Math.PI / 1.1));
            double rowY = y + i * rowH + dy;
            renderer.text(formatClock(e.whenMs()), x, rowY + (rowH - clockH) / 2.0, faded(mutedColor.get(), a * 0.85f), shadow, clockScale);
            drawPill(renderer, e.tag(), tagX, rowY + (rowH - pillH) / 2.0, tagW, pillH, logTagColor(e), a, (float) (0.18 + 0.32 * flash), shadow, microScale);
            drawClipped(renderer, e.text(), textX, rowY + (rowH - textH) / 2.0, textAvail, faded(toneColor(e.tone()), a), shadow, textScale);
        }
    }

    private Color logTagColor(LogEntry e) {
        for (int g = 0; g < GROUP_WORD.length; g++) {
            if (GROUP_WORD[g].equals(e.tag())) return phaseColor(g);
        }
        return toneColor(e.tone());
    }

    private void drawConsoleHistory(HudRenderer renderer, List<RunSummary> runs, int shown, double reveal, double x, double y, double w, double rowH,
                                    double numW, double durW, double chestsW, double shulkW, double badgeW, double pillH, double limitY,
                                    boolean shadow, double textScale, double microScale, double gap, double s) {
        double textH = renderer.textHeight(shadow, textScale);
        if (shown == 0) {
            renderer.text("no finished run yet", x, y + (rowH - textH) / 2.0, faded(mutedColor.get(), 0.6f), shadow, textScale);
            return;
        }
        double badgeX = x + w - badgeW;
        double shulkX = badgeX - gap * 2.0 - shulkW;
        double chestsX = shulkX - gap * 2.0 - chestsW;
        double durX = chestsX - gap * 2.0 - durW;
        double bandX = x + numW + gap * 2.0;
        double bandW = Math.max(4.0 * s, durX - gap * 2.0 - bandX);
        double bandH = Math.max(2.0, 3.0 * s);
        long maxTotal = 1L;
        for (int i = 0; i < shown; i++) maxTotal = Math.max(maxTotal, runs.get(i).run().totalMs());
        double scale = bandW / maxTotal;

        for (int i = 0; i < shown; i++) {
            RunSummary r = runs.get(i);
            float a = (float) (i == 0 ? reveal : Math.max(0.55, 1.0 - 0.1 * i));
            double drop = i == 0 ? Math.min((1.0 - reveal) * 4.0 * s, Math.max(0.0, limitY - y - (rowH + Math.max(textH, pillH)) / 2.0)) : 0.0;
            double rowY = y + i * rowH + drop;
            double ty = rowY + (rowH - textH) / 2.0;

            renderer.text("#" + r.number(), x, ty, faded(mutedColor.get(), a), shadow, textScale);

            double by = rowY + (rowH - bandH) / 2.0;
            renderer.quad(bandX, by, bandW, bandH, faded(mutedColor.get(), 0.14f * a));
            double cx = bandX;
            for (Frozen f : r.run().segments()) {
                double segW = f.durationMs() * scale;
                if (segW > 0.0) renderer.quad(cx, by, segW, bandH, faded(phaseColor(f.group()), a));
                cx += segW;
            }
            if (r.outcome() == Outcome.FAILED && cx > bandX) {
                double capW = Math.min(bandH, cx - bandX);
                renderer.quad(cx - capW, by, capW, bandH, faded(badColor.get(), a));
            }

            String dur = formatSpan(r.run().totalMs());
            renderer.text(dur, durX + durW - renderer.textWidth(dur, shadow, textScale), ty, faded(valueColor.get(), a), shadow, textScale);
            if (r.outcome() != Outcome.CRUISE) {
                Color chestColor = r.chests() > 0 ? faded(valueColor.get(), a) : faded(mutedColor.get(), a * 0.7f);
                drawClipped(renderer, plural(r.chests(), "chest"), chestsX, ty, chestsW, chestColor, shadow, textScale);
            }
            if (r.shulkers() > 0) {
                drawClipped(renderer, plural(r.shulkers(), "shulker"), shulkX, ty, shulkW, faded(accentColor.get(), a), shadow, textScale);
            }
            drawPill(renderer, OUTCOME_WORDS[r.outcome().ordinal()], badgeX, rowY + (rowH - pillH) / 2.0, badgeW, pillH,
                outcomeColor(r.outcome()), a, 0.22f, shadow, microScale);
        }
    }

    private Color outcomeColor(Outcome outcome) {
        return switch (outcome) {
            case DONE -> accentColor.get();
            case ABANDONED -> amberColor.get();
            case FAILED -> badColor.get();
            default -> mutedColor.get();
        };
    }

    private void drawConsoleTiles(HudRenderer renderer, ConsoleView v, double x, double y, double w, double h,
                                  boolean shadow, double s, double microScale, double tracking, double gap, boolean anim) {
        String[] values = {
            Integer.toString(v.rooms()), Integer.toString(v.spawners()), Integer.toString(v.chests()),
            Integer.toString(v.shulkers()), v.avgRun(), v.perHour()
        };
        double valueH = renderer.textHeight(shadow, s);
        double tileW = w / values.length;
        for (int i = 0; i < values.length; i++) {
            double tx = x + i * tileW;
            if (i > 0) renderer.quad(tx, y + s, Math.max(0.6, 0.8 * s), h - 2.0 * s, faded(mutedColor.get(), 0.2f));
            float fresh = pulse.freshness("consoleTile" + i, values[i], CONSOLE_VALUE_FLASH_MS);
            boolean empty = "--".equals(values[i]) || "0".equals(values[i]);
            Color base = empty ? faded(mutedColor.get(), 0.7f) : i == 3 ? accentColor.get() : valueColor.get();
            Color c = anim && fresh > 0.0f ? mix(base, accentColor.get(), fresh) : base;
            double inner = i == 0 ? 0.0 : gap;
            renderer.text(values[i], tx + inner, y, c, shadow, s);
            drawTracked(renderer, TILE_LABELS[i], tx + inner, y + valueH + 2.0 * s, faded(mutedColor.get(), 0.85f), shadow, microScale, tracking);
        }
    }

    private void drawPill(HudRenderer renderer, String text, double x, double y, double w, double h, Color color,
                          float alpha, float fillAlpha, boolean shadow, double scale) {
        pill(renderer, x, y, w, h, faded(color, fillAlpha * alpha));
        double tw = renderer.textWidth(text, shadow, scale);
        double ty = y + (h - renderer.textHeight(shadow, scale)) / 2.0;
        if (tw > w) drawClipped(renderer, text, x, ty, w, faded(color, alpha), shadow, scale);
        else renderer.text(text, x + (w - tw) / 2.0, ty, faded(color, alpha), shadow, scale);
    }

    private void pill(HudRenderer renderer, double x, double y, double w, double h, Color colour) {
        double r = Math.min(h, w) / 2.0;
        double cy = y + h / 2.0;
        if (w - 2.0 * r > 0.0) renderer.quad(x + r, y, w - 2.0 * r, h, colour);
        halfDisc(renderer, x + r, cy, r, Math.PI * 0.5, colour);
        halfDisc(renderer, x + w - r, cy, r, -Math.PI * 0.5, colour);
    }

    private void halfDisc(HudRenderer renderer, double cx, double cy, double r, double from, Color colour) {
        int segments = 8;
        double previousX = cx + Math.cos(from) * r;
        double previousY = cy + Math.sin(from) * r;
        for (int i = 1; i <= segments; i++) {
            double angle = from + Math.PI * i / segments;
            double px = cx + Math.cos(angle) * r;
            double py = cy + Math.sin(angle) * r;
            tri(renderer, cx, cy, previousX, previousY, px, py, colour);
            previousX = px;
            previousY = py;
        }
    }

    private double drawTracked(HudRenderer renderer, String text, double x, double y, Color color, boolean shadow, double scale, double tracking) {
        double cx = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            renderer.text(ch, cx, y, color, shadow, scale);
            cx += renderer.textWidth(ch, shadow, scale) + tracking;
        }
        return text.isEmpty() ? x : cx - tracking;
    }

    private double trackedWidth(HudRenderer renderer, String text, boolean shadow, double scale, double tracking) {
        double w = 0.0;
        for (int i = 0; i < text.length(); i++) w += renderer.textWidth(String.valueOf(text.charAt(i)), shadow, scale);
        return w + tracking * Math.max(0, text.length() - 1);
    }

    private double maxTrackedWidth(HudRenderer renderer, String[] arr, boolean shadow, double scale, double tracking) {
        double m = 0.0;
        for (String t : arr) m = Math.max(m, trackedWidth(renderer, t, shadow, scale, tracking));
        return m;
    }

    private static Color mix(Color a, Color b, float t) {
        float k = MathClamp01(t);
        return new Color(
            (int) (a.r + (b.r - a.r) * k), (int) (a.g + (b.g - a.g) * k),
            (int) (a.b + (b.b - a.b) * k), (int) (a.a + (b.a - a.a) * k));
    }

    private static String formatActive(long ms) {
        long minutes = Math.max(0L, ms) / 60_000L;
        String span = minutes >= 60L ? String.format(Locale.ROOT, "%dh %02dm", minutes / 60L, minutes % 60L) : minutes + " min";
        return span + " active";
    }


    private void renderTimeline(HudRenderer renderer, SpawnerDetector detector, String stateName, StepInfo step, long elapsed, long now) {
        boolean shadow = textShadow.get();
        double s = size.get();
        boolean anim = animations.get();
        double pad = padding.get();

        double row1H = ROW1_H * s;
        double gaugeW = GAUGE_W * s;
        double gap = GAP * s;
        double row2H = ROW2_H * s;
        double histH = HIST_H * s;
        double histGap = HIST_GAP * s;

        double wordW = maxTextWidth(renderer, SHORT_WORDS, shadow, s);
        double chronoW = renderer.textWidth(CHRONO_TEMPLATE, shadow, s);

        boolean withHistory = showHistory.get();
        int historyRows = withHistory ? Math.min(HIST_ROWS, history.size()) : 0;
        double legendH = historyRows > 0 ? renderer.textHeight(shadow, s * 0.75) + 2.0 * s : 0.0;

        double width = Math.max(wordW + gap + gaugeW + gap + chronoW, MIN_BAND_W * s);
        double height = row1H + gap + row2H + (historyRows > 0 ? gap + legendH + historyRows * (histH + histGap) : 0.0);

        double x0 = this.x + pad;
        double y0 = this.y + pad;

        if (background.get()) renderer.quad(this.x, this.y, width + pad * 2.0, height + pad * 2.0, backgroundColor.get());

        drawRow1(renderer, detector, stateName, step, elapsed, now, x0, y0, row1H, wordW, gaugeW, gap, chronoW, shadow, s, anim);

        double y = y0 + row1H + gap;
        boolean pausedForDisplay = isPausableState(stateName) && detector.isAutoPaused();
        double totalW = renderer.textWidth(CHRONO_TEMPLATE, shadow, s * 0.8);
        double bandW = Math.max(0.0, width - gap - totalW);
        long liveTotal = runStartMs > 0L ? now - runStartMs : 0L;
        drawBand(renderer, x0, y, bandW, row2H, now, shadow, s, anim, runSegments, liveTotal, runFailedCap, pausedForDisplay);
        String totalText = formatSpan(liveTotal);
        double totalTextH = renderer.textHeight(shadow, s * 0.8);
        renderer.text(totalText, x0 + width - renderer.textWidth(totalText, shadow, s * 0.8), y + (row2H - totalTextH) / 2.0, valueColor.get(), shadow, s * 0.8);
        y += row2H;

        if (historyRows > 0) {
            y += gap;
            renderer.text("last runs", x0, y, mutedColor.get(), shadow, s * 0.75);
            y += legendH;
            double maxTotal = maxHistTotal(now);
            double scale = maxTotal > 0.0 ? bandW / maxTotal : 1.0;
            for (int i = 0; i < historyRows; i++) {
                RunRecord r = history.get(i);
                if (insertion != null && i == 0 && insertion.totalMs == r.totalMs) continue;
                drawFrozenBand(renderer, x0, y, bandW, histH, r, scale);
                y += histH + histGap;
            }
            if (insertion != null) {
                double progress = anim ? smoothstep((now - insertion.startedMs) / (double) INSERT_ANIM_MS) : 1.0;
                double fromY = y0 + row1H + gap;
                double toY = y0 + row1H + gap + row2H + gap + legendH;
                double iy = fromY + (toY - fromY) * progress;
                double ih = row2H + (histH - row2H) * progress;
                double maxTotal2 = Math.max(maxTotal, insertion.totalMs);
                double scale2 = maxTotal2 > 0.0 ? bandW / maxTotal2 : 1.0;
                drawFrozenBand(renderer, x0, iy, bandW, ih, new RunRecord(insertion.segments, insertion.totalMs, insertion.failed), scale2);
            }
        }

        setSize(width + pad * 2.0, height + pad * 2.0);
    }

    private double maxHistTotal(long now) {
        double max = runStartMs > 0L ? now - runStartMs : 0.0;
        for (RunRecord r : history) max = Math.max(max, r.totalMs);
        return Math.max(max, 5_000.0);
    }


    private void drawRow1(HudRenderer renderer, SpawnerDetector detector, String stateName, StepInfo step, long elapsed, long now,
                          double x0, double y0, double rowH, double wordW, double gaugeW, double gap, double chronoW,
                          boolean shadow, double s, boolean anim) {
        double textH = renderer.textHeight(shadow, s);
        double textY = y0 + (rowH - textH) / 2.0;

        double wordProgress = anim ? Math.min(1.0, (now - wordChangeMs) / (double) WORD_ANIM_MS) : 1.0;
        wordProgress = smoothstep(wordProgress);

        if (wordProgress < 1.0 && !outgoingWord.isEmpty()) {
            float outAlpha = (float) (1.0 - wordProgress);
            double outY = textY - wordProgress * rowH;
            drawClipped(renderer, outgoingWord, x0, outY, wordW, faded(outgoingWordColor, outAlpha), shadow, s);
        }

        float inAlpha = (float) (wordProgress < 1.0 ? wordProgress : 1.0);
        double inY = wordProgress < 1.0 ? textY + (1.0 - wordProgress) * rowH * 0.5 : textY;
        drawClipped(renderer, displayedWord, x0, inY, wordW, faded(displayedWordColor, inAlpha), shadow, s);

        double gx = x0 + wordW + gap;
        drawGauge(renderer, step, gx, y0, gaugeW, rowH, shadow, s, anim);

        SettingColor chronoColor = chronoColor(detector, stateName, elapsed, now);
        String chronoText = formatSpan(elapsed);
        double cx = x0 + wordW + gap + gaugeW + gap;
        renderer.text(chronoText, cx, textY, chronoColor, shadow, s);
    }

    private void drawGauge(HudRenderer renderer, StepInfo step, double x, double y, double w, double h, boolean shadow, double s, boolean anim) {
        switch (step.kind()) {
            case BAR -> drawBarWithNumber(renderer, x, y, w, h, step.fraction(), step.barColor(), step.valueText(), step.valueTemplate(), shadow, s);
            case PIPS -> drawRoomPips(renderer, x, y, w, h, step.pillsFilled(), step.pillsTotal(), step.barColor(), anim);
            case DOTS -> drawDots(renderer, x, y, w, h, step.pillsFilled(), step.pillsTotal(), step.barColor());
            case PULSE -> drawPulseDot(renderer, x, y, w, h, step.barColor(), anim);
        }
    }

    private static int MathHelperClamp(int v, int min, int max) {
        return v < min ? min : v > max ? max : v;
    }

    private static double MathClampD(double v, double min, double max) {
        return v < min ? min : v > max ? max : v;
    }

    private static Double hdist(double px, double pz, double tx, double tz) {
        return Math.hypot(tx - px, tz - pz);
    }

    private Double tunnelTargetDist(SpawnerDetector d, net.minecraft.client.network.ClientPlayerEntity player) {
        BlockPos goal = d.getAutoTunnelGoal();
        return goal == null ? null : hdist(player.getX(), player.getZ(), goal.getX() + 0.5, goal.getZ() + 0.5);
    }

    private Double exitTargetDist(SpawnerDetector d, net.minecraft.client.network.ClientPlayerEntity player) {
        if (!d.hasAutoExitShaft()) return null;
        return hdist(player.getX(), player.getZ(), d.getAutoExitShaftX() + 0.5, d.getAutoExitShaftZ() + 0.5);
    }

    private Double swimUpTargetDist(SpawnerDetector d, net.minecraft.client.network.ClientPlayerEntity player) {
        int tx, tz, ty;
        if (d.hasAutoExitShaft()) {
            tx = d.getAutoExitShaftX();
            tz = d.getAutoExitShaftZ();
            ty = d.getAutoExitShaftExitY();
        } else if (d.hasAutoColumn()) {
            tx = d.getAutoColumnX();
            tz = d.getAutoColumnZ();
            ty = d.getAutoColumnExitY();
        } else {
            return null;
        }
        double dx = (tx + 0.5) - player.getX();
        double dz = (tz + 0.5) - player.getZ();
        double dy = ty - player.getY();
        return Math.sqrt(dx * dx + dz * dz + dy * dy);
    }

    private Double ascendTargetDist(SpawnerDetector d, net.minecraft.client.network.ClientPlayerEntity player) {
        BlockPos t = d.getAutoAscendNearestTarget();
        if (t != null) return hdist(player.getX(), player.getZ(), t.getX() + 0.5, t.getZ() + 0.5);
        if (d.hasAutoColumn()) return hdist(player.getX(), player.getZ(), d.getAutoColumnX() + 0.5, d.getAutoColumnZ() + 0.5);
        return null;
    }

    private static int fmtY(int v) {
        return MathHelperClamp(v, -9999, 9999);
    }

    private static String fmtDist(double metres) {
        double v = MathClampD(metres, 0.0, 999.9);
        return String.format(Locale.ROOT, "%.1f m", v);
    }

    private void drawBarWithNumber(HudRenderer renderer, double x, double y, double w, double h,
                                   double fraction, SettingColor color, String numberText, String numberTemplate, boolean shadow, double s) {
        double numScale = s * 0.85;
        double numberW = numberTemplate.isEmpty() ? 0.0 : renderer.textWidth(numberTemplate, shadow, numScale);
        double numGap = numberText.isEmpty() ? 0.0 : 4.0 * s;
        double barW = Math.max(4.0, w - numberW - numGap);
        double barH = Math.max(3.0, h * 0.4);
        double barY = y + (h - barH) / 2.0;
        SettingColor track = mutedColor.get();
        renderer.quad(x, barY, barW, barH, new Color(track.r, track.g, track.b, (int) (track.a * 0.35)));
        double fillW = Math.max(0.0, Math.min(barW, barW * MathClampD(fraction, 0.0, 1.0)));
        if (fillW > 0.3) renderer.quad(x, barY, fillW, barH, color);

        if (!numberText.isEmpty()) {
            double textH = renderer.textHeight(shadow, numScale);
            double textW = renderer.textWidth(numberText, shadow, numScale);
            double tx = x + w - textW;
            renderer.text(numberText, tx, y + (h - textH) / 2.0, color, shadow, numScale);
        }
    }

    private void drawThinBar(HudRenderer renderer, double x, double y, double w, double h, double fraction, SettingColor color) {
        SettingColor track = mutedColor.get();
        renderer.quad(x, y, w, h, new Color(track.r, track.g, track.b, (int) (track.a * 0.35)));
        double fillW = Math.max(0.0, Math.min(w, w * MathClampD(fraction, 0.0, 1.0)));
        if (fillW > 0.3) renderer.quad(x, y, fillW, h, color);
    }

    private void drawRoomPips(HudRenderer renderer, double x, double y, double w, double h, int idx, int total, SettingColor color, boolean anim) {
        if (total <= 0) {
            drawPulseDot(renderer, x, y, w, h, color, anim);
            return;
        }
        int shown = Math.min(total, 6);
        double cy = y + h / 2.0;
        double r = Math.max(2.0, h * 0.28);
        double step = shown > 1 ? (w - 2.0 * r) / (shown - 1) : 0.0;
        double curIdx = total > shown ? (idx / (double) total) * shown : idx;

        for (int i = 0; i < shown; i++) {
            double cx = x + r + i * step;
            if (i < curIdx - 0.001) {
                disc(renderer, cx, cy, r, color);
            } else if (i < curIdx + 0.999) {
                double pulseA = anim ? 0.55 + 0.45 * Math.sin(animTime * Math.PI * 2.0 / 1.1) : 1.0;
                disc(renderer, cx, cy, r, new Color(color.r, color.g, color.b, (int) (color.a * pulseA)));
            } else {
                ring(renderer, cx, cy, r, Math.max(1.0, r * 0.35), mutedColor.get());
            }
        }
    }

    private void drawDots(HudRenderer renderer, double x, double y, double w, double h, int filled, int total, SettingColor color) {
        double cy = y + h / 2.0;
        double r = Math.max(2.0, h * 0.26);
        double step = total > 1 ? (w - 2.0 * r) / (total - 1) : 0.0;
        int f = MathHelperClamp(filled, 0, total);
        for (int i = 0; i < total; i++) {
            double cx = x + r + i * step;
            if (i < f) disc(renderer, cx, cy, r, color);
            else ring(renderer, cx, cy, r, Math.max(1.0, r * 0.35), mutedColor.get());
        }
    }

    private void drawPulseDot(HudRenderer renderer, double x, double y, double w, double h, SettingColor color, boolean anim) {
        double cy = y + h / 2.0;
        double cx = x + Math.min(w, h) / 2.0;
        double r = Math.max(2.0, h * 0.3);
        double a = anim ? 0.5 + 0.5 * Math.sin(animTime * Math.PI * 2.0 / 1.3) : 1.0;
        disc(renderer, cx, cy, r, new Color(color.r, color.g, color.b, (int) (color.a * a)));
    }


    private void drawBand(HudRenderer renderer, double x, double y, double w, double h, long now,
                         boolean shadow, double s, boolean anim,
                         List<Segment> segments, long liveTotalMs, boolean failedCap, boolean pausedForDisplay) {
        SettingColor track = mutedColor.get();
        renderer.quad(x, y, w, h, new Color(track.r, track.g, track.b, (int) (track.a * 0.18)));

        double total = maxHistTotal(now);
        double scale = total > 0.0 ? w / total : 0.0;

        double cx = x;
        boolean anySegment = false;
        for (int i = 0; i < segments.size(); i++) {
            Segment seg = segments.get(i);
            anySegment = true;
            long dur = seg.durationMs(now);
            double segW = dur * scale;
            boolean isLast = i == segments.size() - 1;

            if (isLast && seg.endMs < 0L && !failedCap) {
                long usual = usualMs(seg.group);
                double solidMs = Math.min(dur, usual);
                double solidW = solidMs * scale;
                renderer.quad(cx, y, Math.max(0.0, solidW), h, phaseColor(seg.group));

                if (pausedForDisplay) {
                    drawHatch(renderer, cx, y, segW, h, mutedColor.get(), anim ? animTime * 26.0 : 0.0);
                } else if (dur > usual) {
                    double overflowW = (dur - usual) * scale;
                    drawHatch(renderer, cx + solidW, y, overflowW, h, amberColor.get(), anim ? animTime * 26.0 : 0.0);
                }

                if (usual * scale <= w) {
                    double tickX = cx + usual * scale;
                    renderer.quad(tickX, y, Math.max(1.0, s), h, new Color(255, 255, 255, 90));
                }
            } else {
                renderer.quad(cx, y, Math.max(0.0, segW), h, phaseColor(seg.group));
            }
            cx += segW;
        }

        if (failedCap && anySegment) {
            renderer.quad(Math.max(x, cx - 2.0 * s), y, 2.0 * s, h, badColor.get());
        }

    }

    private void drawFrozenBand(HudRenderer renderer, double x, double y, double w, double h, RunRecord record, double scale) {
        double cx = x;
        for (Frozen f : record.segments()) {
            double segW = f.durationMs() * scale;
            renderer.quad(cx, y, Math.max(0.0, segW), h, phaseColor(f.group()));
            cx += segW;
        }
        if (record.failed() && !record.segments().isEmpty()) {
            renderer.quad(Math.max(x, cx - Math.max(1.0, h)), y, Math.max(1.0, h), h, badColor.get());
        }
    }

    private void drawHatch(HudRenderer renderer, double x, double y, double w, double h, SettingColor color, double offsetPx) {
        if (w <= 0.0) return;
        double spacing = Math.max(3.0, h * 0.5);
        double shear = h * 0.55;
        double stripeW = Math.max(1.0, spacing * 0.45);
        double phase = offsetPx % spacing;
        Color c = new Color(color.r, color.g, color.b, (int) (color.a * 0.8));
        double left = x, right = x + w;

        for (double bx = x - shear - spacing + phase; bx < right; bx += spacing) {
            double blx = MathClampD(bx, left, right), bly = y + h;
            double brx = MathClampD(bx + stripeW, left, right), bry = y + h;
            double trx = MathClampD(bx + stripeW + shear, left, right), tryy = y;
            double tlx = MathClampD(bx + shear, left, right), tly = y;
            if (brx <= blx && trx <= tlx) continue;
            tri(renderer, blx, bly, brx, bry, trx, tryy, c);
            tri(renderer, blx, bly, trx, tryy, tlx, tly, c);
        }
    }


    private void renderCard(HudRenderer renderer, SpawnerDetector detector, String stateName, StepInfo step, long elapsed, long now) {
        boolean shadow = textShadow.get();
        double s = size.get();
        boolean anim = animations.get();
        double pad = padding.get();
        double gap = GAP * s;

        double labelRowH = CARD_LABEL_ROW_H * s;
        double barRowH = CARD_BAR_H * s;
        double pipRowH = CARD_PIP_ROW_H * s;

        double labelW = maxTextWidth(renderer, CARD_LABELS, shadow, s);
        double rangeW = renderer.textWidth(RANGE_TEMPLATE, shadow, s);
        double chronoW = renderer.textWidth(CHRONO_TEMPLATE, shadow, s);
        double width = Math.max(labelW + gap + rangeW + gap + chronoW, MIN_CARD_W * s);

        boolean showBar = step.kind() == Kind.BAR || step.kind() == Kind.PIPS;
        boolean showNote = !step.note().isEmpty();
        boolean showPips = showNote || step.kind() == Kind.DOTS;

        double height = labelRowH + (showBar ? gap + barRowH : 0.0) + (showPips ? gap + pipRowH : 0.0);

        double x0 = this.x + pad, y0 = this.y + pad;
        if (background.get()) renderer.quad(this.x, this.y, width + pad * 2.0, height + pad * 2.0, backgroundColor.get());

        double textH = renderer.textHeight(shadow, s);
        double textY = y0 + (labelRowH - textH) / 2.0;
        String rangeText = step.rangeFrom() != null
            ? String.format(Locale.ROOT, "y %d -> %d", fmtY(step.rangeFrom()), fmtY(step.rangeTo()))
            : "";
        String bigLine = rangeText.isEmpty() ? step.label() : step.label() + "  " + rangeText;

        String chronoText = formatSpan(elapsed);
        SettingColor chronoColor = chronoColor(detector, stateName, elapsed, now);
        double chronoTextW = renderer.textWidth(chronoText, shadow, s);
        double availableForBig = Math.max(0.0, width - gap - chronoW);
        drawClipped(renderer, bigLine, x0, textY, availableForBig, step.labelColor(), shadow, s);
        renderer.text(chronoText, x0 + width - chronoTextW, textY, chronoColor, shadow, s);

        double y = y0 + labelRowH;
        if (showBar) {
            y += gap;
            drawThinBar(renderer, x0, y, width, barRowH, step.fraction(), step.barColor());
            y += barRowH;
        }
        if (showPips) {
            y += gap;
            if (showNote) {
                double ph = renderer.textHeight(shadow, s * 0.8);
                drawClipped(renderer, step.note(), x0, y + (pipRowH - ph) / 2.0, width, mutedColor.get(), shadow, s * 0.8);
            } else {
                String text = step.valueText().isEmpty() ? step.pillsFilled() + "/" + step.pillsTotal() : step.valueText();
                double ph = renderer.textHeight(shadow, s * 0.8);
                double tw = Math.min(width * 0.6, renderer.textWidth(text, shadow, s * 0.8));
                drawClipped(renderer, text, x0, y + (pipRowH - ph) / 2.0, width * 0.6, mutedColor.get(), shadow, s * 0.8);
                double dotsX = x0 + tw + gap;
                double dotsW = Math.max(10.0 * s, width - tw - gap);
                drawDots(renderer, dotsX, y, dotsW, pipRowH, step.pillsFilled(), step.pillsTotal(), step.barColor());
            }
        }

        setSize(width + pad * 2.0, height + pad * 2.0);
    }


    private void renderLine(HudRenderer renderer, SpawnerDetector detector, String stateName, StepInfo step, long elapsed, long now) {
        boolean shadow = textShadow.get();
        double s = size.get();
        double pad = padding.get();
        double gap = GAP * s;
        double rowH = LINE_ROW_H * s;
        double barH = LINE_BAR_H * s;

        double wordW = maxTextWidth(renderer, SHORT_WORDS, shadow, s);
        double rangeW = renderer.textWidth(RANGE_TEMPLATE_COMPACT, shadow, s);
        double valueW = Math.max(renderer.textWidth(FRACTION_TEMPLATE, shadow, s),
            Math.max(renderer.textWidth(DIST_TEMPLATE, shadow, s), renderer.textWidth(RETRY_TEMPLATE, shadow, s)));
        double chronoW = renderer.textWidth(CHRONO_TEMPLATE, shadow, s);
        double sep = renderer.textWidth(" · ", shadow, s);

        double width = Math.max(wordW + sep + rangeW + sep + valueW + sep + chronoW, MIN_LINE_W * s);
        double height = rowH + gap + barH;

        double x0 = this.x + pad, y0 = this.y + pad;
        if (background.get()) renderer.quad(this.x, this.y, width + pad * 2.0, height + pad * 2.0, backgroundColor.get());

        List<String> parts = new ArrayList<>();
        parts.add(step.shortWord());
        if (!step.note().isEmpty()) {
            parts.add(step.note());
        } else {
            if (step.rangeFrom() != null) parts.add(String.format(Locale.ROOT, "y%d->%d", fmtY(step.rangeFrom()), fmtY(step.rangeTo())));
            if (!step.valueText().isEmpty()) parts.add(step.valueText());
        }
        String prefix = String.join(" · ", parts);

        double textH = renderer.textHeight(shadow, s);
        double textY = y0 + (rowH - textH) / 2.0;
        String chronoText = formatSpan(elapsed);
        SettingColor chronoColor = chronoColor(detector, stateName, elapsed, now);
        double chronoTextW = renderer.textWidth(chronoText, shadow, s);
        double availableForPrefix = Math.max(0.0, width - gap - chronoW);
        drawClipped(renderer, prefix, x0, textY, availableForPrefix, step.labelColor(), shadow, s);
        renderer.text(chronoText, x0 + width - chronoTextW, textY, chronoColor, shadow, s);

        double y = y0 + rowH + gap;
        if (step.kind() == Kind.BAR || step.kind() == Kind.PIPS) {
            drawThinBar(renderer, x0, y, width, barH, step.fraction(), step.barColor());
        } else {
            SettingColor track = mutedColor.get();
            renderer.quad(x0, y, width, barH, new Color(track.r, track.g, track.b, (int) (track.a * 0.35)));
        }

        setSize(width + pad * 2.0, height + pad * 2.0);
    }


    private void renderEditorDemo(HudRenderer renderer) {
        switch (style.get()) {
            case CONSOLE -> demoConsole(renderer);
            case CARD -> demoCard(renderer);
            case LINE -> demoLine(renderer);
            default -> demoTimeline(renderer);
        }
    }

    private void demoConsole(HudRenderer renderer) {
        long now = System.currentTimeMillis();
        List<LogEntry> all = List.of(
            new LogEntry(-109L, now - 277_000L, "DIG", "y 45 " + ARROW + " 17 · 0:31", Tone.DIM),
            new LogEntry(-108L, now - 236_000L, "ROOM", "chest 3/3 · 0:41", Tone.DIM),
            new LogEntry(-107L, now - 214_000L, "EXIT", "shaft fly · 0:22", Tone.DIM),
            new LogEntry(-106L, now - 205_000L, "CLIMB", "climb done · 0:09", Tone.DIM),
            new LogEntry(-105L, now - 204_000L, "HANDOFF", "handed off · 0:01", Tone.DIM),
            new LogEntry(-104L, now - 204_000L, "RUN 26", "done · 2:08 · 3 chests · 1 shulker", Tone.ACCENT),
            new LogEntry(-103L, now - 62_000L, "RUN 27", "spawner found", Tone.ACCENT),
            new LogEntry(-102L, now - 44_000L, "FLY", "landed · 0:18", Tone.DIM),
            new LogEntry(-101L, now - 37_000L, "LAND", "walked to hole · 0:07", Tone.DIM)
        );
        List<LogEntry> log = all.subList(Math.max(0, all.size() - logLines.get()), all.size());
        List<RunSummary> runs = List.of(
            demoRun(26, 3, 1, Outcome.DONE, 18, 6, 31, 41, 22, 9, 1),
            demoRun(25, 0, 0, Outcome.ABANDONED, 22, 9, 47),
            demoRun(24, 2, 0, Outcome.DONE, 16, 5, 28, 33, 19, 8, 1),
            demoRun(23, 0, 0, Outcome.FAILED, 20, 7, 38),
            demoRun(22, 0, 0, Outcome.CRUISE, 54),
            demoRun(21, 4, 2, Outcome.DONE, 19, 6, 35, 52, 24, 10, 1),
            demoRun(20, 1, 0, Outcome.DONE, 17, 5, 26, 22, 18, 8, 1),
            demoRun(19, 2, 1, Outcome.DONE, 21, 7, 30, 37, 20, 9, 1)
        );
        ConsoleView view = new ConsoleView(
            "DIGGING", creuseColor.get(), Tone.OK, "y 45 " + ARROW + " 17 · 23/57 blocks", "0:37",
            "RUN 27", "1:02",
            Kind.BAR, 23.0 / 57.0, 0, 0, creuseColor.get(), "demo",
            log, runs, 1.0,
            12, 11, 27, 4, "1:52", "6.4", formatActive(112L * 60_000L));
        drawConsole(renderer, view, now);
    }

    private static RunSummary demoRun(int number, int chests, int shulkers, Outcome outcome, int... seconds) {
        List<Frozen> segments = new ArrayList<>();
        long total = 0L;
        for (int g = 0; g < seconds.length; g++) {
            segments.add(new Frozen(g, seconds[g] * 1000L));
            total += seconds[g] * 1000L;
        }
        return new RunSummary(number, new RunRecord(segments, total, outcome == Outcome.FAILED), chests, shulkers, outcome);
    }

    private void demoCard(HudRenderer renderer) {
        boolean shadow = textShadow.get();
        double s = size.get();
        double pad = padding.get();
        double gap = GAP * s;
        double labelRowH = CARD_LABEL_ROW_H * s;
        double barRowH = CARD_BAR_H * s;
        double pipRowH = CARD_PIP_ROW_H * s;

        double labelW = maxTextWidth(renderer, CARD_LABELS, shadow, s);
        double rangeW = renderer.textWidth(RANGE_TEMPLATE, shadow, s);
        double chronoW = renderer.textWidth(CHRONO_TEMPLATE, shadow, s);
        double width = Math.max(labelW + gap + rangeW + gap + chronoW, MIN_CARD_W * s);
        double height = labelRowH + gap + barRowH + gap + pipRowH;

        double x0 = this.x + pad, y0 = this.y + pad;
        if (background.get()) renderer.quad(this.x, this.y, width + pad * 2.0, height + pad * 2.0, backgroundColor.get());

        double textH = renderer.textHeight(shadow, s);
        double textY = y0 + (labelRowH - textH) / 2.0;
        String bigLine = "DIGGING  y 45 -> 17";
        String chronoText = "0:37";
        double chronoTextW = renderer.textWidth(chronoText, shadow, s);
        drawClipped(renderer, bigLine, x0, textY, width - gap - chronoW, creuseColor.get(), shadow, s);
        renderer.text(chronoText, x0 + width - chronoTextW, textY, valueColor.get(), shadow, s);

        double y = y0 + labelRowH + gap;
        drawThinBar(renderer, x0, y, width, barRowH, 0.55, creuseColor.get());
        y += barRowH + gap;

        String pipText = "retry 2/5";
        double ph = renderer.textHeight(shadow, s * 0.8);
        double tw = Math.min(width * 0.6, renderer.textWidth(pipText, shadow, s * 0.8));
        drawClipped(renderer, pipText, x0, y + (pipRowH - ph) / 2.0, width * 0.6, mutedColor.get(), shadow, s * 0.8);
        drawDots(renderer, x0 + tw + gap, y, Math.max(10.0 * s, width - tw - gap), pipRowH, 2, 5, sortieColor.get());

        setSize(width + pad * 2.0, height + pad * 2.0);
    }

    private void demoLine(HudRenderer renderer) {
        boolean shadow = textShadow.get();
        double s = size.get();
        double pad = padding.get();
        double gap = GAP * s;
        double rowH = LINE_ROW_H * s;
        double barH = LINE_BAR_H * s;

        double wordW = maxTextWidth(renderer, SHORT_WORDS, shadow, s);
        double rangeW = renderer.textWidth(RANGE_TEMPLATE_COMPACT, shadow, s);
        double valueW = Math.max(renderer.textWidth(FRACTION_TEMPLATE, shadow, s),
            Math.max(renderer.textWidth(DIST_TEMPLATE, shadow, s), renderer.textWidth(RETRY_TEMPLATE, shadow, s)));
        double chronoW = renderer.textWidth(CHRONO_TEMPLATE, shadow, s);
        double sep = renderer.textWidth(" · ", shadow, s);
        double width = Math.max(wordW + sep + rangeW + sep + valueW + sep + chronoW, MIN_LINE_W * s);
        double height = rowH + gap + barH;

        double x0 = this.x + pad, y0 = this.y + pad;
        if (background.get()) renderer.quad(this.x, this.y, width + pad * 2.0, height + pad * 2.0, backgroundColor.get());

        String prefix = "DIG · y45->17 · 23/57";
        String chronoText = "0:12";
        double textH = renderer.textHeight(shadow, s);
        double textY = y0 + (rowH - textH) / 2.0;
        double chronoTextW = renderer.textWidth(chronoText, shadow, s);
        drawClipped(renderer, prefix, x0, textY, width - gap - chronoW, creuseColor.get(), shadow, s);
        renderer.text(chronoText, x0 + width - chronoTextW, textY, valueColor.get(), shadow, s);

        double y = y0 + rowH + gap;
        drawThinBar(renderer, x0, y, width, barH, 0.4, creuseColor.get());

        setSize(width + pad * 2.0, height + pad * 2.0);
    }

    private void demoTimeline(HudRenderer renderer) {
        boolean shadow = textShadow.get();
        double s = size.get();
        double pad = padding.get();
        double row1H = ROW1_H * s;
        double gap = GAP * s;
        double row2H = ROW2_H * s;
        double histH = HIST_H * s;
        double histGap = HIST_GAP * s;

        double wordW = maxTextWidth(renderer, SHORT_WORDS, shadow, s);
        double gaugeW = GAUGE_W * s;
        double chronoW = renderer.textWidth(CHRONO_TEMPLATE, shadow, s);
        double width = Math.max(wordW + gap + gaugeW + gap + chronoW, MIN_BAND_W * s);
        int histRows = 3;
        double legendH = renderer.textHeight(shadow, s * 0.75) + 2.0 * s;
        double height = row1H + gap + row2H + gap + legendH + histRows * (histH + histGap);

        double x0 = this.x + pad;
        double y0 = this.y + pad;

        if (background.get()) renderer.quad(this.x, this.y, width + pad * 2.0, height + pad * 2.0, backgroundColor.get());

        double textH = renderer.textHeight(shadow, s);
        renderer.text("DIG", x0, y0 + (row1H - textH) / 2.0, creuseColor.get(), shadow, s);
        drawBarWithNumber(renderer, x0 + wordW + gap, y0, gaugeW, row1H, 0.55, creuseColor.get(), "23/57", FRACTION_TEMPLATE, shadow, s);
        String chronoText = "0:37";
        renderer.text(chronoText, x0 + wordW + gap + gaugeW + gap, y0 + (row1H - textH) / 2.0, valueColor.get(), shadow, s);

        double y = y0 + row1H + gap;
        int[] demoGroups = {0, 1, 2, 2};
        long[] demoDur = {18_000L, 6_000L, 30_000L, 14_000L};
        double total = 0;
        for (long dd : demoDur) total += dd;
        double demoTotalW = renderer.textWidth(CHRONO_TEMPLATE, shadow, s * 0.8);
        double demoBandW = Math.max(0.0, width - gap - demoTotalW);
        double scale = demoBandW / total;
        double cx = x0;
        for (int i = 0; i < demoGroups.length; i++) {
            double w = demoDur[i] * scale;
            renderer.quad(cx, y, w, row2H, phaseColor(demoGroups[i]));
            cx += w;
        }
        drawHatch(renderer, cx - demoDur[3] * scale * 0.3, y, demoDur[3] * scale * 0.3, row2H, amberColor.get(), 0.0);
        String totalText = "1:08";
        renderer.text(totalText, x0 + width - renderer.textWidth(totalText, shadow, s * 0.8),
            y + (row2H - renderer.textHeight(shadow, s * 0.8)) / 2.0, valueColor.get(), shadow, s * 0.8);
        y += row2H + gap;

        renderer.text("last runs", x0, y, mutedColor.get(), shadow, s * 0.75);
        y += legendH;

        for (int r = 0; r < histRows; r++) {
            double hx = x0;
            double scale2 = demoBandW / (total * (1.0 + r * 0.15));
            for (int i = 0; i < demoGroups.length; i++) {
                double w = demoDur[i] * scale2 * (1.0 - r * 0.08);
                renderer.quad(hx, y, Math.max(0.0, w), histH, phaseColor(demoGroups[i]));
                hx += w;
            }
            if (r == 1) renderer.quad(hx, y, Math.max(1.0, histH), histH, badColor.get());
            y += histH + histGap;
        }

        setSize(width + pad * 2.0, height + pad * 2.0);
    }


    private static double smoothstep(double t) {
        double c = MathClampD(t, 0.0, 1.0);
        return c * c * (3.0 - 2.0 * c);
    }

    private static String formatSpan(long ms) {
        long capped = Math.min(Math.max(0L, ms), MAX_DISPLAY_MS);
        long seconds = capped / 1000L;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
    }

    private void disc(HudRenderer renderer, double cx, double cy, double r, Color colour) {
        int segments = 16;
        double previousX = cx + r;
        double previousY = cy;
        for (int i = 1; i <= segments; i++) {
            double angle = Math.PI * 2.0 * i / segments;
            double px = cx + Math.cos(angle) * r;
            double py = cy + Math.sin(angle) * r;
            tri(renderer, cx, cy, previousX, previousY, px, py, colour);
            previousX = px;
            previousY = py;
        }
    }

    private void ring(HudRenderer renderer, double cx, double cy, double r, double thickness, SettingColor colour) {
        ring(renderer, cx, cy, r, thickness, new Color(colour.r, colour.g, colour.b, colour.a));
    }

    private void ring(HudRenderer renderer, double cx, double cy, double r, double thickness, Color colour) {
        int segments = 16;
        double inner = Math.max(0.4, r - thickness);
        double outerX = cx + r;
        double outerY = cy;
        double innerX = cx + inner;
        double innerY = cy;
        for (int i = 1; i <= segments; i++) {
            double angle = Math.PI * 2.0 * i / segments;
            double ox = cx + Math.cos(angle) * r;
            double oy = cy + Math.sin(angle) * r;
            double ix = cx + Math.cos(angle) * inner;
            double iy = cy + Math.sin(angle) * inner;
            tri(renderer, outerX, outerY, ox, oy, ix, iy, colour);
            tri(renderer, outerX, outerY, ix, iy, innerX, innerY, colour);
            outerX = ox;
            outerY = oy;
            innerX = ix;
            innerY = iy;
        }
    }

    private void tri(HudRenderer renderer, double x1, double y1, double x2, double y2, double x3, double y3, Color colour) {
        double cross = (x2 - x1) * (y3 - y1) - (x3 - x1) * (y2 - y1);
        if (cross > 0.0) renderer.triangle(x1, y1, x3, y3, x2, y2, colour);
        else renderer.triangle(x1, y1, x2, y2, x3, y3, colour);
    }
}
