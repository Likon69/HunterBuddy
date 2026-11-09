package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.hud.toast.ModuleToastBlur;
import com.hunterbuddy.hud.toast.ModuleToastFeed;
import com.hunterbuddy.hud.toast.ModuleToastInbox;
import com.hunterbuddy.hud.toast.ModuleToastPainter;
import com.hunterbuddy.hud.toast.ModuleToastTimeline;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public final class ModuleToastHud extends HudElement {
    public static final HudElementInfo<ModuleToastHud> INFO = new HudElementInfo<>(HunterBuddyAddon.HUD_GROUP,
        "ModuleToast", "Fading glass notifications for module state changes and their keybinds.", ModuleToastHud::new);
    private static final ModuleToastTimeline.Notice PREVIEW_ON = new ModuleToastTimeline.Notice("Elytra Auto Fly", "R", true);
    private static final ModuleToastTimeline.Notice PREVIEW_OFF = new ModuleToastTimeline.Notice("Kill Aura Plus", "Ctrl + G", false);
    private final SettingGroup general = settings.getDefaultGroup();
    private final SettingGroup colors = settings.createGroup("Colors");
    private final Setting<Double> duration = general.add(new DoubleSetting.Builder().name("duration")
        .description("Seconds each notification remains visible, including its fade.").defaultValue(4).min(1).max(15).sliderRange(2, 8).build());
    private final Setting<Integer> maximum = general.add(new IntSetting.Builder().name("visible-cards")
        .description("Maximum simultaneous notifications; additional changes wait in order.").defaultValue(3).min(1).max(6).sliderRange(1, 6).build());
    private final Setting<Double> width = general.add(new DoubleSetting.Builder().name("width")
        .description("Card width before scaling.").defaultValue(300).min(220).max(600).sliderRange(240, 420).build());
    private final Setting<Double> scale = general.add(new DoubleSetting.Builder().name("scale")
        .description("Scale of the cards.").defaultValue(1).min(0.5).max(2).sliderRange(0.6, 1.5).build());
    private final Setting<Double> textScale = general.add(new DoubleSetting.Builder().name("text-scale")
        .description("Size of the text, independent of the card scale.").defaultValue(1).min(0.5).max(2).sliderRange(0.6, 1.5).build());
    private final Setting<Boolean> compact = general.add(new BoolSetting.Builder().name("compact")
        .description("One-line cards without the state caption.").defaultValue(false).build());
    private final Setting<Boolean> boundOnly = general.add(new BoolSetting.Builder().name("bound-only")
        .description("Only show modules with an assigned keyboard or mouse bind.").defaultValue(false).build());
    private final Setting<Boolean> upward = general.add(new BoolSetting.Builder().name("grow-upward")
        .description("Anchor the first card at the bottom and stack new cards upward.").defaultValue(false).build());
    private final Setting<Boolean> blur = general.add(new BoolSetting.Builder().name("background-blur")
        .description("Blur the actual scene behind each card, limited to its rounded bounds.").defaultValue(true).build());
    private final Setting<Integer> blurStrength = general.add(new IntSetting.Builder().name("blur-strength")
        .description("Background blur sampling radius.").defaultValue(2).min(1).max(5).sliderRange(1, 5).visible(blur::get).build());
    private final Setting<Boolean> reflection = general.add(new BoolSetting.Builder().name("glass-reflection")
        .description("Add a soft reflected highlight to the translucent cards.").defaultValue(true).build());
    private final Setting<SettingColor> enabledColor = colors.add(new ColorSetting.Builder().name("on-color")
        .description("Accent for enabled modules.").defaultValue(new SettingColor(106, 231, 193, 255)).build());
    private final Setting<SettingColor> disabledColor = colors.add(new ColorSetting.Builder().name("off-color")
        .description("Accent for disabled modules.").defaultValue(new SettingColor(244, 137, 161, 255)).build());
    private final Setting<SettingColor> background = colors.add(new ColorSetting.Builder().name("background")
        .description("Glass background, including its opacity.").defaultValue(new SettingColor(17, 24, 35, 222)).build());
    private final Setting<SettingColor> text = colors.add(new ColorSetting.Builder().name("text-color")
        .description("Notification text, including its opacity.").defaultValue(new SettingColor(236, 243, 252, 255)).build());
    private final ModuleToastInbox inbox = new ModuleToastInbox(ModuleToastFeed.INSTANCE.history);
    private final ModuleToastTimeline timeline = inbox.timeline;
    private final ModuleToastPainter painter = new ModuleToastPainter();

    public ModuleToastHud() {
        super(INFO);
        setSize(312, 220);
    }

    @Override
    public void render(HudRenderer renderer) {
        double s = scale.get();
        double w = width.get() * s;
        double cardHeight = ModuleToastPainter.height(renderer, s, textScale.get(), compact.get());
        double stride = cardHeight + 8 * s;
        double h = maximum.get() * stride - 8 * s;
        double pad = 7 * s;
        setSize(w + pad * 2, h + pad * 2);
        long now = System.nanoTime();
        inbox.read(boundOnly.get());
        if (blur.get()) ModuleToastBlur.INSTANCE.begin(blurStrength.get());
        timeline.update(now, maximum.get(), duration.get());
        if (isInEditor()) {
            int count = Math.min(2, maximum.get());
            for (int i = 0; i < count; i++) {
                ModuleToastTimeline.Notice notice = i == 0 ? PREVIEW_ON : PREVIEW_OFF;
                double rowY = upward.get() ? h - cardHeight - i * stride : i * stride;
                painter.draw(renderer, i, notice, x + pad, y + pad + rowY, w, cardHeight, s, textScale.get(),
                    compact.get(), 1, i == 0 ? 0.76 : 0.43,
                    reflection.get(), blur.get(), notice.enabled() ? enabledColor.get() : disabledColor.get(), background.get(), text.get());
            }
            return;
        }
        for (int i = 0; i < timeline.size(); i++) {
            ModuleToastTimeline.Slot slot = timeline.get(i);
            double alpha = slot.opacity(now, duration.get());
            double rowY = upward.get() ? h - cardHeight - slot.row * stride : slot.row * stride;
            painter.draw(renderer, i, slot.notice, x + pad + (1 - alpha) * 14 * s, y + pad + rowY,
                w, cardHeight, s, textScale.get(), compact.get(), alpha, slot.remaining(now, duration.get()), reflection.get(), blur.get(),
                slot.notice.enabled() ? enabledColor.get() : disabledColor.get(), background.get(), text.get());
        }
    }

    @Override
    public void onFontChanged() {
        painter.invalidate();
    }
}
