package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.util.HudGlowPanel;
import com.hunterbuddy.util.HudPulse;
import com.hunterbuddy.util.HuntFeed;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

public class FindsTickerHud extends HudElement {
    public static final HudElementInfo<FindsTickerHud> INFO = new HudElementInfo<>(
        HunterBuddyAddon.HUD_GROUP, "FindsTicker",
        "Recent finds and sightings as a fading feed.",
        FindsTickerHud::new);

    public enum Align {
        Left,
        Right
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDisplay = settings.createGroup("Display");
    private final SettingGroup sgTypes = settings.createGroup("Types");
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgPanel = settings.createGroup("Panel");

    private final Setting<Integer> maxLines = sgGeneral.add(new IntSetting.Builder()
        .name("max-lines").description("How many entries to show.")
        .defaultValue(4).min(1).max(8).sliderRange(1, 8).build());

    private final Setting<Integer> maxAgeMinutes = sgGeneral.add(new IntSetting.Builder()
        .name("max-age").description("Entries older than this many minutes drop off.")
        .defaultValue(10).min(1).max(60).sliderRange(1, 30).build());

    private final Setting<Double> panelWidth = sgGeneral.add(new DoubleSetting.Builder()
        .name("width")
        .description("Fixed width of the feed. Fixed rather than fitted: entries come and go, and a panel that resized with each would never sit still.")
        .defaultValue(190.0).min(120.0).max(360.0).sliderRange(140.0, 300.0).build());

    private final Setting<Boolean> alertOnPlayer = sgGeneral.add(new BoolSetting.Builder()
        .name("alert-on-player").description("Critical glow for a minute after a player sighting.")
        .defaultValue(true).build());

    private final Setting<Boolean> mergeDuplicates = sgGeneral.add(new BoolSetting.Builder()
        .name("merge-duplicates")
        .description("Fold the same find seen again within a minute and a few blocks into one line with a count, instead of repeating it.")
        .defaultValue(true).build());

    private final Setting<Boolean> animations = sgGeneral.add(new BoolSetting.Builder()
        .name("animations")
        .description("Lines slide and fade in and out and the rest glide into place instead of snapping.")
        .defaultValue(true).build());

    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("text-scale").description("Scale of the text.")
        .defaultValue(1.0).min(0.5).max(2.0).sliderRange(0.5, 2.0).build());

    private final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow").description("Render shadow behind text.").defaultValue(true).build());

    private final Setting<Boolean> showDistance = sgDisplay.add(new BoolSetting.Builder()
        .name("show-distance")
        .description("How far each find is and which way, as a compass point.")
        .defaultValue(true).build());

    private final Setting<Boolean> showCoords = sgDisplay.add(new BoolSetting.Builder()
        .name("show-coords")
        .description("Show the find's exact x, z instead of a distance and bearing. Wants a wider panel.")
        .defaultValue(false).build());

    private final Setting<Align> align = sgDisplay.add(new EnumSetting.Builder<Align>()
        .name("align")
        .description("Which side the dot and the entry text sit on.")
        .defaultValue(Align.Left).build());

    private final Setting<Boolean> showStashes = sgTypes.add(new BoolSetting.Builder()
        .name("stashes").description("Stash detections.").defaultValue(true).build());
    private final Setting<Boolean> showSpawners = sgTypes.add(new BoolSetting.Builder()
        .name("spawners").description("Classified spawners.").defaultValue(true).build());
    private final Setting<Boolean> showPortals = sgTypes.add(new BoolSetting.Builder()
        .name("portals").description("Portals you lit.").defaultValue(true).build());
    private final Setting<Boolean> showPlayers = sgTypes.add(new BoolSetting.Builder()
        .name("players").description("Players entering and leaving range.").defaultValue(true).build());
    private final Setting<Boolean> showOldChunks = sgTypes.add(new BoolSetting.Builder()
        .name("old-chunks").description("Confirmed old chunk clusters.").defaultValue(true).build());
    private final Setting<Boolean> showItems = sgTypes.add(new BoolSetting.Builder()
        .name("items").description("Watched items seen on the ground.").defaultValue(true).build());
    private final Setting<Boolean> showCombat = sgTypes.add(new BoolSetting.Builder()
        .name("combat").description("Crystal and anchor activity nearby.").defaultValue(true).build());

    private final Setting<SettingColor> labelColor = sgColors.add(new ColorSetting.Builder()
        .name("age-color").description("The age column.")
        .defaultValue(new SettingColor(150, 150, 150, 255)).build());

    private final Setting<SettingColor> valueColor = sgColors.add(new ColorSetting.Builder()
        .name("text-color").description("The entry text.")
        .defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private final Setting<SettingColor> stashColor = sgColors.add(new ColorSetting.Builder()
        .name("stash-color").description("Stash dot.")
        .defaultValue(new SettingColor(110, 240, 130, 255)).build());

    private final Setting<SettingColor> spawnerColor = sgColors.add(new ColorSetting.Builder()
        .name("spawner-color").description("Spawner dot.")
        .defaultValue(new SettingColor(255, 200, 80, 255)).build());

    private final Setting<SettingColor> portalColor = sgColors.add(new ColorSetting.Builder()
        .name("portal-color").description("Portal dot.")
        .defaultValue(new SettingColor(150, 80, 220, 255)).build());

    private final Setting<SettingColor> playerColor = sgColors.add(new ColorSetting.Builder()
        .name("player-color").description("Player dot.")
        .defaultValue(new SettingColor(255, 80, 80, 255)).build());

    private final Setting<SettingColor> oldChunkColor = sgColors.add(new ColorSetting.Builder()
        .name("old-chunk-color").description("Old chunk dot.")
        .defaultValue(new SettingColor(126, 200, 255, 255)).build());

    private final Setting<SettingColor> itemColor = sgColors.add(new ColorSetting.Builder()
        .name("item-color").description("Watched item dot.")
        .defaultValue(new SettingColor(255, 85, 255, 255)).build());

    private final Setting<SettingColor> combatColor = sgColors.add(new ColorSetting.Builder()
        .name("combat-color").description("Crystal and anchor activity dot.")
        .defaultValue(new SettingColor(255, 100, 40, 255)).build());

    private final HudGlowPanel panel = new HudGlowPanel(sgPanel);
    private final HudPulse.Tracker pulse = new HudPulse.Tracker();

    private static final long ENTER_MS = 280L;
    private static final long EXIT_MS = 220L;
    private static final long REFRESH_MS = 50L;
    private static final long MERGE_WINDOW_MS = 60_000L;
    private static final int MERGE_RADIUS = 4;
    private static final double SLIDE_PX = 14.0;
    private static final double ROW_TAU = 0.08;
    private static final double HEIGHT_TAU = 0.1;
    private static final String ELLIPSIS = "...";

    private static final class Group {
        final HuntFeed.Entry newest;
        HuntFeed.Entry last;
        long oldestAt;
        int count = 1;

        Group(HuntFeed.Entry entry) {
            this.newest = entry;
            this.last = entry;
            this.oldestAt = entry.at();
        }
    }

    private static final class Slot {
        HuntFeed.Entry newest;
        int count;
        boolean claimed;
        boolean placed;
        double y;
        int target;
        long bornAt = -1L;
        long orderKey;
        long exitAt = -1L;

        String name;
        String where;
        String ageText;
        String countText;
        int countShown;
        String signature;
        long signatureAt;
        int signatureCount;

        String fitName;
        String fitCount;
        String fitted;
        double fitAvail;
        double fitScale;
        double fitCountW;
        boolean fitShadow;
    }

    private final List<Slot> slots = new ArrayList<>();
    private final List<Slot> scratchSlots = new ArrayList<>();
    private final List<Slot> liveSlots = new ArrayList<>();
    private final List<Group> groups = new ArrayList<>();
    private final Color scratch = new Color(255, 255, 255, 255);

    private long lastFrameNanos;
    private long lastRefreshMs;
    private double heightLines;
    private double slidePx;
    private String newestSignature = "";
    private boolean demoMode;

    public FindsTickerHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        long nowNanos = System.nanoTime();
        double dt = lastFrameNanos == 0L ? 0.016 : Math.min(0.25, (nowNanos - lastFrameNanos) / 1_000_000_000.0);
        lastFrameNanos = nowNanos;
        long nowMs = System.currentTimeMillis();
        boolean anim = animations.get();

        if (nowMs - lastRefreshMs >= REFRESH_MS) {
            lastRefreshMs = nowMs;
            refresh(nowMs, anim);
        }

        step(dt, nowMs, anim);

        if (slots.isEmpty() && heightLines < 0.02) {
            heightLines = 0.0;
            setSize(0.0, 0.0);
            return;
        }

        boolean shadow = textShadow.get();
        double scale = textScale.get();
        double pad = panel.padding();
        double lineH = renderer.textHeight(shadow, scale) + 2.0 * scale;
        double w = panelWidth.get();
        slidePx = Math.min(SLIDE_PX * scale, pad);
        double h = Math.max(0.0, heightLines * lineH - 2.0 * scale);

        HudGlowPanel.Severity severity = HudGlowPanel.Severity.OK;
        if (alertOnPlayer.get()) {
            long age = HuntFeed.get().ageOfNewest(HuntFeed.Type.PLAYER_ENTER);
            if (age >= 0 && age < 60_000L) severity = HudGlowPanel.Severity.CRITICAL;
        }

        panel.draw(renderer, x + pad, y + pad, w, h, severity);

        float freshness = anim ? pulse.freshness("newest", newestSignature, 700) : 0.0f;

        boolean whereOn = showDistance.get() || showCoords.get();
        double gap = 4.0 * scale;
        double ageSlot = renderer.textWidth("88 min", shadow, scale);
        double whereSlot = whereOn ? renderer.textWidth(whereTemplate(), shadow, scale) : 0.0;
        double textH = renderer.textHeight(shadow, scale);

        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            double top = y + pad + slot.y * lineH;
            drawSlot(renderer, slot, x + pad, top, w, shadow, scale, gap, ageSlot, whereSlot, textH,
                slot.target == 0 && slot.exitAt < 0L ? freshness : 0.0f, nowMs, anim);
        }

        setSize(w + pad * 2.0, h + pad * 2.0);
    }

    private void refresh(long nowMs, boolean anim) {
        List<HuntFeed.Entry> entries = collect(nowMs);
        demoMode = entries.isEmpty() && isInEditor();
        if (demoMode) entries = demoEntries();

        groups.clear();
        if (demoMode) {
            for (HuntFeed.Entry entry : entries) groups.add(new Group(entry));
        } else {
            groupEntries(entries);
        }

        boolean firstFill = slots.isEmpty();
        for (int i = 0; i < slots.size(); i++) slots.get(i).claimed = false;
        liveSlots.clear();

        for (int i = 0; i < groups.size(); i++) {
            Group group = groups.get(i);
            Slot slot = findSlot(group);

            if (slot == null) {
                slot = new Slot();
                slot.orderKey = group.oldestAt;
                if (!anim || (firstFill && group.newest.ageMs() >= 1000L)) slot.bornAt = nowMs - ENTER_MS;
            }

            slot.claimed = true;
            slot.exitAt = -1L;
            slot.newest = group.newest;
            slot.count = group.count;
            describe(slot, nowMs);

            int at = liveSlots.size();
            while (at > 0 && liveSlots.get(at - 1).orderKey < slot.orderKey) at--;
            liveSlots.add(at, slot);
        }

        scratchSlots.clear();
        scratchSlots.addAll(liveSlots);

        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.claimed || !anim || slot.bornAt < 0L) continue;

            if (slot.exitAt < 0L) slot.exitAt = nowMs;

            int at = 0;
            for (int j = i - 1; j >= 0; j--) {
                int found = scratchSlots.indexOf(slots.get(j));
                if (found >= 0) {
                    at = found + 1;
                    break;
                }
            }

            scratchSlots.add(at, slot);
        }

        slots.clear();
        slots.addAll(scratchSlots);
        layout();
    }

    private void layout() {
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            slot.target = i;

            if (!slot.placed) {
                slot.y = i;
                slot.placed = true;
            }
        }
    }

    private void step(double dt, long nowMs, boolean anim) {
        boolean removed = false;

        for (int i = slots.size() - 1; i >= 0; i--) {
            Slot slot = slots.get(i);

            if (slot.exitAt >= 0L && (!anim || nowMs - slot.exitAt >= EXIT_MS)) {
                slots.remove(i);
                removed = true;
            }
        }

        if (removed) layout();

        double rowAlpha = anim ? 1.0 - Math.exp(-dt / ROW_TAU) : 1.0;
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            slot.y = slot.bornAt < 0L ? slot.target : settle(slot.y, slot.target, rowAlpha);
        }

        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.bornAt < 0L && placeIsFree(slot)) slot.bornAt = nowMs;
        }

        double heightAlpha = anim ? 1.0 - Math.exp(-dt / HEIGHT_TAU) : 1.0;
        double needed = 0.0;
        Slot top = null;
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.bornAt >= 0L) needed = Math.max(needed, slot.y + enterProgress(slot, nowMs, anim));
            if (top == null && slot.exitAt < 0L) top = slot;
        }

        if (top != null && top.bornAt >= 0L) newestSignature = demoMode ? "demo" : top.signature;
        else if (top == null) newestSignature = "";

        heightLines = Math.max(needed, settle(heightLines, slots.size(), heightAlpha));
    }

    private static double enterProgress(Slot slot, long nowMs, boolean anim) {
        if (!anim) return 1.0;
        return easeOutCubic(MathHelper.clamp((nowMs - slot.bornAt) / (double) ENTER_MS, 0.0, 1.0));
    }

    private boolean placeIsFree(Slot waiting) {
        for (int i = 0; i < slots.size(); i++) {
            Slot other = slots.get(i);
            if (other != waiting && other.bornAt >= 0L && Math.abs(other.y - waiting.target) < 0.9) return false;
        }

        return true;
    }

    private static double settle(double current, double target, double alpha) {
        double next = current + (target - current) * alpha;
        return Math.abs(target - next) < 0.003 ? target : next;
    }

    private Slot findSlot(Group group) {
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.claimed) continue;

            if (demoMode) {
                if (slot.newest.type() == group.newest.type() && slot.newest.label().equals(group.newest.label())) {
                    return slot;
                }
                continue;
            }

            long at = slot.newest.at();
            if (at >= group.oldestAt && at <= group.newest.at() && sameSubject(slot.newest, group.newest)) return slot;
        }

        return null;
    }

    private void groupEntries(List<HuntFeed.Entry> entries) {
        int limit = maxLines.get();
        boolean merge = mergeDuplicates.get();

        for (int i = 0; i < entries.size(); i++) {
            HuntFeed.Entry entry = entries.get(i);
            Group target = null;

            if (merge) {
                for (int g = 0; g < groups.size(); g++) {
                    Group group = groups.get(g);
                    if (group.last.at() - entry.at() <= MERGE_WINDOW_MS && sameSubject(group.last, entry)) {
                        target = group;
                        break;
                    }
                }
            }

            if (target != null) {
                target.count++;
                target.last = entry;
                target.oldestAt = entry.at();
            } else if (groups.size() < limit) {
                groups.add(new Group(entry));
            }
        }
    }

    private static boolean sameSubject(HuntFeed.Entry a, HuntFeed.Entry b) {
        return a.type() == b.type()
            && Objects.equals(a.label(), b.label())
            && Objects.equals(a.dimension(), b.dimension())
            && near(a.pos(), b.pos());
    }

    private static boolean near(BlockPos a, BlockPos b) {
        if (a == null || b == null) return a == b;

        return Math.abs(a.getX() - b.getX()) <= MERGE_RADIUS
            && Math.abs(a.getY() - b.getY()) <= MERGE_RADIUS
            && Math.abs(a.getZ() - b.getZ()) <= MERGE_RADIUS;
    }

    private void describe(Slot slot, long nowMs) {
        slot.name = displayText(slot.newest);
        slot.where = where(slot.newest);
        slot.ageText = formatAge(Math.max(0L, nowMs - slot.newest.at()));

        if (slot.signature == null || slot.signatureAt != slot.newest.at() || slot.signatureCount != slot.count) {
            slot.signatureAt = slot.newest.at();
            slot.signatureCount = slot.count;
            slot.signature = slot.signatureAt + "|" + slot.signatureCount;
        }

        if (slot.count != slot.countShown) {
            slot.countShown = slot.count;
            slot.countText = slot.count > 1 ? "(" + slot.count + "x)" : null;
        }
    }

    private void drawSlot(HudRenderer renderer, Slot slot, double left, double top, double w, boolean shadow,
                          double scale, double gap, double ageSlot, double whereSlot, double textH,
                          float freshness, long nowMs, boolean anim) {
        if (slot.bornAt < 0L) return;

        long age = Math.max(0L, nowMs - slot.newest.at());
        boolean exiting = slot.exitAt >= 0L;

        double enterEase = enterProgress(slot, nowMs, anim);
        double exitT = exiting ? MathHelper.clamp((nowMs - slot.exitAt) / (double) EXIT_MS, 0.0, 1.0) : 0.0;

        double rankSpan = Math.max(1, maxLines.get() - 1);
        double rank = 1.0 - 0.4 * MathHelper.clamp(slot.y, 0.0, rankSpan) / rankSpan;
        double highlight = exiting ? 0.0 : MathHelper.clamp(1.0 - slot.y, 0.0, 1.0);

        double cover = MathHelper.clamp(heightLines - slot.y, 0.0, 1.0);
        double fade = ageFade(age) * rank * enterEase * (1.0 - exitT) * cover;
        if (fade <= 0.004) return;

        boolean right = align.get() == Align.Right;
        double slide = ((1.0 - enterEase) + exitT * 0.6) * slidePx;
        left = right ? left + slide : left - slide;

        SettingColor dot = colorFor(slot.newest.type());
        double dotSize = 3.0 * scale;
        boolean whereOn = whereSlot > 0.0;

        double dotX, textLeft, textRight, whereX, ageX;

        if (right) {
            ageX = left;
            whereX = ageX + ageSlot + gap;
            textLeft = whereOn ? whereX + whereSlot + gap : ageX + ageSlot + gap;
            dotX = left + w - dotSize;
            textRight = dotX - gap;
        } else {
            dotX = left;
            textLeft = dotX + dotSize + gap;
            ageX = left + w - ageSlot;
            double whereRight = ageX - gap;
            whereX = whereRight - whereSlot;
            textRight = whereOn ? whereX - gap : whereRight;
        }

        if (highlight > 0.01) {
            scratch.set(dot.r, dot.g, dot.b, alpha(30.0 * fade * highlight));
            renderer.quad(left, top - scale, w, textH + 2.0 * scale, scratch);
        }

        double popSize = dotSize * HudPulse.pop(freshness);
        scratch.set(dot.r, dot.g, dot.b, alpha(dot.a * fade));
        renderer.quad(dotX - (popSize - dotSize) / 2.0, top + (textH - popSize) / 2.0, popSize, popSize, scratch);

        fitLabel(renderer, slot, textRight - textLeft, shadow, scale, gap);

        double labelW = renderer.textWidth(slot.fitted, shadow, scale);
        double labelX;
        double countX;

        if (right) {
            labelX = textRight - labelW;
            countX = labelX - gap - slot.fitCountW;
        } else {
            labelX = textLeft;
            countX = labelX + labelW + gap;
        }

        SettingColor base = valueColor.get();
        double mix = freshness * 0.6;
        scratch.set(
            (int) (base.r + (dot.r - base.r) * mix),
            (int) (base.g + (dot.g - base.g) * mix),
            (int) (base.b + (dot.b - base.b) * mix),
            alpha(base.a * fade));
        renderer.text(slot.fitted, labelX, top, scratch, shadow, scale);

        if (slot.fitCount != null) {
            scratch.set(labelColor.get().r, labelColor.get().g, labelColor.get().b, alpha(labelColor.get().a * fade));
            renderer.text(slot.fitCount, countX, top, scratch, shadow, scale);
        }

        SettingColor ageBase = labelColor.get();
        scratch.set(ageBase.r, ageBase.g, ageBase.b, alpha(ageBase.a * fade));

        if (whereOn && slot.where != null) {
            double whereW = renderer.textWidth(slot.where, shadow, scale);
            renderer.text(slot.where, right ? whereX : whereX + whereSlot - whereW, top, scratch, shadow, scale);
        }

        renderer.text(slot.ageText, right ? ageX : ageX + ageSlot - renderer.textWidth(slot.ageText, shadow, scale),
            top, scratch, shadow, scale);
    }

    private void fitLabel(HudRenderer renderer, Slot slot, double available, boolean shadow, double scale, double gap) {
        available = Math.max(0.0, available);

        if (slot.fitted != null
            && slot.fitName.equals(slot.name)
            && Objects.equals(slot.fitCount, slot.countText)
            && Math.abs(slot.fitAvail - available) < 0.01
            && slot.fitScale == scale
            && slot.fitShadow == shadow) {
            return;
        }

        slot.fitName = slot.name;
        slot.fitCount = slot.countText;
        slot.fitAvail = available;
        slot.fitScale = scale;
        slot.fitShadow = shadow;
        slot.fitCountW = slot.countText == null ? 0.0 : renderer.textWidth(slot.countText, shadow, scale);

        double room = slot.countText == null ? available : Math.max(0.0, available - slot.fitCountW - gap);
        slot.fitted = fit(renderer, slot.name, room, shadow, scale);
    }

    private static int alpha(double value) {
        return MathHelper.clamp((int) value, 0, 255);
    }

    private static String displayText(HuntFeed.Entry entry) {
        return switch (entry.type()) {
            case PLAYER_ENTER -> entry.label() + " entered range";
            case PLAYER_LEAVE -> entry.label() + " left range";
            case PORTAL -> "Lit a portal";
            default -> entry.label();
        };
    }

    private String where(HuntFeed.Entry entry) {
        if (!showDistance.get() && !showCoords.get()) return null;
        if (entry.pos() == null) return "-";

        if (showCoords.get()) return entry.pos().getX() + ", " + entry.pos().getZ();

        if (MeteorClient.mc.player == null || MeteorClient.mc.world == null) return "-";

        String here = MeteorClient.mc.world.getRegistryKey().getValue().toString();
        if (entry.dimension() != null && !entry.dimension().equals(here)) return "-";

        double dx = entry.pos().getX() - MeteorClient.mc.player.getX();
        double dz = entry.pos().getZ() - MeteorClient.mc.player.getZ();

        return formatDistance(Math.sqrt(dx * dx + dz * dz)) + " " + bearing(dx, dz);
    }

    private String whereTemplate() {
        return showCoords.get() ? "-8888888, -8888888" : "8888km NW";
    }

    private static String formatDistance(double blocks) {
        if (blocks < 1000.0) return (int) blocks + "m";

        double km = blocks / 1000.0;
        return km < 10.0 ? String.format("%.1fkm", km) : (int) km + "km";
    }

    private static final String[] COMPASS = {"E", "SE", "S", "SW", "W", "NW", "N", "NE"};

    private static String bearing(double dx, double dz) {
        double degrees = (Math.toDegrees(Math.atan2(dz, dx)) + 360.0) % 360.0;
        return COMPASS[(int) Math.round(degrees / 45.0) % 8];
    }

    private List<HuntFeed.Entry> collect(long nowMs) {
        long cutoff = maxAgeMinutes.get() * 60_000L;
        List<HuntFeed.Entry> out = new ArrayList<>();

        for (HuntFeed.Entry entry : HuntFeed.get().entries()) {
            if (nowMs - entry.at() > cutoff) break;
            if (!enabled(entry.type())) continue;

            out.add(entry);
        }

        return out;
    }

    private boolean enabled(HuntFeed.Type type) {
        return switch (type) {
            case STASH -> showStashes.get();
            case SPAWNER -> showSpawners.get();
            case PORTAL -> showPortals.get();
            case PLAYER_ENTER, PLAYER_LEAVE -> showPlayers.get();
            case ITEM -> showItems.get();
            case DANGER -> showCombat.get();
            case OLD_CHUNK -> showOldChunks.get();
        };
    }

    private SettingColor colorFor(HuntFeed.Type type) {
        return switch (type) {
            case STASH -> stashColor.get();
            case SPAWNER -> spawnerColor.get();
            case PORTAL -> portalColor.get();
            case PLAYER_ENTER, PLAYER_LEAVE -> playerColor.get();
            case ITEM -> itemColor.get();
            case DANGER -> combatColor.get();
            case OLD_CHUNK -> oldChunkColor.get();
        };
    }

    private double ageFade(long age) {
        long maxAgeMs = maxAgeMinutes.get() * 60_000L;
        long lastFifthStart = (long) (maxAgeMs * 0.8);
        double plateauFloor = 0.55;

        if (age <= 30_000L) return 1.0;

        if (lastFifthStart <= 30_000L) {
            double t = MathHelper.clamp((age - 30_000L) / (double) Math.max(1L, maxAgeMs - 30_000L), 0.0, 1.0);
            return plateauFloor * (1.0 - t);
        }

        if (age < lastFifthStart) {
            double t = (age - 30_000L) / (double) (lastFifthStart - 30_000L);
            return 1.0 + (plateauFloor - 1.0) * t;
        }

        double t = MathHelper.clamp((age - lastFifthStart) / (double) Math.max(1L, maxAgeMs - lastFifthStart), 0.0, 1.0);
        return plateauFloor * (1.0 - t);
    }

    private static double easeOutCubic(double t) {
        double u = 1.0 - t;
        return 1.0 - u * u * u;
    }

    private static String formatAge(long ms) {
        long seconds = ms / 1000L;
        if (seconds < 60L) return seconds + " s";
        if (seconds < 3600L) return (seconds / 60L) + " min";
        return (seconds / 3600L) + " h";
    }

    private String fit(HudRenderer renderer, String label, double available, boolean shadow, double scale) {
        if (renderer.textWidth(label, shadow, scale) <= available) return label;

        int end = label.length();
        while (end > 0 && renderer.textWidth(label.substring(0, end) + ELLIPSIS, shadow, scale) > available) end--;

        return end == 0 ? "" : label.substring(0, end) + ELLIPSIS;
    }

    private List<HuntFeed.Entry> demoEntries() {
        long now = System.currentTimeMillis();
        List<HuntFeed.Entry> out = new ArrayList<>();

        String here = MeteorClient.mc.world == null
            ? null
            : MeteorClient.mc.world.getRegistryKey().getValue().toString();

        out.add(new HuntFeed.Entry(HuntFeed.Type.STASH, now - 2_000L, "Stash · 26 chests", demoPos(240, -240), here));
        out.add(new HuntFeed.Entry(HuntFeed.Type.ITEM, now - 45_000L, "Netherite Ingot ×2", demoPos(60, 30), here));
        out.add(new HuntFeed.Entry(HuntFeed.Type.SPAWNER, now - 120_000L, "Skeleton spawner", demoPos(1200, 60), here));
        out.add(new HuntFeed.Entry(HuntFeed.Type.DANGER, now - 200_000L, "crystal placed · Fit2Win",
            demoPos(-30, 12), here));
        out.add(new HuntFeed.Entry(HuntFeed.Type.PORTAL, now - 240_000L, "Portal lit", demoPos(-62, 62), here));
        out.add(new HuntFeed.Entry(HuntFeed.Type.PLAYER_ENTER, now - 360_000L, "Fit2Win", demoPos(0, -210), here));
        out.add(new HuntFeed.Entry(HuntFeed.Type.PLAYER_LEAVE, now - 480_000L, "ObamaCare", null, here));
        out.add(new HuntFeed.Entry(HuntFeed.Type.OLD_CHUNK, now - 660_000L, "Old chunks · cluster of 12",
            demoPos(-300, -300), "minecraft:the_nether"));

        return out.subList(0, Math.min(out.size(), maxLines.get()));
    }

    private static BlockPos demoPos(int dx, int dz) {
        if (MeteorClient.mc.player == null) return null;

        return BlockPos.ofFloored(
            MeteorClient.mc.player.getX() + dx, MeteorClient.mc.player.getY(), MeteorClient.mc.player.getZ() + dz);
    }
}
