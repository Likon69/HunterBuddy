package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.SpawnerDetector;
import com.hunterbuddy.util.HudGlowPanel;
import java.util.ArrayList;
import java.util.List;
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
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

public class SpawnerFarmHud extends HudElement {
    public static final HudElementInfo<SpawnerFarmHud> INFO = new HudElementInfo<>(
        HunterBuddyAddon.HUD_GROUP, "SpawnerFarm",
        "SpawnerDetector auto-farm tally: spawners, chests, shulkers, rooms and the automaton's state.",
        SpawnerFarmHud::new);

    public enum Layout {
        Vertical,
        Horizontal
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgPanel = settings.createGroup("Panel");

    private final Setting<Boolean> showLabels = sgGeneral.add(new BoolSetting.Builder()
        .name("show-labels").description("Show the written label beside each counter. Off shows just the icon and the number.")
        .defaultValue(true).build());

    private final Setting<Layout> layout = sgGeneral.add(new EnumSetting.Builder<Layout>()
        .name("layout").description("Vertical stacks one counter per line. Horizontal lines them all up side by side in a single row.")
        .defaultValue(Layout.Vertical).build());

    private final Setting<Double> iconScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("icon-scale").description("Size of the item icons.")
        .defaultValue(0.6).min(0.2).max(2.0).sliderRange(0.3, 1.5).build());

    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("text-scale").description("Scale of the text.")
        .defaultValue(1.0).min(0.5).max(2.0).sliderRange(0.5, 2.0).build());

    private final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow").description("Render shadow behind text.").defaultValue(true).build());

    private final Setting<SettingColor> labelColor = sgColors.add(new ColorSetting.Builder()
        .name("label-color").description("Labels and separators.")
        .defaultValue(new SettingColor(150, 150, 150, 255)).build());

    private final Setting<SettingColor> valueColor = sgColors.add(new ColorSetting.Builder()
        .name("value-color").description("The numbers.")
        .defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private final Setting<SettingColor> stateColor = sgColors.add(new ColorSetting.Builder()
        .name("state-color").description("The automaton's current state line.")
        .defaultValue(new SettingColor(120, 240, 255, 255)).build());

    private final HudGlowPanel panel = new HudGlowPanel(sgPanel);

    private record Row(String label, String value, ItemStack icon) {}

    public SpawnerFarmHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        boolean shadow = textShadow.get();
        double scale = textScale.get();
        double inset = panel.padding();
        double iconSize = 16.0 * iconScale.get();
        double lineHeight = renderer.textHeight(shadow, scale);
        double rowHeight = Math.max(lineHeight, iconSize);

        List<Row> rows;
        String stateLine;

        if (isInEditor()) {
            rows = demoRows();
            stateLine = "ROOM";
        } else if (MeteorClient.mc.player == null || MeteorClient.mc.world == null) {
            setSize(0.0, 0.0);
            return;
        } else {
            SpawnerDetector detector = Modules.get().get(SpawnerDetector.class);
            if (detector == null || !detector.isActive()) {
                setSize(0.0, 0.0);
                return;
            }
            rows = liveRows(detector);
            String state = detector.getAutoStateName();
            if (state.equals("IDLE")) {
                stateLine = null;
            } else {
                SpawnerDetector.Site site = detector.getAutoSite();
                stateLine = site == null ? state : state + " " + siteLabel(site.center);
            }
        }

        if (!showLabels.get()) stateLine = null;

        double width;
        double height;

        if (layout.get() == Layout.Horizontal) {
            double sep = renderer.textWidth("  ", shadow, scale);

            width = 0.0;
            for (int i = 0; i < rows.size(); i++) {
                width += entryWidth(renderer, rows.get(i), shadow, scale, iconSize);
                if (i < rows.size() - 1) width += sep;
            }
            if (stateLine != null) width = Math.max(width, renderer.textWidth(stateLine, shadow, scale));

            height = rowHeight + (stateLine != null ? lineHeight : 0.0);

            panel.draw(renderer, this.x + inset, this.y + inset, width, height, HudGlowPanel.Severity.OK);

            double x0 = this.x + inset;
            double y0 = this.y + inset;
            double x = x0;

            for (int i = 0; i < rows.size(); i++) {
                x = drawRow(renderer, rows.get(i), x, y0, rowHeight, shadow, scale, iconSize);
                if (i < rows.size() - 1) x += sep;
            }

            if (stateLine != null) {
                renderer.text(stateLine, x0, y0 + rowHeight, stateColor.get(), shadow, scale);
            }
        } else {
            width = 0.0;
            for (Row row : rows) width = Math.max(width, entryWidth(renderer, row, shadow, scale, iconSize));
            if (stateLine != null) width = Math.max(width, renderer.textWidth(stateLine, shadow, scale));

            height = rowHeight * rows.size() + (stateLine != null ? lineHeight : 0.0);

            panel.draw(renderer, this.x + inset, this.y + inset, width, height, HudGlowPanel.Severity.OK);

            double x0 = this.x + inset;
            double y = this.y + inset;

            for (Row row : rows) {
                drawRow(renderer, row, x0, y, rowHeight, shadow, scale, iconSize);
                y += rowHeight;
            }

            if (stateLine != null) {
                renderer.text(stateLine, x0, y, stateColor.get(), shadow, scale);
            }
        }

        setSize(width + inset * 2.0, height + inset * 2.0);
    }

    private List<Row> liveRows(SpawnerDetector detector) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Spawners", String.valueOf(detector.getStatSpawnersBroken()), Items.SPAWNER.getDefaultStack()));
        rows.add(new Row("Chests", String.valueOf(detector.getStatChestsBroken()), Items.CHEST.getDefaultStack()));
        rows.add(new Row("Shulkers", String.valueOf(detector.getStatShulkersFound()), Items.SHULKER_BOX.getDefaultStack()));
        rows.add(new Row("Rooms", String.valueOf(detector.getStatRoomsFarmed()), Items.MOSSY_COBBLESTONE.getDefaultStack()));
        return rows;
    }

    private List<Row> demoRows() {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Spawners", "3", Items.SPAWNER.getDefaultStack()));
        rows.add(new Row("Chests", "6", Items.CHEST.getDefaultStack()));
        rows.add(new Row("Shulkers", "2", Items.SHULKER_BOX.getDefaultStack()));
        rows.add(new Row("Rooms", "1", Items.MOSSY_COBBLESTONE.getDefaultStack()));
        return rows;
    }

    private static String siteLabel(BlockPos center) {
        return center.getX() + "," + center.getY() + "," + center.getZ();
    }

    private double entryWidth(HudRenderer renderer, Row row, boolean shadow, double scale, double iconSize) {
        double w = iconSize + 2.0 * scale;
        if (showLabels.get()) w += renderer.textWidth(row.label() + " ", shadow, scale);
        return w + renderer.textWidth(row.value(), shadow, scale);
    }

    private double drawRow(HudRenderer renderer, Row row, double x, double y, double rowHeight,
                           boolean shadow, double scale, double iconSize) {
        double textY = y + (rowHeight - renderer.textHeight(shadow, scale)) / 2.0;
        double iconY = y + (rowHeight - iconSize) / 2.0;

        renderer.item(row.icon(), (int) x, (int) iconY, iconScale.get().floatValue(), false);
        x += iconSize + 2.0 * scale;

        if (showLabels.get()) {
            renderer.text(row.label() + " ", x, textY, labelColor.get(), shadow, scale);
            x += renderer.textWidth(row.label() + " ", shadow, scale);
        }

        renderer.text(row.value(), x, textY, valueColor.get(), shadow, scale);
        x += renderer.textWidth(row.value(), shadow, scale);

        return x;
    }
}
