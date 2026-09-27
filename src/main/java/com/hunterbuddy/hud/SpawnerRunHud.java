package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.SpawnerDetector;
import com.hunterbuddy.util.HudPulse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
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
        "The spawner farm run's current step, in a choice of styles: a compact card, a single line, or the classic timeline band.",
        SpawnerRunHud::new);

    public enum HudStyle {
        TIMELINE,
        CARD,
        LINE
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgPanel = settings.createGroup("Panel");

    private final Setting<HudStyle> style = sgGeneral.add(new EnumSetting.Builder<HudStyle>()
        .name("style")
        .description("CARD is a compact readable status card. LINE is a single minimal line. TIMELINE is the classic time band with phase history.")
        .defaultValue(HudStyle.CARD).build());

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

    private final List<Segment> runSegments = new ArrayList<>();
    private final List<RunRecord> history = new ArrayList<>();
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

        SpawnerDetector detector = Modules.get().get(SpawnerDetector.class);
        boolean live = MeteorClient.mc.player != null && MeteorClient.mc.world != null
            && detector != null && detector.isActive();

        if (!live) {
            wasLive = false;
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

        Object site = detector.getAutoSite();
        String stateName = detector.getAutoStateName();
        boolean idleNow = "IDLE".equals(stateName) && site == null;

        updateRun(detector, site, stateName, now);
        insertion = updateInsertion(now);

        StepInfo step = computeStep(detector, stateName);
        updateWordDisplay(step, now);

        long elapsed = idleNow
            ? now - (lastRunEndMs != 0L ? lastRunEndMs : moduleActivatedMs)
            : now - stepSinceMs;

        switch (style.get()) {
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
            if (lastSiteRef != null) finishRun(now);
            if (site != null) {
                runSegments.clear();
                runStartMs = now;
                currentGroup = -1;
                runFailedCap = false;
                lastStateName = "";
            }
            lastSiteRef = site;
        } else if (site == null && (runStartMs != 0L || !runSegments.isEmpty())) {
            finishRun(now);
        }

        if (site == null) return;

        boolean failedNow = "FAILED".equals(stateName);
        int group = groupOf(stateName);
        boolean stateChanged = !stateName.equals(lastStateName);

        if (failedNow) {
            if (!runFailedCap) {
                closeCurrentSegment(now);
                runFailedCap = true;
            }
        } else if (group >= 0 && group != currentGroup) {
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

    private void finishRun(long now) {
        closeCurrentSegment(now);
        if (!runSegments.isEmpty()) {
            List<Frozen> frozen = new ArrayList<>();
            for (Segment s : runSegments) frozen.add(new Frozen(s.group, s.durationMs(now)));
            long total = Math.max(1L, now - runStartMs);
            RunRecord record = new RunRecord(frozen, total, runFailedCap);
            history.add(0, record);
            while (history.size() > 8) history.remove(history.size() - 1);
            insertion = new Insertion(frozen, total, runFailedCap, now);
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

    private SettingColor chronoColor(SpawnerDetector d, String state, long elapsed, long now) {
        if ("IDLE".equals(state)) return valueColor.get();
        boolean failedNow = "FAILED".equals(state);
        boolean exhausted = isExhausted(d, state);
        boolean immobile = now - lastMovedMs > IMMOBILE_MS && currentGroup >= 0;
        long avg = currentGroup >= 0 ? usualMs(currentGroup) : DEFAULT_AVG_MS[0];
        boolean slow = elapsed > 2L * avg;
        return failedNow || exhausted ? badColor.get() : immobile || slow ? amberColor.get() : valueColor.get();
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

    private static Color faded(SettingColor c, float alpha) {
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
        drawBand(renderer, x0, y, width, row2H, now, shadow, s, anim, runSegments, runStartMs > 0L ? now - runStartMs : 0L, runFailedCap, pausedForDisplay);
        y += row2H;

        if (historyRows > 0) {
            y += gap;
            renderer.text("last runs", x0, y, mutedColor.get(), shadow, s * 0.75);
            y += legendH;
            double maxTotal = maxHistTotal(now);
            double scale = maxTotal > 0.0 ? width / maxTotal : 1.0;
            for (int i = 0; i < historyRows; i++) {
                RunRecord r = history.get(i);
                if (insertion != null && i == 0 && insertion.totalMs == r.totalMs) continue;
                drawFrozenBand(renderer, x0, y, width, histH, r, scale);
                y += histH + histGap;
            }
            if (insertion != null) {
                double progress = anim ? smoothstep((now - insertion.startedMs) / (double) INSERT_ANIM_MS) : 1.0;
                double fromY = y0 + row1H + gap;
                double toY = y0 + row1H + gap + row2H + gap + legendH;
                double iy = fromY + (toY - fromY) * progress;
                double ih = row2H + (histH - row2H) * progress;
                double maxTotal2 = Math.max(maxTotal, insertion.totalMs);
                double scale2 = maxTotal2 > 0.0 ? width / maxTotal2 : 1.0;
                drawFrozenBand(renderer, x0, iy, width, ih, new RunRecord(insertion.segments, insertion.totalMs, insertion.failed), scale2);
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

        String totalText = formatSpan(liveTotalMs);
        double textH = renderer.textHeight(shadow, s * 0.8);
        double totalTextW = renderer.textWidth(totalText, shadow, s * 0.8);
        renderer.text(totalText, x + Math.max(0.0, w - totalTextW), y - textH - 1.0, valueColor.get(), shadow, s * 0.8);
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
            case CARD -> demoCard(renderer);
            case LINE -> demoLine(renderer);
            default -> demoTimeline(renderer);
        }
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
        double scale = width / total;
        double cx = x0;
        for (int i = 0; i < demoGroups.length; i++) {
            double w = demoDur[i] * scale;
            renderer.quad(cx, y, w, row2H, phaseColor(demoGroups[i]));
            cx += w;
        }
        drawHatch(renderer, cx - demoDur[3] * scale * 0.3, y, demoDur[3] * scale * 0.3, row2H, amberColor.get(), 0.0);
        String totalText = "1:08";
        renderer.text(totalText, x0 + width - renderer.textWidth(totalText, shadow, s * 0.8),
            y - renderer.textHeight(shadow, s * 0.8) - 1.0, valueColor.get(), shadow, s * 0.8);
        y += row2H + gap;

        renderer.text("last runs", x0, y, mutedColor.get(), shadow, s * 0.75);
        y += legendH;

        for (int r = 0; r < histRows; r++) {
            double hx = x0;
            double scale2 = width / (total * (1.0 + r * 0.15));
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
