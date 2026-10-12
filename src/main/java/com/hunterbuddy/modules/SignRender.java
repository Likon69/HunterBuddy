package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.renderer.text.VanillaTextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

public class SignRender extends Module {
    private static final int PAD = 4;
    private static final int MAX_LINE_CHARS = 100;

    public enum ClusterMode {
        Stack,
        Cycle,
        Count,
        Smart;

        @Override
        public String toString() {
            return switch (this) {
                case Stack -> "Stack vertically";
                case Cycle -> "Cycle through signs";
                case Count -> "Show count only";
                case Smart -> "Smart layout";
            };
        }
    }

    private static final class SignInfo {
        final BlockPos block;
        final Vec3d center;
        final List<String> rows;
        final String flat;
        double dist;
        double sx;
        double sy;
        boolean onScreen;

        SignInfo(BlockPos block, List<String> rows) {
            this.block = block;
            this.center = Vec3d.ofCenter(block);
            this.rows = rows;
            this.flat = String.join(" ", rows).trim();
        }
    }

    private static final class Bunch {
        final List<SignInfo> members;
        final SignInfo lead;

        Bunch(List<SignInfo> members) {
            members.sort(Comparator.comparingDouble(s -> s.dist));
            this.members = members;
            this.lead = members.get(0);
        }

        double cx() {
            return lead.sx;
        }

        double cy() {
            return lead.sy;
        }
    }

    private static final class Rotor {
        int index;
        long stamp;
    }

    private final SettingGroup sgMain = settings.getDefaultGroup();
    private final SettingGroup sgLook = settings.createGroup("Render");
    private final SettingGroup sgGroup = settings.createGroup("Clustering");
    private final SettingGroup sgPerf = settings.createGroup("Optimization");

    private final Setting<Double> rangeLimit = sgMain.add(new DoubleSetting.Builder()
        .name("max-distance")
        .description("Farthest sign shown, in blocks.")
        .defaultValue(1024)
        .min(16)
        .max(1024)
        .sliderRange(16, 1024)
        .build()
    );

    private final Setting<Integer> signCap = sgMain.add(new IntSetting.Builder()
        .name("max-signs")
        .description("Most signs shown at once.")
        .defaultValue(500)
        .min(5)
        .max(500)
        .sliderRange(5, 1000)
        .build()
    );

    private final Setting<Boolean> skipBlank = sgMain.add(new BoolSetting.Builder()
        .name("filter-empty")
        .description("Skip signs with no text.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> stackLines = sgMain.add(new BoolSetting.Builder()
        .name("multiline-display")
        .description("Keep the sign's lines one under the other instead of joining them on one line.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> textTint = sgLook.add(new ColorSetting.Builder()
        .name("text-color")
        .description("Colour of the sign text.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> panelTint = sgLook.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Colour of the box behind the text.")
        .defaultValue(new SettingColor(0, 0, 0, 120))
        .build()
    );

    private final Setting<Boolean> panelOn = sgLook.add(new BoolSetting.Builder()
        .name("show-background")
        .description("Draw a box behind the text.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> vanillaFont = sgLook.add(new BoolSetting.Builder()
        .name("force-default-font")
        .description("Use Minecraft's font even when Meteor is set to a custom one.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> bunchOn = sgGroup.add(new BoolSetting.Builder()
        .name("enable-clustering")
        .description("Group signs that land close together on screen.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> bunchReach = sgGroup.add(new DoubleSetting.Builder()
        .name("cluster-radius")
        .description("Screen distance in pixels under which signs are grouped.")
        .defaultValue(100)
        .min(20)
        .max(500)
        .sliderRange(20, 200)
        .visible(bunchOn::get)
        .build()
    );

    private final Setting<ClusterMode> bunchStyle = sgGroup.add(new EnumSetting.Builder<ClusterMode>()
        .name("cluster-mode")
        .description("How a group of signs is shown.")
        .defaultValue(ClusterMode.Count)
        .visible(bunchOn::get)
        .build()
    );

    private final Setting<Integer> rotateMs = sgGroup.add(new IntSetting.Builder()
        .name("cycle-time")
        .description("Milliseconds each sign stays up when cycling.")
        .defaultValue(2000)
        .min(500)
        .max(10000)
        .sliderRange(500, 5000)
        .visible(() -> bunchOn.get() && bunchStyle.get() == ClusterMode.Cycle)
        .build()
    );

    private final Setting<Integer> bunchShown = sgGroup.add(new IntSetting.Builder()
        .name("max-cluster-display")
        .description("Most signs of a group shown together.")
        .defaultValue(5)
        .min(1)
        .max(10)
        .sliderRange(1, 10)
        .visible(() -> bunchOn.get() && bunchStyle.get() != ClusterMode.Count)
        .build()
    );

    private final Setting<Boolean> tallyOn = sgGroup.add(new BoolSetting.Builder()
        .name("show-cluster-count")
        .description("Show how many signs a group holds.")
        .defaultValue(true)
        .visible(bunchOn::get)
        .build()
    );

    private final Setting<SettingColor> tallyTint = sgGroup.add(new ColorSetting.Builder()
        .name("cluster-count-color")
        .description("Colour of that count.")
        .defaultValue(new SettingColor(255, 200, 100, 255))
        .visible(() -> bunchOn.get() && tallyOn.get())
        .build()
    );

    private final Setting<Double> stackGap = sgGroup.add(new DoubleSetting.Builder()
        .name("stack-spacing")
        .description("Gap in pixels between stacked signs.")
        .defaultValue(5)
        .min(0)
        .max(20)
        .sliderRange(0, 20)
        .visible(() -> bunchOn.get() && bunchStyle.get() == ClusterMode.Stack)
        .build()
    );

    private final Setting<Boolean> offscreenCull = sgPerf.add(new BoolSetting.Builder()
        .name("cull-off-screen")
        .description("Ignore signs that are off screen.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> nearestFirst = sgPerf.add(new BoolSetting.Builder()
        .name("prioritize-closest")
        .description("When there are too many signs, keep the closest.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> reuseReads = sgPerf.add(new BoolSetting.Builder()
        .name("cache-sign-text")
        .description("Only re-read the signs every few frames, and just move them in between.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> rereadEvery = sgPerf.add(new IntSetting.Builder()
        .name("update-interval")
        .description("Frames between two full re-reads of the signs.")
        .defaultValue(20)
        .min(1)
        .max(100)
        .sliderRange(1, 100)
        .visible(reuseReads::get)
        .build()
    );

    private final List<SignInfo> signs = new ArrayList<>();
    private final List<Bunch> bunches = new ArrayList<>();
    private final Map<BlockPos, Rotor> rotors = new HashMap<>();
    private final Rotor globalRotor = new Rotor();
    private int frameCount;
    private TextRenderer font;

    public SignRender() {
        super(HunterBuddyAddon.VISUALS_CATEGORY, "sign-reader", "Shows the text of nearby signs on screen, through walls, grouping the ones that overlap.", "sign-render");
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.world == null || mc.player == null) return;

        frameCount++;
        boolean full = !reuseReads.get() || frameCount >= rereadEvery.get();
        if (full) frameCount = 0;

        if (full) rescan();
        else refresh();

        bunches.clear();
        if (bunchOn.get() && !signs.isEmpty()) buildBunches();

        font = vanillaFont.get() ? VanillaTextRenderer.INSTANCE : TextRenderer.get();

        if (!bunches.isEmpty()) {
            Set<SignInfo> done = new HashSet<>();
            Set<BlockPos> liveKeys = new HashSet<>();
            for (Bunch b : bunches) drawBunch(b, done, liveKeys);
            rotors.keySet().retainAll(liveKeys);
            for (SignInfo s : signs) {
                if (s.onScreen && !done.contains(s)) drawSign(s, s.sx, s.sy);
            }
        } else if (bunchOn.get() && bunchStyle.get() == ClusterMode.Cycle && !signs.isEmpty()) {
            drawGlobalRotation();
        } else {
            for (SignInfo s : signs) {
                if (s.onScreen) drawSign(s, s.sx, s.sy);
            }
        }
    }

    private void rescan() {
        signs.clear();
        double limit = rangeLimit.get();
        for (BlockEntity be : Utils.blockEntities()) {
            if (!(be instanceof SignBlockEntity sign)) continue;
            BlockPos at = be.getPos();
            if (distanceTo(Vec3d.ofCenter(at)) > limit) continue;
            List<String> rows = readRows(sign);
            if (rows == null) continue;
            if (rows.isEmpty() && skipBlank.get()) continue;
            SignInfo info = new SignInfo(at, rows);
            locate(info);
            if (info.onScreen || !offscreenCull.get()) signs.add(info);
        }
        if (nearestFirst.get()) signs.sort(Comparator.comparingDouble(s -> s.dist));
        int cap = signCap.get();
        if (signs.size() > cap) signs.subList(cap, signs.size()).clear();
        if (globalRotor.index >= signs.size()) globalRotor.index = 0;
    }

    private void refresh() {
        for (SignInfo s : signs) locate(s);
        if (offscreenCull.get()) signs.removeIf(s -> !s.onScreen);
    }

    private void locate(SignInfo s) {
        s.dist = distanceTo(s.center);
        Vector3d v = new Vector3d(s.center.x, s.center.y + 0.5, s.center.z);
        if (NametagUtils.to2D(v, 1.0)) {
            s.sx = v.x;
            s.sy = v.y;
            s.onScreen = true;
        } else {
            s.onScreen = false;
        }
    }

    private double distanceTo(Vec3d p) {
        double dx = p.x - mc.player.getX();
        double dy = p.y - mc.player.getY();
        double dz = p.z - mc.player.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private List<String> readRows(SignBlockEntity sign) {
        try {
            List<String> rows = collect(sign.getFrontText());
            if (rows.isEmpty()) rows = collect(sign.getBackText());
            return rows;
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> collect(SignText face) {
        List<String> out = new ArrayList<>();
        for (Text t : face.getMessages(false)) {
            if (t == null) continue;
            String line = tidy(t.getString());
            if (!line.isEmpty()) out.add(line);
        }
        return out;
    }

    private static String tidy(String raw) {
        String s = raw.replaceAll("\\u00a7.", "");
        s = s.replaceAll("&[0-9a-fk-or]", "");
        if (s.contains("{\"") || s.contains("[\"")) {
            s = s.replaceAll("\\{\"[^\"]*\"\\s*:\\s*\"([^\"]*)\"[^}]*\\}", "$1");
            s = s.replaceAll("\\[\"([^\"]*)\"\\]", "$1");
        }
        s = s.replaceAll("\\{(?! )[^}]*\\}", "");
        s = s.replaceAll("[\\p{C}&&[^ ]]", "");
        s = s.replaceAll("[\\u0000-\\u001F\\u007F-\\u009F]", "");
        s = s.replaceAll("[\\[\\]{}\"']", "");
        s = s.replaceAll("\\s+", " ").trim();
        if (s.length() > MAX_LINE_CHARS) s = s.substring(0, MAX_LINE_CHARS - 3) + "...";
        return s;
    }

    private void buildBunches() {
        Set<SignInfo> taken = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        double reach = bunchReach.get();
        for (int i = 0; i < signs.size(); i++) {
            SignInfo seed = signs.get(i);
            if (!seed.onScreen || taken.contains(seed)) continue;
            taken.add(seed);
            List<SignInfo> found = new ArrayList<>();
            found.add(seed);
            for (int j = i + 1; j < signs.size(); j++) {
                SignInfo other = signs.get(j);
                if (!other.onScreen || taken.contains(other)) continue;
                if (Math.hypot(other.sx - seed.sx, other.sy - seed.sy) <= reach) {
                    taken.add(other);
                    found.add(other);
                }
            }
            if (found.size() >= 2) bunches.add(new Bunch(found));
        }
    }

    private void drawBunch(Bunch b, Set<SignInfo> done, Set<BlockPos> liveKeys) {
        double cx = b.cx();
        double cy = b.cy();
        int total = b.members.size();
        Color tally = tallyTint.get();
        switch (bunchStyle.get()) {
            case Stack -> {
                int shown = Math.min(total, bunchShown.get());
                double y = cy;
                for (int i = 0; i < shown; i++) {
                    SignInfo s = b.members.get(i);
                    drawSign(s, cx, y);
                    done.add(s);
                    y += heightOf(s) + stackGap.get();
                }
                if (total > shown && tallyOn.get()) drawLabel("+" + (total - shown) + " more", cx, y, 0.8, tally);
            }
            case Cycle -> {
                liveKeys.add(b.lead.block);
                Rotor r = rotors.computeIfAbsent(b.lead.block, k -> {
                    Rotor fresh = new Rotor();
                    fresh.stamp = System.currentTimeMillis();
                    return fresh;
                });
                advance(r, total);
                SignInfo cur = b.members.get(r.index);
                drawSign(cur, cx, cy);
                done.addAll(b.members);
                if (tallyOn.get()) drawLabel("[" + (r.index + 1) + "/" + total + "]", cx, cy + heightOf(cur) / 2 + 15, 0.7, tally);
            }
            case Count -> {
                drawSign(b.lead, cx, cy);
                done.addAll(b.members);
                drawLabel("(" + total + " signs)", cx, cy + heightOf(b.lead) / 2 + 10, 0.8, tally);
            }
            case Smart -> {
                int n = Math.min(total, bunchShown.get());
                if (n == 1) {
                    drawSign(b.members.get(0), cx, cy);
                    done.add(b.members.get(0));
                } else {
                    double radius = 30 + 5 * n;
                    for (int i = 0; i < n; i++) {
                        double angle = i * 2 * Math.PI / n - Math.PI / 2;
                        SignInfo s = b.members.get(i);
                        drawSign(s, cx + Math.cos(angle) * radius, cy + Math.sin(angle) * radius);
                        done.add(s);
                    }
                    if (tallyOn.get() && total > n) drawLabel("+" + (total - n), cx, cy, 0.9, tally);
                }
            }
        }
    }

    private void advance(Rotor r, int size) {
        long now = System.currentTimeMillis();
        if (r.index >= size) r.index = 0;
        if (now - r.stamp >= rotateMs.get()) {
            r.index = (r.index + 1) % size;
            r.stamp = now;
        }
    }

    private void drawGlobalRotation() {
        long now = System.currentTimeMillis();
        int size = signs.size();
        if (globalRotor.stamp == 0) {
            globalRotor.stamp = now;
        } else if (now - globalRotor.stamp >= rotateMs.get()) {
            globalRotor.index = (globalRotor.index + 1) % size;
            globalRotor.stamp = now;
        }
        if (globalRotor.index >= size) globalRotor.index = 0;
        SignInfo cur = signs.get(globalRotor.index);
        if (!cur.onScreen) return;
        drawSign(cur, cur.sx, cur.sy);
        if (tallyOn.get() && size > 1) {
            drawLabel("[" + (globalRotor.index + 1) + "/" + size + "]", cur.sx, cur.sy + heightOf(cur) / 2 + 15, 0.7, tallyTint.get());
        }
    }

    private boolean multiline(SignInfo s) {
        return stackLines.get() && !s.rows.isEmpty();
    }

    private double lineHeight(double scale) {
        font.begin(scale, false, true);
        double h = font.getHeight();
        font.end();
        return h;
    }

    private double textWidth(String text, double scale) {
        font.begin(scale, false, true);
        double w = font.getWidth(text);
        font.end();
        return w;
    }

    private double heightOf(SignInfo s) {
        double lh = lineHeight(1);
        if (multiline(s)) return s.rows.size() * lh + 2 * PAD;
        return lh + 2 * PAD;
    }

    private void drawSign(SignInfo s, double x, double y) {
        Color tint = textTint.get();
        if (multiline(s)) {
            double lh = lineHeight(1);
            double widest = 0;
            for (String row : s.rows) widest = Math.max(widest, textWidth(row, 1));
            double boxW = widest + 2 * PAD;
            double boxH = s.rows.size() * lh + 2 * PAD;
            double left = x - boxW / 2;
            double top = y - boxH / 2;
            fillBox(left, top, boxW, boxH, tint);
            font.begin(1, false, true);
            double ty = top + PAD;
            for (String row : s.rows) {
                if (row.isEmpty()) continue;
                font.render(row, left + (boxW - font.getWidth(row)) / 2, ty, tint);
                ty += lh;
            }
            font.end();
        } else if (!s.flat.isEmpty()) {
            drawLabel(s.flat, x, y, 1, tint);
        }
    }

    private void drawLabel(String text, double x, double y, double scale, Color color) {
        double w = textWidth(text, scale) + 2 * PAD;
        double h = lineHeight(scale) + 2 * PAD;
        double left = x - w / 2;
        double top = y - h / 2;
        fillBox(left, top, w, h, color);
        font.begin(scale, false, true);
        font.render(text, left + PAD, top + PAD, color);
        font.end();
    }

    private void fillBox(double x, double y, double w, double h, Color textColor) {
        if (!panelOn.get()) return;
        Color base = panelTint.get();
        Color fill = new Color(base.r, base.g, base.b, (int) Math.round(base.a * (textColor.a / 255.0)));
        Renderer2D.COLOR.begin();
        Renderer2D.COLOR.quad(x, y, w, h, fill);
        Renderer2D.COLOR.render();
    }
}
