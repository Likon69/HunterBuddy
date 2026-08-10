package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.util.HudGlowPanel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Every player in render distance, with the one number that matters: are they closing.
 *
 * <p>The notifier says someone appeared; this says what they are doing about you. Distance is
 * tracked over a three-second window per player, so the vector is measured rather than guessed,
 * and the gear column says at a glance whether the shape closing in is a kit or a farmhand.
 */
public class ThreatBoardHud extends HudElement {
    public static final HudElementInfo<ThreatBoardHud> INFO = new HudElementInfo<>(
        HunterBuddyAddon.HUD_GROUP, "ThreatBoard",
        "Players in range: distance, approach vector, gear summary.",
        ThreatBoardHud::new);

    private static final long VECTOR_WINDOW_MS = 3_000L;
    private static final long GHOST_MS = 10_000L;

    private static final class Track {
        final ArrayDeque<double[]> samples = new ArrayDeque<>();
        String name = "";
        long lastSeenAt;
        double distance;
        double approachSpeed;
        boolean elytra;
        ItemStack hand = ItemStack.EMPTY;
        boolean totem;
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgPanel = settings.createGroup("Panel");

    private final Setting<Integer> maxRows = sgGeneral.add(new IntSetting.Builder()
        .name("max-rows").description("How many players to list, nearest first.")
        .defaultValue(4).min(1).max(8).sliderRange(1, 8).build());

    private final Setting<Boolean> includeFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("include-friends").description("List friends too.")
        .defaultValue(false).build());

    private final Setting<Double> panelWidth = sgGeneral.add(new DoubleSetting.Builder()
        .name("width").description("Fixed width, so rows joining and leaving never resize it.")
        .defaultValue(230.0).min(160.0).max(400.0).sliderRange(180.0, 320.0).build());

    private final Setting<Integer> alertDistance = sgGeneral.add(new IntSetting.Builder()
        .name("alert-distance").description("A player closing inside this range turns the halo critical.")
        .defaultValue(250).min(50).max(500).sliderRange(100, 400).build());

    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("text-scale").description("Scale of the text.")
        .defaultValue(1.0).min(0.5).max(2.0).sliderRange(0.5, 2.0).build());

    private final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow").description("Render shadow behind text.").defaultValue(true).build());

    private final Setting<SettingColor> labelColor = sgColors.add(new ColorSetting.Builder()
        .name("label-color").description("Distances and the receding.")
        .defaultValue(new SettingColor(150, 150, 150, 255)).build());

    private final Setting<SettingColor> valueColor = sgColors.add(new ColorSetting.Builder()
        .name("value-color").description("Values.")
        .defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private final Setting<SettingColor> approachColor = sgColors.add(new ColorSetting.Builder()
        .name("approach-color").description("A player closing in.")
        .defaultValue(new SettingColor(255, 80, 80, 255)).build());

    private final Setting<SettingColor> holdColor = sgColors.add(new ColorSetting.Builder()
        .name("stationary-color").description("A player holding their distance.")
        .defaultValue(new SettingColor(255, 200, 80, 255)).build());

    private final Setting<SettingColor> recedeColor = sgColors.add(new ColorSetting.Builder()
        .name("recede-color").description("A player moving away.")
        .defaultValue(new SettingColor(110, 240, 130, 255)).build());

    private final HudGlowPanel panel = new HudGlowPanel(sgPanel);

    private final Map<UUID, Track> tracks = new HashMap<>();

    public ThreatBoardHud() {
        super(INFO);
    }

    @Override
    public void tick(HudRenderer renderer) {
        if (MeteorClient.mc.player == null || MeteorClient.mc.world == null) {
            tracks.clear();
            return;
        }

        long now = System.currentTimeMillis();

        for (PlayerEntity player : MeteorClient.mc.world.getPlayers()) {
            if (player == MeteorClient.mc.player) continue;
            if (player.getUuid().equals(MeteorClient.mc.player.getUuid())) continue;
            if (!includeFriends.get() && Friends.get().isFriend(player)) continue;

            Track track = tracks.computeIfAbsent(player.getUuid(), uuid -> new Track());
            track.name = player.getGameProfile().name();
            track.lastSeenAt = now;
            track.distance = MeteorClient.mc.player.distanceTo(player);
            track.elytra = player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
            track.hand = player.getMainHandStack().copy();
            track.totem = player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)
                || player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING);

            track.samples.addLast(new double[]{now, track.distance});
            while (!track.samples.isEmpty() && now - (long) track.samples.peekFirst()[0] > VECTOR_WINDOW_MS) {
                track.samples.removeFirst();
            }

            if (track.samples.size() >= 2) {
                double[] first = track.samples.peekFirst();
                double[] last = track.samples.peekLast();
                double seconds = (last[0] - first[0]) / 1000.0;
                track.approachSpeed = seconds > 0.2 ? (first[1] - last[1]) / seconds : 0.0;
            }
        }

        Iterator<Map.Entry<UUID, Track>> it = tracks.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().lastSeenAt > GHOST_MS) it.remove();
        }
    }

    @Override
    public void render(HudRenderer renderer) {
        List<Track> rows;
        long now = System.currentTimeMillis();

        if (MeteorClient.mc.world != null) {
            rows = new ArrayList<>(tracks.values());
            rows.sort((a, b) -> {
                boolean ghostA = now - a.lastSeenAt > 1_000L;
                boolean ghostB = now - b.lastSeenAt > 1_000L;
                if (ghostA != ghostB) return ghostA ? 1 : -1;
                return Double.compare(a.distance, b.distance);
            });
            if (rows.size() > maxRows.get()) rows = rows.subList(0, maxRows.get());
        } else if (isInEditor()) {
            rows = demoRows();
        } else {
            setSize(0.0, 0.0);
            return;
        }

        boolean shadow = textShadow.get();
        double scale = textScale.get();
        double pad = panel.padding();
        double lineH = Math.max(renderer.textHeight(shadow, scale), 16.0 * 0.5) + 2.0 * scale;
        double w = panelWidth.get();

        if (rows.isEmpty()) {
            setSize(0.0, 0.0);
            return;
        }

        double titleH = renderer.textHeight(shadow, scale);
        double h = titleH + 2.0 + rows.size() * lineH - 2.0 * scale;

        HudGlowPanel.Severity severity = HudGlowPanel.Severity.OK;
        for (Track track : rows) {
            boolean present = now - track.lastSeenAt <= 1_000L;
            if (!present) continue;

            severity = HudGlowPanel.worst(severity, HudGlowPanel.Severity.WARN);
            if (track.approachSpeed > 3.0 && track.distance < alertDistance.get()) {
                severity = HudGlowPanel.Severity.CRITICAL;
            }
        }

        panel.draw(renderer, x + pad, y + pad, w, h, severity);

        double left = x + pad;
        double ty = y + pad;

        renderer.text("threats · " + rows.size(), left, ty, labelColor.get(), shadow, scale);
        ty += titleH + 2.0;

        for (Track track : rows) {
            drawRow(renderer, track, left, ty, w, now, shadow, scale);
            ty += lineH;
        }

        setSize(w + pad * 2.0, h + pad * 2.0);
    }

    private void drawRow(HudRenderer renderer, Track track, double left, double top, double w,
                         long now, boolean shadow, double scale) {
        boolean ghost = now - track.lastSeenAt > 1_000L;
        double fade = ghost ? 0.45 : 1.0;

        SettingColor vector = track.approachSpeed > 1.0 ? approachColor.get()
            : track.approachSpeed < -1.0 ? recedeColor.get()
            : holdColor.get();

        Color nameColor = fade(ghost ? labelColor.get() : vector, fade);

        double tx = left;
        String name = track.name.length() > 14 ? track.name.substring(0, 13) + ".." : track.name;
        renderer.text(name, tx, top, nameColor, shadow, scale);
        tx += renderer.textWidth("WWWWWWWWWWWWWW", shadow, scale) * 0.72;

        String dist = String.format("%.0f b", track.distance);
        renderer.text(dist, tx, top, fade(valueColor.get(), fade), shadow, scale);
        tx += renderer.textWidth("8888 b", shadow, scale) + 4.0 * scale;

        String vectorText = ghost ? "left"
            : track.approachSpeed > 1.0 ? String.format("^ %.0f m/s", track.approachSpeed)
            : track.approachSpeed < -1.0 ? String.format("v %.0f m/s", -track.approachSpeed)
            : "= holding";
        renderer.text(vectorText, tx, top, fade(vector, fade), shadow, scale);

        // Gear on the right edge: worn elytra, held item, totem — the silhouette of the kit.
        double ix = left + w - 16.0 * 0.5;
        if (track.totem) {
            renderer.item(Items.TOTEM_OF_UNDYING.getDefaultStack(), (int) ix, (int) top, 0.5f, false);
            ix -= 16.0 * 0.5 + 1.0;
        }
        if (!track.hand.isEmpty() && !track.hand.isOf(Items.TOTEM_OF_UNDYING)) {
            renderer.item(track.hand, (int) ix, (int) top, 0.5f, false);
            ix -= 16.0 * 0.5 + 1.0;
        }
        if (track.elytra) {
            renderer.item(Items.ELYTRA.getDefaultStack(), (int) ix, (int) top, 0.5f, false);
        }
    }

    private static Color fade(SettingColor color, double fade) {
        return new Color(color.r, color.g, color.b, (int) (color.a * fade));
    }

    private List<Track> demoRows() {
        List<Track> out = new ArrayList<>();
        long now = System.currentTimeMillis();

        Track a = new Track();
        a.name = "Fit2Win";
        a.lastSeenAt = now;
        a.distance = 180.0;
        a.approachSpeed = 12.0;
        a.elytra = true;
        a.totem = true;
        a.hand = Items.NETHERITE_SWORD.getDefaultStack();
        out.add(a);

        Track b = new Track();
        b.name = "xX_Hunt3r";
        b.lastSeenAt = now;
        b.distance = 410.0;
        b.approachSpeed = 0.2;
        b.hand = Items.NETHERITE_PICKAXE.getDefaultStack();
        out.add(b);

        return out;
    }
}
