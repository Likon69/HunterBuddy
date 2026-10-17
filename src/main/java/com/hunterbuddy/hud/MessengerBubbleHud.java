package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.messenger.Messenger;
import com.hunterbuddy.modules.messenger.ui.Pointer;
import com.hunterbuddy.modules.messenger.ui.Rect;
import com.hunterbuddy.util.HudGlowPanel;
import com.hunterbuddy.util.HudPulse;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.util.Identifier;

public class MessengerBubbleHud extends HudElement {
    public static final HudElementInfo<MessengerBubbleHud> INFO = new HudElementInfo<>(
        HunterBuddyAddon.HUD_GROUP, "MessengerBubble",
        "Round button counting unread private messages. Click it in the chat to open the contact list.",
        MessengerBubbleHud::new);

    public enum Style {
        Flat,
        Glass
    }

    private static final long DRAWN_WINDOW_MS = 250L;
    private static final double RIPPLE_DELAY = 0.28;
    private static final double WOBBLE_AMPLITUDE = 0.07;
    private static final Identifier DISC = Identifier.of("hunterbuddy", "textures/messenger/disc.png");
    private static final Identifier GLOW = Identifier.of("hunterbuddy", "textures/radar/glow.png");
    private static final Identifier RING = Identifier.of("hunterbuddy", "textures/radar/ring.png");

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgPanel = settings.createGroup("Panel");

    private final Setting<Style> style = sgGeneral.add(new EnumSetting.Builder<Style>()
        .name("style").description("Glass draws a translucent water drop. Flat draws the plain disc with the Panel halo.")
        .defaultValue(Style.Glass).build());

    private final Setting<Integer> diameter = sgGeneral.add(new IntSetting.Builder()
        .name("diameter").description("Size of the round button in pixels.")
        .defaultValue(56).min(24).max(160).sliderRange(32, 100).build());

    private final Setting<Boolean> showWhenEmpty = sgGeneral.add(new BoolSetting.Builder()
        .name("show-when-empty").description("Keep the button on screen with a 0 when nothing is unread. Off, it only shows with unread messages or while the chat is open.")
        .defaultValue(true).build());

    private final Setting<Boolean> pulse = sgGeneral.add(new BoolSetting.Builder()
        .name("pulse").description("Swell the counter on a new message and breathe a ring while messages are unread. Also needs the module's animations setting on.")
        .defaultValue(true).build());

    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("text-scale").description("Scale of the counter.")
        .defaultValue(1.25).min(0.5).max(3.0).sliderRange(0.5, 2.5).build());

    private final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow").description("Render shadow behind text.").defaultValue(true).build());

    private final Setting<Boolean> dropHalo = sgGeneral.add(new BoolSetting.Builder()
        .name("drop-halo").description("Soft halo around the drop.")
        .defaultValue(true).visible(() -> style.get() == Style.Glass).build());

    private final Setting<Boolean> countBackdrop = sgGeneral.add(new BoolSetting.Builder()
        .name("count-backdrop").description("Dark core under the counter so it stays readable on bright scenes.")
        .defaultValue(true).visible(() -> style.get() == Style.Glass).build());

    private final Setting<Boolean> ripple = sgGeneral.add(new BoolSetting.Builder()
        .name("ripple").description("Two rings spread from the drop when a message arrives. Also needs pulse and the animations setting of the module.")
        .defaultValue(true).visible(() -> style.get() == Style.Glass).build());

    private final Setting<Boolean> wobble = sgGeneral.add(new BoolSetting.Builder()
        .name("wobble").description("The drop shivers when a message arrives. Also needs pulse and the animations setting of the module.")
        .defaultValue(true).visible(() -> style.get() == Style.Glass).build());

    private final Setting<SettingColor> dropIdleColor = sgColors.add(new ColorSetting.Builder()
        .name("drop-idle-color").description("The drop while nothing is unread.")
        .defaultValue(new SettingColor(170, 205, 240, 255)).visible(() -> style.get() == Style.Glass).build());

    private final Setting<SettingColor> dropUnreadColor = sgColors.add(new ColorSetting.Builder()
        .name("drop-unread-color").description("The drop while messages are unread.")
        .defaultValue(new SettingColor(110, 190, 255, 255)).visible(() -> style.get() == Style.Glass).build());

    private final Setting<SettingColor> countBackdropColor = sgColors.add(new ColorSetting.Builder()
        .name("count-backdrop-color").description("The dark core under the counter.")
        .defaultValue(new SettingColor(8, 16, 30, 140)).visible(() -> style.get() == Style.Glass && countBackdrop.get()).build());

    private final Setting<SettingColor> idleColor = sgColors.add(new ColorSetting.Builder()
        .name("idle-color").description("The flat button while nothing is unread.")
        .defaultValue(new SettingColor(44, 54, 72, 235)).build());

    private final Setting<SettingColor> unreadColor = sgColors.add(new ColorSetting.Builder()
        .name("unread-color").description("The flat button while messages are unread.")
        .defaultValue(new SettingColor(40, 110, 170, 245)).build());

    private final Setting<SettingColor> countIdleColor = sgColors.add(new ColorSetting.Builder()
        .name("count-idle-color").description("The 0 while nothing is unread.")
        .defaultValue(new SettingColor(140, 152, 170, 255)).build());

    private final Setting<SettingColor> badgeColor = sgColors.add(new ColorSetting.Builder()
        .name("badge-color").description("Counter and ring while messages are unread.")
        .defaultValue(new SettingColor(255, 170, 60, 255)).build());

    private final HudGlowPanel panel = new HudGlowPanel(sgPanel);
    private final HudPulse.Tracker tracker = new HudPulse.Tracker();
    private final Color tintColor = new Color();
    private final Color ringColor = new Color();
    private GlassDrop drop;
    private long drawnAt;

    public MessengerBubbleHud() {
        super(INFO);
    }

    public static MessengerBubbleHud find() {
        Hud hud = Hud.get();
        if (hud == null || !hud.active) return null;

        for (HudElement element : hud) {
            if (element instanceof MessengerBubbleHud bubble && bubble.isDrawn()) return bubble;
        }
        return null;
    }

    private boolean isDrawn() {
        return isActive() && System.currentTimeMillis() - drawnAt < DRAWN_WINDOW_MS;
    }

    public Rect hitRect() {
        double pad = inset();
        return new Rect(x + pad, y + pad, diameter.get(), diameter.get());
    }

    private double inset() {
        return style.get() == Style.Glass ? diameter.get() * (GlassDrop.SHADOW_SCALE - 1.0) / 2.0 : panel.padding();
    }

    public boolean hits(double px, double py) {
        Rect disc = hitRect();
        double radius = disc.w() / 2.0;
        return Math.hypot(px - (disc.x() + radius), py - (disc.y() + radius)) <= radius;
    }

    @Override
    public void render(HudRenderer renderer) {
        Messenger messenger = Messenger.get();
        boolean live = messenger != null && messenger.isActive();
        boolean chatOpen = MeteorClient.mc.currentScreen instanceof ChatScreen;

        if (!live && !isInEditor()) {
            setSize(0.0, 0.0);
            return;
        }

        int unread = live ? messenger.unreadTotal() : 3;
        double d = diameter.get();
        double pad = inset();
        setSize(d + pad * 2.0, d + pad * 2.0);

        if (live && unread == 0 && !showWhenEmpty.get() && !chatOpen && !isInEditor()) return;

        boolean animate = pulse.get() && (!live || messenger.cfg().animations.get());
        boolean hasUnread = unread > 0;
        double left = x + pad;
        double top = y + pad;

        boolean hover = chatOpen && hits(Pointer.x(), Pointer.y());
        drawnAt = System.currentTimeMillis();

        SettingColor accent = badgeColor.get();
        float fresh = animate ? tracker.freshness("unread", String.valueOf(unread), 700L) : 0.0f;

        if (style.get() == Style.Glass) drawGlass(renderer, left, top, d, hasUnread, animate, hover, fresh, accent);
        else drawFlat(renderer, left, top, d, hasUnread, animate, hover, accent);

        double scale = textScale.get() * (animate ? HudPulse.pop(fresh) : 1.0);
        boolean shadow = textShadow.get();

        String label = unread > 99 ? "99+" : String.valueOf(unread);
        double tw = renderer.textWidth(label, shadow, scale);
        double th = renderer.textHeight(shadow, scale);

        Color color = hasUnread ? accent : countIdleColor.get();
        if (fresh > 0.0f) {
            color = HudPulse.tint(HudPulse.Style.Lively, toSetting(color), new SettingColor(255, 255, 255, 255), fresh);
        }

        renderer.text(label, left + (d - tw) / 2.0, top + (d - th) / 2.0, color, shadow, scale);
    }

    private void drawFlat(HudRenderer renderer, double left, double top, double d, boolean hasUnread,
                          boolean animate, boolean hover, SettingColor accent) {
        panel.drawRound(renderer, GLOW, left, top, d, hasUnread ? HudGlowPanel.Severity.WARN : HudGlowPanel.Severity.OK);

        SettingColor base = hasUnread ? unreadColor.get() : idleColor.get();
        int lift = hover ? 28 : 0;
        renderer.texture(DISC, left, top, d, d, new Color(
            Math.min(255, base.r + lift), Math.min(255, base.g + lift), Math.min(255, base.b + lift), base.a));

        if (hasUnread && animate) {
            double breath = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 420.0);
            double grow = 1.0 + 0.1 * breath;
            double size = d * grow;
            renderer.texture(RING, left - (size - d) / 2.0, top - (size - d) / 2.0, size, size,
                new Color(accent.r, accent.g, accent.b, (int) (90 + 120 * breath)));
        } else if (hasUnread) {
            renderer.texture(RING, left, top, d, d, accent);
        } else if (hover) {
            renderer.texture(RING, left, top, d, d, new Color(255, 255, 255, 110));
        }
    }

    private void drawGlass(HudRenderer renderer, double left, double top, double d, boolean hasUnread,
                           boolean animate, boolean hover, float fresh, SettingColor accent) {
        if (drop == null) drop = new GlassDrop(renderer);
        double cx = left + d / 2.0;
        double cy = top + d / 2.0;

        SettingColor base = hasUnread ? dropUnreadColor.get() : dropIdleColor.get();
        int lift = hover ? 28 : 0;
        Color tint = tintColor.set(
            Math.min(255, base.r + lift), Math.min(255, base.g + lift), Math.min(255, base.b + lift), base.a);

        double shiver = wobble.get() && animate ? WOBBLE_AMPLITUDE * Math.sin(fresh * Math.PI * 3.0) * fresh : 0.0;
        drop.draw(cx, cy, d, tint, dropHalo.get(), 1.0 + shiver, 1.0 - shiver, hover);

        if (ripple.get() && animate && fresh > 0.0f) {
            drop.ripple(cx, cy, d, accent, fresh);
            double progress = Math.min(1.0, Math.max(0.0, (1.0 - fresh - RIPPLE_DELAY) / (1.0 - RIPPLE_DELAY)));
            if (progress > 0.0) drop.ripple(cx, cy, d, accent, (float) (1.0 - progress));
        }

        if (hasUnread && animate) {
            double breath = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 420.0);
            double size = d * (1.0 + 0.1 * breath);
            renderer.texture(RING, cx - size / 2.0, cy - size / 2.0, size, size,
                ringColor.set(accent.r, accent.g, accent.b, (int) (45 + 60 * breath)));
        } else if (hasUnread) {
            renderer.texture(RING, left, top, d, d, ringColor.set(accent.r, accent.g, accent.b, 120));
        }

        if (countBackdrop.get()) drop.core(cx, cy, d, countBackdropColor.get());
    }

    private static SettingColor toSetting(Color color) {
        return new SettingColor(color.r, color.g, color.b, color.a);
    }
}
