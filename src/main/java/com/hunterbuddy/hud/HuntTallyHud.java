package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.util.HudGlowPanel;
import com.hunterbuddy.util.HudPulse;
import com.hunterbuddy.util.SessionStats;
import java.util.ArrayList;
import java.util.List;
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
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

/**
 * What this session has spent and found, as a tally.
 *
 * <p>Every number comes from {@link SessionStats}, which is the only thing watching the events —
 * so the counters cannot disagree with anything else that shows them, and they all reset
 * together when you join a server.
 */
public class HuntTallyHud extends HudElement {
    public static final HudElementInfo<HuntTallyHud> INFO = new HudElementInfo<>(
        HunterBuddyAddon.HUD_GROUP, "HuntTally",
        "Session tally: portals, ender chests, shulkers, rockets, stashes, blocks.",
        HuntTallyHud::new);

    public enum Layout {
        Inline,
        Stacked
    }

    public enum LabelMode {
        Text,
        Icon,
        Both
    }

    public enum Alignment {
        Left,
        Center,
        Right
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFields = settings.createGroup("Fields");
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgPanel = settings.createGroup("Panel");

    private final Setting<Layout> layout = sgGeneral.add(new EnumSetting.Builder<Layout>()
        .name("layout").description("One line, or one entry per line.")
        .defaultValue(Layout.Stacked).build());

    private final Setting<LabelMode> labelMode = sgGeneral.add(new EnumSetting.Builder<LabelMode>()
        .name("label-mode").description("Show the item icon, the written label, or both.")
        .defaultValue(LabelMode.Both).build());

    private final Setting<Boolean> iconOnLeft = sgGeneral.add(new BoolSetting.Builder()
        .name("icon-on-left").description("Put the icon before the label instead of after the value.")
        .defaultValue(true)
        .visible(() -> labelMode.get() != LabelMode.Text).build());

    private final Setting<Alignment> alignment = sgGeneral.add(new EnumSetting.Builder<Alignment>()
        .name("alignment")
        .description("How each line sits in the panel. Only Stacked is affected: an inline tally is one line that already fills its own width.")
        .defaultValue(Alignment.Center)
        .visible(() -> layout.get() == Layout.Stacked).build());

    private final Setting<Boolean> hideZero = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-zero")
        .description("Leave out anything still at zero, so the tally only lists what has actually happened.")
        .defaultValue(true).build());

    private final Setting<Double> iconScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("icon-scale").description("Size of the item icons.")
        .defaultValue(0.6).min(0.2).max(2.0).sliderRange(0.3, 1.5).build());

    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("text-scale").description("Scale of the text.")
        .defaultValue(1.0).min(0.5).max(2.0).sliderRange(0.5, 2.0).build());

    private final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow").description("Render shadow behind text.").defaultValue(true).build());

    private final Setting<Boolean> showPortals = sgFields.add(new BoolSetting.Builder()
        .name("show-portals").description("Nether portals lit.").defaultValue(true).build());

    private final Setting<Boolean> showEnderChests = sgFields.add(new BoolSetting.Builder()
        .name("show-ender-chests").description("Ender chests opened.").defaultValue(true).build());

    private final Setting<Boolean> showShulkerChests = sgFields.add(new BoolSetting.Builder()
        .name("show-shulker-chests").description("Containers found holding at least one shulker.")
        .defaultValue(true).build());

    private final Setting<Boolean> showShulkers = sgFields.add(new BoolSetting.Builder()
        .name("show-shulkers").description("Shulker boxes seen in those containers.")
        .defaultValue(true).build());

    private final Setting<Boolean> showRockets = sgFields.add(new BoolSetting.Builder()
        .name("show-rockets").description("Firework rockets used.").defaultValue(true).build());

    private final Setting<Boolean> showStashes = sgFields.add(new BoolSetting.Builder()
        .name("show-stashes").description("Stashes StashFinder reported.").defaultValue(true).build());

    private final Setting<Boolean> showXpBottles = sgFields.add(new BoolSetting.Builder()
        .name("show-xp-bottles").description("Experience bottles thrown.").defaultValue(true).build());

    private final Setting<Boolean> showBlocks = sgFields.add(new BoolSetting.Builder()
        .name("show-blocks").description("Blocks MlepMine broke.").defaultValue(true).build());

    private final Setting<Boolean> showJumps = sgFields.add(new BoolSetting.Builder()
        .name("show-jumps").description("Jumps this session.").defaultValue(true).build());

    private final Setting<Boolean> showDistance = sgFields.add(new BoolSetting.Builder()
        .name("show-distance").description("Ground covered this session.").defaultValue(true).build());

    private final Setting<Boolean> showTime = sgFields.add(new BoolSetting.Builder()
        .name("show-time").description("How long the session has been running.").defaultValue(true).build());

    private final Setting<SettingColor> labelColor = sgColors.add(new ColorSetting.Builder()
        .name("label-color").description("Labels and separators.")
        .defaultValue(new SettingColor(150, 150, 150, 255)).build());

    private final Setting<SettingColor> valueColor = sgColors.add(new ColorSetting.Builder()
        .name("value-color").description("The numbers.")
        .defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private final Setting<Boolean> animate = sgGeneral.add(new BoolSetting.Builder()
        .name("animate-changes")
        .description("React when a counter moves, so a find registers even when you were looking elsewhere.")
        .defaultValue(true).build());

    private final Setting<Anim> animationStyle = sgGeneral.add(
        new EnumSetting.Builder<Anim>()
            .name("animation-style")
            .description("Subtle tints the new number briefly. Lively also makes it hop. IconPop swells the icon instead. Flash takes the accent colour outright.")
            .defaultValue(Anim.Subtle)
            .visible(animate::get).build());

    private final Setting<Integer> animationMs = sgGeneral.add(new IntSetting.Builder()
        .name("animation-length").description("How long the reaction lasts, in milliseconds.")
        .defaultValue(400).min(100).max(2000).sliderRange(150, 1000)
        .visible(animate::get).build());

    private final Setting<SettingColor> accentColor = sgColors.add(new ColorSetting.Builder()
        .name("change-color").description("Colour a number takes on the instant it changes.")
        .defaultValue(new SettingColor(120, 240, 255, 255))
        .visible(animate::get).build());

    /**
     * How a counter reacts when it moves.
     *
     * <p>Subtle and Lively lead the list and keep their names, so a config written before the
     * other two existed still lands on the setting it chose.
     */
    public enum Anim {
        Subtle,
        Lively,
        IconPop,
        Flash
    }

    /** Which pulse style the number's tint borrows. Flash takes the accent at full weight. */
    private HudPulse.Style tintStyle() {
        Anim anim = animationStyle.get();
        return anim == Anim.Lively || anim == Anim.Flash ? HudPulse.Style.Lively : HudPulse.Style.Subtle;
    }

    /** Only Lively moves the number itself; the others leave the row where it is. */
    private HudPulse.Style bounceStyle() {
        return animationStyle.get() == Anim.Lively ? HudPulse.Style.Lively : HudPulse.Style.Subtle;
    }

    private final HudPulse.Tracker pulse = new HudPulse.Tracker();

    private final HudGlowPanel panel = new HudGlowPanel(this.sgPanel);

    /**
     * One line of the tally.
     *
     * <p>{@code portalSprite} is the odd one out: the nether portal has no item form, so it
     * cannot be drawn the way every other icon is. It gets the block's own texture instead.
     */
    private record Stat(String label, String value, ItemStack icon, boolean portalSprite) {
        Stat(String label, String value, ItemStack icon) {
            this(label, value, icon, false);
        }
    }

    public HuntTallyHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        boolean shadow = textShadow.get();
        double scale = textScale.get();
        double lineHeight = renderer.textHeight(shadow, scale);
        double inset = panel.padding();

        List<Stat> stats = collect();

        if (stats.isEmpty()) {
            // Nothing has happened yet and hide-zero is on. The editor still needs a handle, so
            // it gets the title rather than a zero-size element nobody can grab.
            if (isInEditor()) {
                double w = renderer.textWidth("HuntTally", shadow, scale);
                panel.draw(renderer, this.x + inset, this.y + inset, w, lineHeight, HudGlowPanel.Severity.OK);
                renderer.text("HuntTally", this.x + inset, this.y + inset, labelColor.get(), shadow, scale);
                setSize(w + inset * 2.0, lineHeight + inset * 2.0);
            } else {
                setSize(0.0, 0.0);
            }

            return;
        }

        double iconSize = 16.0 * iconScale.get();
        double rowHeight = Math.max(lineHeight, labelMode.get() == LabelMode.Text ? 0.0 : iconSize);

        if (layout.get() == Layout.Inline) {
            double width = measureInline(renderer, stats, shadow, scale, iconSize);
            panel.draw(renderer, this.x + inset, this.y + inset, width, rowHeight, HudGlowPanel.Severity.OK);
            drawInline(renderer, stats, this.x + inset, this.y + inset, rowHeight, shadow, scale, iconSize);
            setSize(width + inset * 2.0, rowHeight + inset * 2.0);
        } else {
            double width = measureStacked(renderer, stats, shadow, scale, iconSize);
            double height = rowHeight * stats.size();
            panel.draw(renderer, this.x + inset, this.y + inset, width, height, HudGlowPanel.Severity.OK);
            drawStacked(renderer, stats, this.x + inset, this.y + inset, rowHeight, width, shadow, scale, iconSize);
            setSize(width + inset * 2.0, height + inset * 2.0);
        }
    }

    /** The enabled, non-empty counters, in a fixed order so the list never reshuffles. */
    private List<Stat> collect() {
        SessionStats s = SessionStats.get();
        List<Stat> stats = new ArrayList<>();

        // Drawn from the block texture rather than stood in for by obsidian, which was only ever
        // the frame around the thing being counted.
        if (showPortals.get() && (!hideZero.get() || s.portalsBuilt() > 0)) {
            stats.add(new Stat("Portals", String.valueOf(s.portalsBuilt()),
                Items.CRYING_OBSIDIAN.getDefaultStack(), true));
        }
        add(stats, showEnderChests.get(), "EChests", s.enderChestsOpened(), Items.ENDER_CHEST);
        add(stats, showShulkerChests.get(), "Loot chests", s.shulkerChests(), Items.CHEST);
        add(stats, showShulkers.get(), "Shulkers", s.shulkersSeen(), Items.SHULKER_BOX);
        add(stats, showRockets.get(), "Rockets", s.rocketsUsed(), Items.FIREWORK_ROCKET);
        add(stats, showStashes.get(), "Stashes", s.stashesFound(), Items.WHITE_BANNER);
        add(stats, showXpBottles.get(), "XP", s.xpBottlesUsed(), Items.EXPERIENCE_BOTTLE);
        add(stats, showBlocks.get(), "Mined", s.blocksMined(), Items.NETHERITE_PICKAXE);
        add(stats, showJumps.get(), "Jumps", s.jumps(), Items.RABBIT_FOOT);

        if (showDistance.get()) {
            double blocks = s.distance();
            if (!hideZero.get() || blocks >= 1.0) {
                stats.add(new Stat("Travelled", formatDistance(blocks), Items.AIR.getDefaultStack()));
            }
        }

        if (showTime.get()) {
            long seconds = s.elapsedSeconds();
            if (!hideZero.get() || seconds > 0) {
                stats.add(new Stat("Session", formatTime(seconds), Items.AIR.getDefaultStack()));
            }
        }

        return stats;
    }

    private void add(List<Stat> stats, boolean enabled, String label, int value, net.minecraft.item.Item icon) {
        if (!enabled) return;
        if (hideZero.get() && value == 0) return;

        stats.add(new Stat(label, String.valueOf(value), icon.getDefaultStack()));
    }

    private double measureInline(HudRenderer renderer, List<Stat> stats, boolean shadow, double scale,
                                 double iconSize) {
        double w = 0.0;
        double sep = renderer.textWidth("  ", shadow, scale);

        for (int i = 0; i < stats.size(); i++) {
            w += entryWidth(renderer, stats.get(i), shadow, scale, iconSize);
            if (i < stats.size() - 1) w += sep;
        }

        return Math.max(w, 60.0);
    }

    private double measureStacked(HudRenderer renderer, List<Stat> stats, boolean shadow, double scale,
                                  double iconSize) {
        double w = 0.0;

        for (Stat stat : stats) {
            w = Math.max(w, entryWidth(renderer, stat, shadow, scale, iconSize));
        }

        return Math.max(w, 60.0);
    }

    /** One entry's width, laid out exactly as it will be drawn. */
    private double entryWidth(HudRenderer renderer, Stat stat, boolean shadow, double scale, double iconSize) {
        LabelMode mode = labelMode.get();
        double w = 0.0;

        // An empty stack is how distance and time say "no icon"; reserving room for it would
        // leave those two rows indented against nothing.
        boolean hasIcon = mode != LabelMode.Text && !stat.icon.isEmpty();

        if (hasIcon) w += iconSize + 2.0 * scale;
        if (mode != LabelMode.Icon) w += renderer.textWidth(stat.label + " ", shadow, scale);

        return w + renderer.textWidth(stat.value, shadow, scale);
    }

    private void drawInline(HudRenderer renderer, List<Stat> stats, double x, double y, double rowHeight,
                            boolean shadow, double scale, double iconSize) {
        double sep = renderer.textWidth("  ", shadow, scale);

        for (int i = 0; i < stats.size(); i++) {
            x = drawEntry(renderer, stats.get(i), x, y, rowHeight, shadow, scale, iconSize);
            if (i < stats.size() - 1) x += sep;
        }
    }

    /**
     * Draws the rows one under another, each shifted to sit where the alignment asks.
     *
     * <p>Every line is a different width — "Portals 3" against "Rockets 412" — so the panel takes
     * the widest and the others need a shift of their own to line up against it. Left is no
     * shift, centre is half the slack, right is all of it.
     */
    private void drawStacked(HudRenderer renderer, List<Stat> stats, double x, double y, double rowHeight,
                             double totalWidth, boolean shadow, double scale, double iconSize) {
        double factor = switch (alignment.get()) {
            case Left -> 0.0;
            case Center -> 0.5;
            case Right -> 1.0;
        };

        for (Stat stat : stats) {
            double slack = totalWidth - entryWidth(renderer, stat, shadow, scale, iconSize);
            drawEntry(renderer, stat, x + slack * factor, y, rowHeight, shadow, scale, iconSize);
            y += rowHeight;
        }
    }

    private double drawEntry(HudRenderer renderer, Stat stat, double x, double y, double rowHeight,
                             boolean shadow, double scale, double iconSize) {
        LabelMode mode = labelMode.get();
        boolean hasIcon = mode != LabelMode.Text && !stat.icon.isEmpty();
        double textY = y + (rowHeight - renderer.textHeight(shadow, scale)) / 2.0;

        // Read before anything is drawn: the icon reacts too, and on a left-hand icon it is drawn
        // before the number that the freshness used to be measured at.
        float freshness = animate.get()
            ? pulse.freshness(stat.label, stat.value, animationMs.get())
            : 0.0f;

        double iconY = y + (rowHeight - iconSize) / 2.0;

        if (hasIcon && iconOnLeft.get()) {
            drawPoppedIcon(renderer, stat, x, iconY, iconSize, freshness);
            x += iconSize + 2.0 * scale;
        }

        if (mode != LabelMode.Icon) {
            renderer.text(stat.label + " ", x, textY, labelColor.get(), shadow, scale);
            x += renderer.textWidth(stat.label + " ", shadow, scale);
        }

        renderer.text(stat.value, x,
            textY + HudPulse.bounce(bounceStyle(), freshness, scale),
            HudPulse.tint(tintStyle(), valueColor.get(), accentColor.get(), freshness),
            shadow, scale);
        x += renderer.textWidth(stat.value, shadow, scale);

        if (hasIcon && !iconOnLeft.get()) {
            x += 2.0 * scale;
            drawPoppedIcon(renderer, stat, x, iconY, iconSize, freshness);
            x += iconSize;
        }

        return x;
    }

    /**
     * The icon, swollen from its centre when the counter has just moved.
     *
     * <p>Grown about the middle rather than from the corner, so it never leans into the row above
     * or the field beside it. The layout is measured on the resting size throughout: an icon that
     * pushed its neighbours along as it grew would make the whole line breathe.
     */
    private void drawPoppedIcon(HudRenderer renderer, Stat stat, double x, double y, double size,
                                float freshness) {
        if (animationStyle.get() != Anim.IconPop || freshness <= 0.0f) {
            drawIcon(renderer, stat, x, y, size);
            return;
        }

        double grown = size * HudPulse.pop(freshness);
        double offset = (grown - size) / 2.0;

        drawIcon(renderer, stat, x - offset, y - offset, grown);
    }

    /**
     * The portal texture, shipped with the addon.
     *
     * <p>A single frame cut out of the vanilla {@code nether_portal.png}, which is sixteen by
     * five hundred and twelve — thirty-two frames stacked — and therefore unusable as-is: drawn
     * whole it collapses into a purple smear.
     */
    private static final Identifier PORTAL_TEXTURE =
        Identifier.of("hunterbuddy", "textures/portal.png");

    /**
     * Draws one icon, by whichever route its subject allows.
     *
     * <p>Items go through the item renderer like anything else. The portal has no item form, so
     * it is drawn as a plain texture through Meteor's own 2D renderer — the same path
     * {@code renderer.item} ends up on, and the one thing here that is known to reach the
     * screen from inside a HUD element.
     *
     * <p>Reading the sprite out of the block atlas was tried first and drew nothing at all, not
     * even the fallback, so the sprite was being found and then silently dropped somewhere
     * between the atlas and the frame. Shipping the frame as an asset removes every part of
     * that chain: no atlas, no pipeline, no animation state to be mid-update.
     */
    private void drawIcon(HudRenderer renderer, Stat stat, double x, double y, double size) {
        if (stat.portalSprite) {
            // Deferred, or the panel lands on top of it. Meteor buffers quads and flushes them
            // all at the end of the frame, while texture() draws the moment it is called — so
            // the icon went down first and the translucent panel was painted over it, which is
            // why this one looked washed out while the items beside it did not. Items escape it
            // by going through the draw context, which the game flushes later still.
            final double px = x;
            final double py = y;
            final double ps = size;
            renderer.post(() -> renderer.texture(PORTAL_TEXTURE, px, py, ps, ps, Color.WHITE));
            return;
        }

        renderer.item(stat.icon, (int) x, (int) y, iconScale.get().floatValue(), false);
    }

    private static String formatDistance(double blocks) {
        return blocks >= 1000.0 ? String.format("%.1f km", blocks / 1000.0) : String.format("%.0f m", blocks);
    }

    private static String formatTime(long seconds) {
        if (seconds >= 3600) return String.format("%dh %02dm", seconds / 3600, (seconds % 3600) / 60);
        if (seconds >= 60) return String.format("%dm", seconds / 60);
        return seconds + "s";
    }
}
