package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.hud.glass.GlassPainter;
import com.hunterbuddy.hud.glass.GlassStyle;
import com.hunterbuddy.hud.text.HudText;
import com.hunterbuddy.hud.text.HudTextScale;
import com.hunterbuddy.hud.toast.ModuleToastBlur;
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
import net.minecraft.world.World;

public final class CoordsHud extends HudElement {
    public static final HudElementInfo<CoordsHud> INFO = new HudElementInfo<>(HunterBuddyAddon.HUD_GROUP,
        "Coords", "Current coordinates and their Overworld or Nether equivalent.", CoordsHud::new);
    private static final String[] DIGITS = { "0", "1", "2", "3", "4", "5", "6", "7", "8", "9" };

    public enum Show {
        Both,
        CurrentOnly,
        OppositeOnly;

        @Override
        public String toString() {
            return switch (this) {
                case Both -> "Both";
                case CurrentOnly -> "Current only";
                case OppositeOnly -> "Opposite only";
            };
        }
    }

    private final SettingGroup general = settings.getDefaultGroup();
    private final Setting<GlassStyle> style = general.add(new EnumSetting.Builder<GlassStyle>()
        .name("style").description("Glass: rounded glass card. Flat: square flat panel.")
        .defaultValue(GlassStyle.Glass).build());
    private final Setting<Show> show = general.add(new EnumSetting.Builder<Show>()
        .name("show").description("Choose current coordinates, opposite coordinates, or both.")
        .defaultValue(Show.Both).build());
    private final Setting<Boolean> showY = general.add(new BoolSetting.Builder()
        .name("show-y").description("Include the Y coordinate.").defaultValue(true).build());
    private final Setting<Integer> decimals = general.add(new IntSetting.Builder()
        .name("decimals").description("Decimal places for coordinates.")
        .defaultValue(0).min(0).max(3).sliderRange(0, 3).build());
    private final Setting<Double> scale = general.add(new DoubleSetting.Builder()
        .name("scale").description("Scale of the card and spacing, independent of text size.")
        .defaultValue(1).min(0.5).max(2).sliderRange(0.6, 1.5).build());
    private final Setting<Boolean> backgroundBlur = general.add(new BoolSetting.Builder()
        .name("background-blur").description("Blur the actual scene behind the card.")
        .defaultValue(true).build());
    private final Setting<SettingColor> background = general.add(new ColorSetting.Builder()
        .name("background").description("Card background color and opacity.")
        .defaultValue(new SettingColor(17, 24, 35, 222)).build());
    private final Setting<SettingColor> textColor = general.add(new ColorSetting.Builder()
        .name("text-color").description("Coordinate text color and opacity.")
        .defaultValue(new SettingColor(236, 243, 252, 255)).build());
    private final Setting<SettingColor> accentColor = general.add(new ColorSetting.Builder()
        .name("accent-color").description("Left accent color in Glass style.")
        .defaultValue(new SettingColor(126, 200, 255, 235)).build());

    private final GlassPainter glass = new GlassPainter();
    private final Row current = new Row();
    private final Row opposite = new Row();
    private final Color muted = new Color();
    private final StringBuilder template = new StringBuilder(64);
    private String dimension = "Overworld";
    private String otherDimension = "Nether";
    private double labelWidth;
    private double valueWidth;
    private double currentHeight;
    private double oppositeHeight;
    private int measuredDecimals = -1;
    private boolean measuredY;
    private boolean measured;

    public CoordsHud() {
        super(INFO);
        update(1234.5, 64, -9876.5);
    }

    @Override
    public void tick(HudRenderer renderer) {
        if (MeteorClient.mc.player == null || MeteorClient.mc.world == null) return;
        var key = MeteorClient.mc.world.getRegistryKey();
        dimension = key.equals(World.NETHER) ? "Nether" : key.equals(World.END) ? "End"
            : key.equals(World.OVERWORLD) ? "Overworld" : "Other";
        otherDimension = key.equals(World.NETHER) ? "Overworld" : key.equals(World.OVERWORLD) ? "Nether" : null;
        update(MeteorClient.mc.player.getX(), MeteorClient.mc.player.getY(), MeteorClient.mc.player.getZ());
    }

    private void update(double px, double py, double pz) {
        int precision = decimals.get();
        current.update(px, py, pz, precision, showY.get());
        if (otherDimension != null) {
            double factor = dimension.equals("Nether") ? 8 : 0.125;
            opposite.update(px * factor, py, pz * factor, precision, showY.get());
        }
    }

    @Override
    public void render(HudRenderer renderer) {
        if (MeteorClient.mc.player == null || MeteorClient.mc.world == null) {
            if (!HudEditing.active()) {
                setSize(0, 0);
                return;
            }
            dimension = "Overworld";
            otherDimension = "Nether";
            update(1234.5, 64, -9876.5);
        }
        measure(renderer);
        boolean first = otherDimension == null || show.get() != Show.OppositeOnly;
        boolean second = otherDimension != null && show.get() != Show.CurrentOnly;
        double s = scale.get();
        double padX = 14 * s;
        double padY = Math.max(6 * s, GlassPainter.radius(s) * 0.6);
        double gap = 4 * s;
        double rowHeight = Math.max(currentHeight, oppositeHeight);
        double width = padX * 2 + labelWidth + 8 * s + valueWidth;
        double height = padY * 2 + (first ? rowHeight : 0) + (second ? rowHeight : 0)
            + (first && second ? gap : 0);
        boolean flat = style.get() == GlassStyle.Flat;
        setSize(width, height);
        if (backgroundBlur.get()) {
            ModuleToastBlur.INSTANCE.begin(2);
            ModuleToastBlur.INSTANCE.draw(x, y, width, height, flat ? 0 : GlassPainter.radius(s), 1);
        }
        if (flat) glass.flatCard(renderer, x, y, width, height, s, 1, background.get());
        else {
            glass.card(renderer, x, y, width, height, s, 1, true, background.get());
            glass.accentBar(renderer, x, y, height, s, 1, accentColor.get());
        }
        SettingColor ink = textColor.get();
        muted.set(ink.r, ink.g, ink.b, (int) Math.round(ink.a * 0.65));
        double top = y + padY;
        double valueX = x + padX + labelWidth + 8 * s;
        if (first) {
            drawRow(renderer, dimension, current.text, x + padX, valueX, top, rowHeight,
                HudTextScale.NORMAL, ink);
            top += rowHeight + gap;
        }
        if (second) drawRow(renderer, otherDimension, opposite.text, x + padX, valueX, top, rowHeight,
            HudTextScale.SMALL, muted);
    }

    private void drawRow(HudRenderer renderer, String label, String value, double labelX, double valueX,
                         double top, double height, double textScale, Color ink) {
        HudText.text(renderer, label, labelX, top + (height - oppositeHeight) / 2, muted, false, HudTextScale.SMALL);
        String visible = HudText.textWidth(renderer, value, false, textScale) <= valueWidth ? value : "...";
        HudText.text(renderer, visible, valueX, top + (height - HudText.textHeight(renderer, false, textScale)) / 2,
            ink, false, textScale);
    }

    private void measure(HudRenderer renderer) {
        int precision = decimals.get();
        boolean withY = showY.get();
        if (measured && measuredDecimals == precision && measuredY == withY) return;
        measured = true;
        measuredDecimals = precision;
        measuredY = withY;
        labelWidth = HudText.textWidth(renderer, "Overworld", false, HudTextScale.SMALL);
        currentHeight = HudText.textHeight(renderer, false, HudTextScale.NORMAL);
        oppositeHeight = HudText.textHeight(renderer, false, HudTextScale.SMALL);
        int widest = 0;
        double digitWidth = 0;
        for (int i = 0; i < DIGITS.length; i++) {
            double width = HudText.textWidth(renderer, DIGITS[i], false, HudTextScale.NORMAL);
            if (width > digitWidth) {
                widest = i;
                digitWidth = width;
            }
        }
        template.setLength(0);
        for (int axis = 0; axis < (withY ? 3 : 2); axis++) {
            if (axis > 0) template.append(", ");
            template.append('-');
            for (int i = 0; i < 9; i++) template.append(DIGITS[widest]);
            if (precision > 0) {
                template.append('.');
                for (int i = 0; i < precision; i++) template.append(DIGITS[widest]);
            }
        }
        valueWidth = HudText.textWidth(renderer, template.toString(), false, HudTextScale.NORMAL);
    }

    @Override
    public void onFontChanged() {
        measured = false;
    }

    static final class Row {
        private final StringBuilder buffer = new StringBuilder(64);
        private long x = Long.MIN_VALUE;
        private long y = Long.MIN_VALUE;
        private long z = Long.MIN_VALUE;
        private int decimals = -1;
        private boolean showY;
        String text = "";

        void update(double px, double py, double pz, int precision, boolean withY) {
            int factor = switch (precision) {
                case 1 -> 10;
                case 2 -> 100;
                case 3 -> 1000;
                default -> 1;
            };
            long nx = Math.round(px * factor);
            long ny = withY ? Math.round(py * factor) : 0;
            long nz = Math.round(pz * factor);
            if (x == nx && y == ny && z == nz && decimals == precision && showY == withY) return;
            x = nx;
            y = ny;
            z = nz;
            decimals = precision;
            showY = withY;
            buffer.setLength(0);
            append(nx, factor);
            buffer.append(", ");
            if (withY) {
                append(ny, factor);
                buffer.append(", ");
            }
            append(nz, factor);
            text = buffer.toString();
        }

        private void append(long value, int factor) {
            if (value < 0) buffer.append('-');
            long absolute = Math.abs(value);
            buffer.append(absolute / factor);
            if (decimals == 0) return;
            buffer.append('.');
            long fraction = absolute % factor;
            for (int place = factor / 10; place > 0; place /= 10) buffer.append(fraction / place % 10);
        }
    }
}
