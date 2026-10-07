package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.sorter.ShulkerSorter;
import com.hunterbuddy.modules.sorter.SorterRunSnapshot;
import com.hunterbuddy.util.HudPulse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import meteordevelopment.meteorclient.settings.BoolSetting;
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

public final class SorterStatusHud extends HudElement {
    public static final HudElementInfo<SorterStatusHud> INFO = new HudElementInfo<>(
        HunterBuddyAddon.HUD_GROUP, "SorterStatus", "Sorter progress, confirmed deliveries and storage capacity.",
        SorterStatusHud::new);

    public enum Density { COMPACT, FULL }

    private record Line(String label, String value, SettingColor color, String counter) { }

    private static final SettingColor CYAN = new SettingColor(90, 200, 255);
    private static final SettingColor GREEN = new SettingColor(110, 240, 150);
    private static final SettingColor VIOLET = new SettingColor(180, 155, 230);
    private static final SettingColor AMBER = new SettingColor(255, 200, 80);
    private static final SettingColor RED = new SettingColor(255, 90, 90);
    private static final SettingColor WHITE = new SettingColor(255, 255, 255);
    private static final SettingColor MUTED = new SettingColor(150, 150, 150);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Density> density = sgGeneral.add(new EnumSetting.Builder<Density>()
        .name("density").description("Compact status or full results and storage details.")
        .defaultValue(Density.COMPACT).build());
    private final Setting<Double> size = sgGeneral.add(new DoubleSetting.Builder()
        .name("size").description("Scale of the panel and text.")
        .defaultValue(1.0).min(0.6).max(2.0).sliderRange(0.6, 2.0).build());
    private final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow").description("Render text shadows.").defaultValue(true).build());
    private final Setting<Boolean> animations = sgGeneral.add(new BoolSetting.Builder()
        .name("animations").description("Highlight changes to counters and state.").defaultValue(true).build());
    private final Setting<Boolean> background = sgGeneral.add(new BoolSetting.Builder()
        .name("background").description("Draw a dark translucent panel.").defaultValue(true).build());
    private final HudPulse.Tracker pulse = new HudPulse.Tracker();

    public SorterStatusHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        ShulkerSorter sorter = Modules.get().get(ShulkerSorter.class);
        SorterRunSnapshot snapshot = sorter == null ? SorterRunSnapshot.empty() : sorter.snapshot();
        if (isInEditor() && snapshot.mode() == SorterRunSnapshot.Mode.OFF) {
            snapshot = example();
        }
        draw(renderer, snapshot);
    }

    private void draw(HudRenderer renderer, SorterRunSnapshot snapshot) {
        double scale = size.get();
        boolean shade = shadow.get();
        double pad = 8 * scale;
        double rowHeight = renderer.textHeight(shade, scale) + 4 * scale;
        double titleScale = scale * 1.15;
        double titleHeight = renderer.textHeight(shade, titleScale) + 5 * scale;
        double contentWidth = Math.max(340 * scale,
            renderer.textWidth("Delivered 000 shulkers (000 purified) · 000 loose stacks", shade, scale));
        List<Line> lines = lines(snapshot);
        boolean steps = density.get() == Density.FULL && snapshot.stage() != null;
        List<String> alerts = new ArrayList<>();
        SettingColor alertColor = AMBER;
        String alertLabel = "";
        if (snapshot.alert() != null) {
            alertColor = snapshot.alert().blocking() ? RED : AMBER;
            alertLabel = (snapshot.alert().blocking() ? "Action required" : "Warning")
                + " · " + snapshot.alert().ageMillis() / 1000 + "s ago";
            alerts = wrap(renderer, snapshot.alert().text(), contentWidth, shade, scale);
        } else if (density.get() == Density.COMPACT && !snapshot.capacity().missingCategories().isEmpty()) {
            alertLabel = "No free destination";
            alerts = wrap(renderer, String.join(", ", snapshot.capacity().missingCategories()), contentWidth, shade, scale);
        }
        double fullWidth = contentWidth + pad * 2;
        double height = pad * 2 + titleHeight + rowHeight
            + (steps ? rowHeight + 4 * scale : 0) + lines.size() * rowHeight
            + (alerts.isEmpty() ? 0 : (alerts.size() + 1) * rowHeight + 4 * scale);
        setSize(fullWidth, height);
        SettingColor accent = accent(snapshot);
        if (background.get()) {
            renderer.quad(x, y, fullWidth, height, new Color(0, 0, 0, 120));
            renderer.quad(x, y, 3 * scale, height, accent);
            renderer.quad(x + 3 * scale, y, fullWidth - 3 * scale, titleHeight + pad,
                new Color(accent.r, accent.g, accent.b, 18));
        }
        double left = x + pad;
        double top = y + pad;
        String clock = snapshot.durationMillis() > 0 ? duration(snapshot.durationMillis()) : "";
        double clockWidth = renderer.textWidth(clock, shade, scale);
        renderer.text("SHULKER SORTER", left, top, MUTED, shade, titleScale);
        if (!clock.isEmpty()) {
            renderer.text(clock, left + contentWidth - clockWidth, top, WHITE, shade, scale);
        }
        top += titleHeight;
        float stateFreshness = pulse.freshness("state", snapshot.mode() + ":" + snapshot.state(), 700);
        Color stateColor = animations.get() ? HudPulse.tint(HudPulse.Style.Subtle, accent, WHITE, stateFreshness) : accent;
        renderer.text(fit(renderer, snapshot.state(), contentWidth, shade, scale), left, top, stateColor, shade, scale);
        top += rowHeight;
        if (steps) {
            String[] names = { "Inputs", "Sort", "Station", "Pack", "Store" };
            double segment = contentWidth / names.length;
            for (int i = 0; i < names.length; i++) {
                boolean current = snapshot.stage().ordinal() == i;
                renderer.text(names[i], left + segment * i, top, current ? accent : MUTED, shade, scale);
                if (current) {
                    renderer.quad(left + segment * i, top + rowHeight - 2 * scale, segment - 10 * scale,
                        Math.max(1, scale), accent);
                }
            }
            top += rowHeight + 4 * scale;
        }
        for (Line line : lines) {
            double labelWidth = renderer.textWidth(line.label(), shade, scale) + 10 * scale;
            renderer.text(line.label(), left, top, MUTED, shade, scale);
            Color color = line.color();
            if (line.counter() != null) {
                float freshness = pulse.freshness(line.counter(), line.value(), 900);
                if (animations.get()) {
                    color = HudPulse.tint(HudPulse.Style.Subtle, line.color(), GREEN, freshness);
                }
            }
            renderer.text(fit(renderer, line.value(), contentWidth - labelWidth, shade, scale),
                left + labelWidth, top, color, shade, scale);
            top += rowHeight;
        }
        if (!alerts.isEmpty()) {
            top += 4 * scale;
            renderer.text(alertLabel, left, top, alertColor, shade, scale);
            top += rowHeight;
            for (String line : alerts) {
                renderer.text(line, left, top, alertColor, shade, scale);
                top += rowHeight;
            }
        }
    }

    private List<Line> lines(SorterRunSnapshot snapshot) {
        List<Line> lines = new ArrayList<>();
        if (snapshot.target() != null) {
            SorterRunSnapshot.Target target = snapshot.target();
            String value = (target.category().isEmpty() ? "" : target.category() + " · ")
                + target.position() + String.format(Locale.ROOT, " · %.1fm", target.distance());
            lines.add(new Line(target.role(), value, WHITE, null));
        }
        if (snapshot.pauseReason() != null && !snapshot.pauseReason().isEmpty()) {
            lines.add(new Line("Waiting", snapshot.pauseReason(), AMBER, null));
        }
        if (snapshot.withdrawal() != null) {
            SorterRunSnapshot.Withdrawal withdrawal = snapshot.withdrawal();
            lines.add(new Line(withdrawal.category(), withdrawal.withdrawn() + "/" + withdrawal.requested()
                + " → " + withdrawal.destination(), WHITE, "withdrawal"));
            if (snapshot.mode() == SorterRunSnapshot.Mode.DONE) {
                lines.add(new Line("Inventory", withdrawal.leftInInventory() + " shulkers left", WHITE, null));
            }
            return lines;
        }
        SorterRunSnapshot.Storage storage = snapshot.storage();
        boolean off = snapshot.mode() == SorterRunSnapshot.Mode.OFF;
        if (off || snapshot.mode() == SorterRunSnapshot.Mode.DONE) {
            if (storage.inputs() + storage.destinations() + storage.reserves() == 0 && storage.station().isEmpty()) {
                lines.add(new Line("Setup", "Register storage with tag-key", MUTED, null));
            } else {
                lines.add(new Line("Storage", storage.inputs() + " inputs · " + storage.destinations()
                    + " destinations · " + storage.reserves() + " reserves", WHITE, null));
            }
            if (!storage.station().isEmpty()) {
                lines.add(new Line("Station", storage.station(), VIOLET, null));
            }
        }
        if (!off && snapshot.progress().registered() > 0) {
            SorterRunSnapshot.Progress progress = snapshot.progress();
            lines.add(new Line("Input visits", progress.remaining() + "/" + progress.registered()
                + " left · " + progress.emptied() + " empty · " + progress.excluded() + " excluded", WHITE, "inputs"));
        }
        if (density.get() == Density.FULL) {
            SorterRunSnapshot.Results results = snapshot.results();
            if (results.shulkers() > 0 || results.looseStacks() > 0) {
                lines.add(new Line("Delivered", results.shulkers() + " shulkers (" + results.purifiedDelivered()
                    + " purified) · " + results.looseStacks() + " loose stacks", WHITE, "deliveries"));
            }
            if (results.purified() > 0) {
                lines.add(new Line("Purification", results.purified() + " completed", WHITE, "purified"));
            }
            lines.add(new Line("Packed", results.packed() + " shulkers (" + results.packedFull()
                + " full)", WHITE, "packed"));
            for (Map.Entry<String, Integer> category : results.byCategory().entrySet()) {
                lines.add(new Line(category.getKey(), category.getValue() + " deliveries", WHITE, "category:" + category.getKey()));
            }
            SorterRunSnapshot.Capacity capacity = snapshot.capacity();
            if (capacity.fullChests() > 0 || capacity.reserves() > 0) {
                lines.add(new Line("Capacity", capacity.fullChests() + " full chests · " + capacity.reserves()
                    + " reserves", capacity.fullChests() > 0 ? AMBER : WHITE, "capacity"));
            }
            if (!capacity.missingCategories().isEmpty()) {
                lines.add(new Line("No space", String.join(", ", capacity.missingCategories()), AMBER, null));
            }
        }
        return lines;
    }

    private static SettingColor accent(SorterRunSnapshot snapshot) {
        if (snapshot.alert() != null && snapshot.alert().blocking()) {
            return RED;
        }
        if (snapshot.mode() == SorterRunSnapshot.Mode.PAUSED || !snapshot.capacity().missingCategories().isEmpty()) {
            return AMBER;
        }
        return switch (snapshot.mode()) {
            case STATION -> VIOLET;
            case DONE -> GREEN;
            case SORTING, WITHDRAW -> CYAN;
            default -> MUTED;
        };
    }

    private static String duration(long millis) {
        long seconds = millis / 1000;
        return seconds >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
            : String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private static String fit(HudRenderer renderer, String text, double width, boolean shadow, double scale) {
        if (renderer.textWidth(text, shadow, scale) <= width) {
            return text;
        }
        int length = text.length();
        while (length > 0 && renderer.textWidth(text.substring(0, length) + "…", shadow, scale) > width) {
            length--;
        }
        return length == 0 ? "" : text.substring(0, length) + "…";
    }

    private static List<String> wrap(HudRenderer renderer, String text, double width, boolean shadow, double scale) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && renderer.textWidth(candidate, shadow, scale) > width) {
                lines.add(fit(renderer, line.toString(), width, shadow, scale));
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        if (!line.isEmpty()) {
            lines.add(fit(renderer, line.toString(), width, shadow, scale));
        }
        return lines;
    }

    private static SorterRunSnapshot example() {
        return new SorterRunSnapshot(SorterRunSnapshot.Mode.SORTING, SorterRunSnapshot.Stage.STORE, "Depositing", "",
            new SorterRunSnapshot.Target("Destination", "pvp", "128, 64, -320", 3.2),
            new SorterRunSnapshot.Progress(8, 3, 4, 1),
            new SorterRunSnapshot.Results(12, 3, 4, 7, 2, 1, Map.of("pvp", 9, "flight", 6, "building", 4)),
            new SorterRunSnapshot.Capacity(1, 2, List.of()),
            new SorterRunSnapshot.Alert("Reserve at 124, 64, -318 was not empty; marked full.", 2300, false),
            new SorterRunSnapshot.Storage(8, 6, 2, "130 64 -322"), 137_000, null);
    }
}
