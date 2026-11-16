package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.hud.glass.GlassPanel;
import com.hunterbuddy.hud.text.HudText;
import com.hunterbuddy.hud.text.HudTextScale;
import com.hunterbuddy.util.HudGlowPanel;
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

    public enum Show {
        Both,
        CurrentOnly,
        OppositeOnly;

        @Override
        public String toString() {
            return switch (this) {
                case Both -> "Current and opposite";
                case CurrentOnly -> "Current only";
                case OppositeOnly -> "Opposite only";
            };
        }
    }

    public enum Labels {
        Full,
        Short,
        None
    }

    public enum TextSize {
        Small,
        Normal
    }

    private final SettingGroup general = settings.getDefaultGroup();
    private final GlassPanel panel = new GlassPanel(general, settings.createGroup("Panel"),
        settings.createGroup("Glow"));
    private final Setting<Show> show = general.add(new EnumSetting.Builder<Show>()
        .name("dimensions").description("Choose current coordinates, opposite coordinates, or both.")
        .defaultValue(Show.Both).build());
    private final Setting<Boolean> showY = general.add(new BoolSetting.Builder()
        .name("show-y").description("Include the Y coordinate.").defaultValue(true).build());
    private final Setting<Integer> decimals = general.add(new IntSetting.Builder()
        .name("decimals").description("Decimal places for coordinates.")
        .defaultValue(0).min(0).max(3).sliderRange(0, 3).build());
    private final Setting<Labels> labels = general.add(new EnumSetting.Builder<Labels>()
        .name("labels").description("Dimension label before each row: full name, short name, or none.")
        .defaultValue(Labels.Full).build());
    private final Setting<TextSize> labelSize = general.add(new EnumSetting.Builder<TextSize>()
        .name("label-size").description("Text size of the dimension label.")
        .defaultValue(TextSize.Normal).visible(() -> labels.get() != Labels.None).build());
    private final Setting<SettingColor> labelColor = general.add(new ColorSetting.Builder()
        .name("label-color").description("Dimension label color and opacity.")
        .defaultValue(new SettingColor(126, 200, 255, 255)).visible(() -> labels.get() != Labels.None).build());
    private final Setting<Boolean> direction = general.add(new BoolSetting.Builder()
        .name("direction").description("Show the facing direction from the player's yaw.")
        .defaultValue(false).build());
    private final Setting<TextSize> oppositeSize = general.add(new EnumSetting.Builder<TextSize>()
        .name("opposite-size").description("Text size of the opposite dimension row.")
        .defaultValue(TextSize.Normal).visible(() -> show.get() != Show.CurrentOnly).build());
    private final Setting<Double> oppositeOpacity = general.add(new DoubleSetting.Builder()
        .name("opposite-opacity").description("Opacity of the opposite dimension row.")
        .defaultValue(0.85).min(0.2).max(1).sliderRange(0.2, 1)
        .visible(() -> show.get() != Show.CurrentOnly).build());
    private final Setting<SettingColor> textColor = general.add(new ColorSetting.Builder()
        .name("text-color").description("Coordinate text color and opacity.")
        .defaultValue(new SettingColor(236, 243, 252, 255)).build());
    private final Setting<Boolean> axisColors = general.add(new BoolSetting.Builder()
        .name("axis-colors").description("Draw X, Y and Z in their own colors.")
        .defaultValue(false).build());
    private final Setting<SettingColor> xColor = general.add(new ColorSetting.Builder()
        .name("x-color").description("Color of the X coordinate.")
        .defaultValue(new SettingColor(255, 120, 120, 255)).visible(axisColors::get).build());
    private final Setting<SettingColor> yColor = general.add(new ColorSetting.Builder()
        .name("y-color").description("Color of the Y coordinate.")
        .defaultValue(new SettingColor(130, 230, 140, 255)).visible(axisColors::get).build());
    private final Setting<SettingColor> zColor = general.add(new ColorSetting.Builder()
        .name("z-color").description("Color of the Z coordinate.")
        .defaultValue(new SettingColor(120, 170, 255, 255)).visible(axisColors::get).build());

    private final Row current = new Row();
    private final Row opposite = new Row();
    private final CoordsGlyphs normalGlyphs = new CoordsGlyphs();
    private final CoordsGlyphs smallGlyphs = new CoordsGlyphs();
    private final Color paint = new Color();
    private String dimension = "Overworld";
    private String otherDimension = "Nether";
    private float yaw = 225;
    private double labelWidth;
    private double compassWidth;
    private double normalHeight;
    private double smallHeight;
    private Labels measuredLabels;
    private TextSize measuredLabelSize;
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
        yaw = MeteorClient.mc.player.getYaw();
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
        boolean compass = direction.get();
        boolean smallOpposite = oppositeSize.get() == TextSize.Small;
        double oppositeScale = smallOpposite ? HudTextScale.SMALL : HudTextScale.NORMAL;
        CoordsGlyphs oppositeGlyphs = smallOpposite ? smallGlyphs : normalGlyphs;
        double s = panel.scale();
        double pad = panel.padding();
        double gap = 4 * s;
        double labelGap = labelWidth > 0 ? 8 * s : 0;
        double currentRow = Math.max(normalHeight, smallHeight);
        double oppositeRow = smallOpposite ? smallHeight : normalHeight;
        double valueWidth = Math.max(first ? normalGlyphs.width(current) : 0,
            second ? oppositeGlyphs.width(opposite) : 0);
        double contentWidth = labelWidth + labelGap + valueWidth;
        double contentHeight = 0;
        int rows = 0;
        if (first) {
            contentHeight += currentRow;
            rows++;
        }
        if (second) {
            contentHeight += oppositeRow;
            rows++;
        }
        if (compass) {
            contentWidth = Math.max(contentWidth, compassWidth);
            contentHeight += smallHeight;
            rows++;
        }
        contentHeight += gap * (rows - 1);
        setSize(contentWidth + pad * 2, contentHeight + pad * 2);
        panel.draw(renderer, x + pad, y + pad, contentWidth, contentHeight, HudGlowPanel.Severity.OK);
        double top = y + pad;
        double valueX = x + pad + labelWidth + labelGap;
        if (first) {
            drawRow(renderer, dimension, current, x + pad, valueX, top, currentRow, HudTextScale.NORMAL, 1);
            top += currentRow + gap;
        }
        if (second) {
            drawRow(renderer, otherDimension, opposite, x + pad, valueX, top, oppositeRow, oppositeScale,
                oppositeOpacity.get());
            top += oppositeRow + gap;
        }
        if (compass) {
            SettingColor ink = textColor.get();
            paint.set(ink.r, ink.g, ink.b, ink.a);
            HudText.text(renderer, CoordsCompass.name(yaw), x + pad, top, paint, false, HudTextScale.SMALL);
        }
    }

    private void drawRow(HudRenderer renderer, String label, Row row, double labelX, double valueX,
                         double top, double height, double textScale, double opacity) {
        SettingColor ink = textColor.get();
        if (labels.get() != Labels.None) {
            SettingColor tint = labelColor.get();
            paint.set(tint.r, tint.g, tint.b, (int) Math.round(tint.a * opacity));
            String shown = labels.get() == Labels.Short ? shortLabel(label) : label;
            double labelScale = labelScale();
            HudText.text(renderer, shown, labelX, top + (height - HudText.textHeight(renderer, false, labelScale)) / 2,
                paint, false, labelScale);
        }
        double textY = top + (height - HudText.textHeight(renderer, false, textScale)) / 2;
        paint.set(ink.r, ink.g, ink.b, (int) Math.round(ink.a * opacity));
        if (!axisColors.get()) {
            HudText.text(renderer, row.text, valueX, textY, paint, false, textScale);
            return;
        }
        SettingColor[] colors = { xColor.get(), yColor.get(), zColor.get() };
        double cursor = valueX;
        boolean started = false;
        for (int axis = 0; axis < colors.length; axis++) {
            String part = row.part[axis];
            if (part == null) continue;
            if (started) {
                paint.set(ink.r, ink.g, ink.b, (int) Math.round(ink.a * opacity));
                HudText.text(renderer, ", ", cursor, textY, paint, false, textScale);
                cursor += HudText.textWidth(renderer, ", ", false, textScale);
            }
            started = true;
            SettingColor color = colors[axis];
            paint.set(color.r, color.g, color.b, (int) Math.round(color.a * opacity));
            HudText.text(renderer, part, cursor, textY, paint, false, textScale);
            cursor += HudText.textWidth(renderer, part, false, textScale);
        }
    }

    private double labelScale() {
        return labelSize.get() == TextSize.Small ? HudTextScale.SMALL : HudTextScale.NORMAL;
    }

    private static String shortLabel(String label) {
        return switch (label) {
            case "Overworld" -> "OW";
            case "Nether" -> "N";
            case "End" -> "End";
            default -> "?";
        };
    }

    private void measure(HudRenderer renderer) {
        if (measured && measuredLabels == labels.get() && measuredLabelSize == labelSize.get()) return;
        measured = true;
        measuredLabels = labels.get();
        measuredLabelSize = labelSize.get();
        normalGlyphs.measure(renderer, HudTextScale.NORMAL);
        smallGlyphs.measure(renderer, HudTextScale.SMALL);
        normalHeight = HudText.textHeight(renderer, false, HudTextScale.NORMAL);
        smallHeight = HudText.textHeight(renderer, false, HudTextScale.SMALL);
        labelWidth = 0;
        if (measuredLabels != Labels.None) {
            for (String name : new String[] { "Overworld", "Nether", "End", "Other" }) {
                String shown = measuredLabels == Labels.Short ? shortLabel(name) : name;
                labelWidth = Math.max(labelWidth, HudText.textWidth(renderer, shown, false, labelScale()));
            }
        }
        compassWidth = 0;
        for (String name : CoordsCompass.NAMES) {
            compassWidth = Math.max(compassWidth, HudText.textWidth(renderer, name, false, HudTextScale.SMALL));
        }
    }

    @Override
    public void onFontChanged() {
        measured = false;
    }

    static final class Row {
        private long x = Long.MIN_VALUE;
        private long y = Long.MIN_VALUE;
        private long z = Long.MIN_VALUE;
        private int decimals = -1;
        private boolean showY;
        final String[] part = new String[3];
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
            part[0] = format(nx, factor);
            part[1] = withY ? format(ny, factor) : null;
            part[2] = format(nz, factor);
            text = withY ? part[0] + ", " + part[1] + ", " + part[2] : part[0] + ", " + part[2];
        }

        private String format(long value, int factor) {
            StringBuilder out = new StringBuilder(16);
            if (value < 0) out.append('-');
            long absolute = Math.abs(value);
            out.append(absolute / factor);
            if (decimals == 0) return out.toString();
            out.append('.');
            long fraction = absolute % factor;
            for (int place = factor / 10; place > 0; place /= 10) out.append(fraction / place % 10);
            return out.toString();
        }
    }
}
