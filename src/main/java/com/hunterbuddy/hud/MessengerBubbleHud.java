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
        "Messenger icon with selectable shapes and an unread notification badge. Click it in the chat to open the contact list.",
        MessengerBubbleHud::new);

    public enum Style {
        Flat,
        Glass
    }

    private static final long DRAWN_WINDOW_MS = 250L;
    private static final double RIPPLE_DELAY = 0.28;
    private static final double WOBBLE_AMPLITUDE = 0.07;
    private static final Identifier GLOW = Identifier.of("hunterbuddy", "textures/radar/glow.png");

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgPanel = settings.createGroup("Panel");

    private final Setting<Style> style = sgGeneral.add(new EnumSetting.Builder<Style>()
        .name("style").description("Flat draws a solid app button with the Panel halo. Glass adds translucent depth and reflections.")
        .defaultValue(Style.Flat).build());

    private final Setting<MessengerButtonArt.Shape> shape = sgGeneral.add(new EnumSetting.Builder<MessengerButtonArt.Shape>()
        .name("shape").description("Outline of the messenger button.")
        .defaultValue(MessengerButtonArt.Shape.RoundedSquare).build());

    private final Setting<MessengerButtonArt.Icon> icon = sgGeneral.add(new EnumSetting.Builder<MessengerButtonArt.Icon>()
        .name("icon").description("Drawn symbol inside the messenger button.")
        .defaultValue(MessengerButtonArt.Icon.ChatBubble).build());

    private final Setting<Integer> diameter = sgGeneral.add(new IntSetting.Builder()
        .name("diameter").description("Size of the messenger button in pixels.")
        .defaultValue(56).min(24).max(160).sliderRange(32, 100).build());

    private final Setting<Boolean> showWhenEmpty = sgGeneral.add(new BoolSetting.Builder()
        .name("show-when-empty").description("Keep the icon on screen without a badge when nothing is unread. Off, it only shows with unread messages or while the chat is open.")
        .defaultValue(true).build());

    private final Setting<Boolean> pulse = sgGeneral.add(new BoolSetting.Builder()
        .name("pulse").description("Pop the badge on a new message and pulse the outline while messages are unread. Also needs the module's animations setting on.")
        .defaultValue(true).build());

    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("text-scale").description("Scale of the notification count, limited to fit the badge.")
        .defaultValue(0.7).min(0.5).max(3.0).sliderRange(0.5, 2.5).build());

    private final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow").description("Render shadow behind text.").defaultValue(true).build());

    private final Setting<Boolean> dropHalo = sgGeneral.add(new BoolSetting.Builder()
        .name("drop-halo").description("Soft halo around the drop.")
        .defaultValue(true).visible(() -> style.get() == Style.Glass).build());

    private final Setting<Boolean> countBackdrop = sgGeneral.add(new BoolSetting.Builder()
        .name("count-backdrop").description("Dark backing under the icon so it stays readable on bright scenes.")
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
        .name("count-backdrop-color").description("The dark backing under the icon.")
        .defaultValue(new SettingColor(8, 16, 30, 140)).visible(() -> style.get() == Style.Glass && countBackdrop.get()).build());

    private final Setting<SettingColor> idleColor = sgColors.add(new ColorSetting.Builder()
        .name("idle-color").description("The flat button while nothing is unread.")
        .defaultValue(new SettingColor(34, 76, 106, 245)).build());

    private final Setting<SettingColor> unreadColor = sgColors.add(new ColorSetting.Builder()
        .name("unread-color").description("The flat button while messages are unread.")
        .defaultValue(new SettingColor(38, 123, 178, 250)).build());

    private final Setting<SettingColor> countIdleColor = sgColors.add(new ColorSetting.Builder()
        .name("count-idle-color").description("Icon color while nothing is unread.")
        .defaultValue(new SettingColor(226, 240, 250, 255)).build());

    private final Setting<SettingColor> badgeColor = sgColors.add(new ColorSetting.Builder()
        .name("badge-color").description("Fill of the unread notification badge and color of the animated outline.")
        .defaultValue(new SettingColor(244, 76, 101, 255)).build());

    private final Setting<SettingColor> iconColor = sgColors.add(new ColorSetting.Builder()
        .name("icon-color").description("Icon color while messages are unread.")
        .defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private final HudGlowPanel panel = new HudGlowPanel(sgPanel);
    private final HudPulse.Tracker tracker = new HudPulse.Tracker();
    private final Color ringColor = new Color();
    private final MessengerBadge badge = new MessengerBadge();
    private MessengerButtonArt art;
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
        double reserve = diameter.get() * (GlassDrop.SHADOW_SCALE - 1.0) / 2.0 + 2.0;
        return style.get() == Style.Glass ? reserve : Math.max(reserve, panel.padding());
    }

    public boolean hits(double px, double py) {
        Rect disc = hitRect();
        return badge.contains(px, py) || shape.get().contains(px - disc.x(), py - disc.y(), disc.w());
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

        if (art == null) art = new MessengerButtonArt(renderer);
        MessengerButtonArt.Shape outline = shape.get();
        if (style.get() == Style.Glass) {
            double shiver = wobble.get() && animate ? WOBBLE_AMPLITUDE * Math.sin(fresh * Math.PI * 3.0) * fresh : 0.0;
            art.glass(outline, left, top, d, hasUnread ? dropUnreadColor.get() : dropIdleColor.get(), dropHalo.get(), hover, shiver);
            if (ripple.get() && animate && fresh > 0.0f) {
                art.ripple(outline, left, top, d, accent, fresh);
                double progress = Math.min(1.0, Math.max(0.0, (1.0 - fresh - RIPPLE_DELAY) / (1.0 - RIPPLE_DELAY)));
                if (progress > 0.0) art.ripple(outline, left, top, d, accent, (float) (1.0 - progress));
            }
        } else {
            panel.drawRound(renderer, GLOW, left, top, d, hasUnread ? HudGlowPanel.Severity.WARN : HudGlowPanel.Severity.OK);
            art.flat(outline, left, top, d, hasUnread ? unreadColor.get() : idleColor.get(), hover);
        }

        if (hasUnread) {
            double breath = animate ? 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 420.0) : 0.0;
            art.outline(outline, left, top, d, ringColor.set(accent.r, accent.g, accent.b, (int) (80 + 70 * breath)),
                1.0 + 0.08 * breath);
        }
        Color backing = style.get() == Style.Glass && countBackdrop.get() ? countBackdropColor.get() : null;
        art.icon(outline, icon.get(), left, top, d, hasUnread ? iconColor.get() : countIdleColor.get(), backing);
        badge.draw(renderer, unread, left, top, d, textScale.get(), textShadow.get(), accent,
            animate ? HudPulse.pop(fresh) : 1.0);
    }
}
