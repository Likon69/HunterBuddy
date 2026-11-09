package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.hud.cards.CardMetrics;
import com.hunterbuddy.hud.cards.CardStyle;
import com.hunterbuddy.hud.cards.PlayerCard;
import com.hunterbuddy.hud.cards.PlayerCardsPainter;
import com.hunterbuddy.hud.cards.PlayerCardsPreview;
import com.hunterbuddy.hud.cards.PlayerMovement;
import com.hunterbuddy.hud.cards.PlayerTrack;
import com.hunterbuddy.hud.cards.PlayerTracker;
import com.hunterbuddy.hud.toast.ModuleToastBlur;
import java.util.List;
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

public final class PlayerCardsHud extends HudElement {
    public static final HudElementInfo<PlayerCardsHud> INFO = new HudElementInfo<>(HunterBuddyAddon.HUD_GROUP,
        "PlayerCards", "Glass cards for players in range: head, name, gear, distance and whether they are closing.",
        PlayerCardsHud::new);
    private static final double GHOST_ALPHA = 0.45;
    private final SettingGroup general = settings.getDefaultGroup();
    private final SettingGroup colors = settings.createGroup("Colors");
    private final Setting<Integer> maxCards = general.add(new IntSetting.Builder().name("max-cards")
        .description("How many players to show, nearest first.").defaultValue(4).min(1).max(8).sliderRange(1, 8).build());
    private final Setting<Boolean> includeFriends = general.add(new BoolSetting.Builder().name("include-friends")
        .description("Show friends too.").defaultValue(false).build());
    private final Setting<Integer> alertDistance = general.add(new IntSetting.Builder().name("alert-distance")
        .description("A player closing inside this range turns the card accent critical.")
        .defaultValue(250).min(50).max(500).sliderRange(100, 400).build());
    private final Setting<Double> width = general.add(new DoubleSetting.Builder().name("width")
        .description("Card width before scaling; grows when the content needs more room.")
        .defaultValue(270).min(200).max(600).sliderRange(220, 420).build());
    private final Setting<Double> scale = general.add(new DoubleSetting.Builder().name("scale")
        .description("Scale of the cards.").defaultValue(1).min(0.5).max(2).sliderRange(0.6, 1.5).build());
    private final Setting<Double> textScale = general.add(new DoubleSetting.Builder().name("text-scale")
        .description("Size of the text, independent of the card scale.").defaultValue(1).min(0.5).max(2).sliderRange(0.6, 1.5).build());
    private final Setting<Boolean> compact = general.add(new BoolSetting.Builder().name("compact")
        .description("One-line cards: head, name and distance, without the gear row.").defaultValue(false).build());
    private final Setting<Boolean> upward = general.add(new BoolSetting.Builder().name("grow-upward")
        .description("Anchor the nearest card at the bottom and stack the others upward.").defaultValue(false).build());
    private final Setting<Boolean> blur = general.add(new BoolSetting.Builder().name("background-blur")
        .description("Blur the actual scene behind each card, limited to its rounded bounds.").defaultValue(true).build());
    private final Setting<Integer> blurStrength = general.add(new IntSetting.Builder().name("blur-strength")
        .description("Background blur sampling radius.").defaultValue(2).min(1).max(5).sliderRange(1, 5).visible(blur::get).build());
    private final Setting<Boolean> reflection = general.add(new BoolSetting.Builder().name("glass-reflection")
        .description("Add a soft reflected highlight to the translucent cards.").defaultValue(true).build());
    private final Setting<SettingColor> approachColor = colors.add(new ColorSetting.Builder().name("approach-color")
        .description("A player closing in.").defaultValue(new SettingColor(255, 80, 80, 255)).build());
    private final Setting<SettingColor> holdColor = colors.add(new ColorSetting.Builder().name("stationary-color")
        .description("A player holding their distance.").defaultValue(new SettingColor(255, 200, 80, 255)).build());
    private final Setting<SettingColor> recedeColor = colors.add(new ColorSetting.Builder().name("recede-color")
        .description("A player moving away.").defaultValue(new SettingColor(110, 240, 130, 255)).build());
    private final Setting<SettingColor> criticalColor = colors.add(new ColorSetting.Builder().name("critical-color")
        .description("Card accent for a player closing inside the alert distance.").defaultValue(new SettingColor(255, 45, 85, 255)).build());
    private final Setting<SettingColor> background = colors.add(new ColorSetting.Builder().name("background")
        .description("Glass background, including its opacity.").defaultValue(new SettingColor(17, 24, 35, 222)).build());
    private final Setting<SettingColor> text = colors.add(new ColorSetting.Builder().name("text-color")
        .description("Names and distances, including their opacity.").defaultValue(new SettingColor(236, 243, 252, 255)).build());
    private final PlayerTracker tracker = new PlayerTracker();
    private final PlayerCardsPainter painter = new PlayerCardsPainter();

    public PlayerCardsHud() {
        super(INFO);
        setSize(300, 220);
    }

    @Override
    public void tick(HudRenderer renderer) {
        tracker.update(includeFriends.get(), System.currentTimeMillis());
    }

    @Override
    public void render(HudRenderer renderer) {
        long now = System.currentTimeMillis();
        CardStyle style = new CardStyle(scale.get(), textScale.get(), compact.get(), blur.get(), reflection.get(),
            background.get(), text.get());
        CardMetrics metrics = painter.measure(renderer, style);
        double s = style.scale();
        double cardWidth = Math.max(width.get() * s, metrics.minWidth());
        double stride = metrics.height() + 8 * s;
        double h = maxCards.get() * stride - 8 * s;
        double pad = 7 * s;
        setSize(cardWidth + pad * 2, h + pad * 2);
        List<PlayerTrack> tracks = tracker.nearest(maxCards.get(), now);
        if (isInEditor() && tracks.stream().noneMatch(track -> track.present(now))) {
            tracks = PlayerCardsPreview.tracks(now);
        }
        if (style.blur()) ModuleToastBlur.INSTANCE.begin(blurStrength.get());
        for (int i = 0; i < tracks.size() && i < maxCards.get(); i++) {
            PlayerTrack track = tracks.get(i);
            PlayerMovement movement = track.movement(now);
            SettingColor pill = pillColor(movement);
            SettingColor accent = track.closingWithin(alertDistance.get(), now) ? criticalColor.get() : pill;
            PlayerCard card = new PlayerCard(track, movement, accent, pill, track.present(now) ? 1 : GHOST_ALPHA);
            double rowY = upward.get() ? h - metrics.height() - i * stride : i * stride;
            painter.draw(renderer, style, metrics, card, x + pad, y + pad + rowY, cardWidth);
        }
    }

    private SettingColor pillColor(PlayerMovement movement) {
        return switch (movement) {
            case APPROACH -> approachColor.get();
            case RECEDE -> recedeColor.get();
            case HOLD, GONE -> holdColor.get();
        };
    }
}
