package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.util.HudGlowPanel;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * A heraldic banner for the dimension you stand in.
 *
 * <p>Sky over grass for the Overworld, ember over black for the Nether with the portal frame
 * shimmering in it, void purple for the End. It crossfades on crossing, which is the whole
 * ceremony: a little flag-change for stepping through the door.
 */
public class DimensionBannerHud extends HudElement {
    public static final HudElementInfo<DimensionBannerHud> INFO = new HudElementInfo<>(
        HunterBuddyAddon.HUD_GROUP, "DimensionBanner",
        "A heraldic banner per dimension, crossfading at portals.",
        DimensionBannerHud::new);

    private static final Identifier PORTAL_TEXTURE = Identifier.of("hunterbuddy", "textures/portal.png");

    private enum Dim {
        Overworld("OVERWORLD",
            new Color(87, 148, 220, 235), new Color(94, 146, 66, 235),
            new Color(232, 238, 246, 255), new Color(224, 232, 244, 255)),
        Nether("NETHER",
            new Color(92, 29, 32, 240), new Color(26, 8, 9, 240),
            new Color(200, 134, 60, 255), new Color(232, 168, 124, 255)),
        End("THE END",
            new Color(28, 16, 44, 240), new Color(10, 6, 18, 240),
            new Color(168, 140, 210, 255), new Color(216, 200, 240, 255));

        final String label;
        final Color top;
        final Color bottom;
        final Color border;
        final Color text;

        Dim(String label, Color top, Color bottom, Color border, Color text) {
            this.label = label;
            this.top = top;
            this.bottom = bottom;
            this.border = border;
            this.text = text;
        }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPanel = settings.createGroup("Panel");

    private final Setting<Double> bannerWidth = sgGeneral.add(new DoubleSetting.Builder()
        .name("width").description("Width of the banner.")
        .defaultValue(44.0).min(28.0).max(90.0).sliderRange(32.0, 70.0).build());

    private final Setting<Boolean> showName = sgGeneral.add(new BoolSetting.Builder()
        .name("name").description("The dimension name, stacked letter by letter.")
        .defaultValue(true).build());

    private final Setting<Boolean> crenellations = sgGeneral.add(new BoolSetting.Builder()
        .name("crenellations").description("The cut bottom edge.")
        .defaultValue(true).build());

    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("text-scale").description("Scale of the stacked letters.")
        .defaultValue(0.85).min(0.5).max(1.6).sliderRange(0.6, 1.3).build());

    private final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow").description("Render shadow behind the letters.").defaultValue(true).build());

    private final HudGlowPanel panel = new HudGlowPanel(sgPanel);

    private Dim lastDim = Dim.Overworld;
    private long changedAt;

    public DimensionBannerHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        Dim dim;

        if (MeteorClient.mc.world != null) {
            dim = of(MeteorClient.mc.world.getRegistryKey());
        } else if (isInEditor()) {
            // Cycles in the editor so all three coats of arms can be judged.
            Dim[] all = Dim.values();
            dim = all[(int) ((System.currentTimeMillis() / 3000L) % all.length)];
        } else {
            setSize(0.0, 0.0);
            return;
        }

        long now = System.currentTimeMillis();
        if (dim != lastDim) {
            lastDim = dim;
            changedAt = now;
        }

        // The fade-in is the ceremony; fading the old one out too would need two banners drawn
        // at once for a border crossing nobody watches in slow motion.
        double fade = changedAt == 0L ? 1.0 : Math.min(1.0, (now - changedAt) / 500.0);

        boolean shadow = textShadow.get();
        double scale = textScale.get();
        double pad = panel.padding();
        double w = bannerWidth.get();

        double motifH = 30.0;
        double lineH = renderer.textHeight(shadow, scale);
        double lettersH = showName.get() ? dim.label.length() * (lineH * 0.92) : 0.0;
        double crenH = crenellations.get() ? 7.0 : 0.0;
        double h = 4.0 + motifH + (showName.get() ? 4.0 + lettersH : 0.0) + 6.0;

        panel.draw(renderer, x + pad, y + pad, w, h + crenH, HudGlowPanel.Severity.OK);

        double left = x + pad;
        double top = y + pad;

        Color topC = withFade(dim.top, fade);
        Color bottomC = withFade(dim.bottom, fade);
        Color borderC = withFade(dim.border, fade);

        renderer.quad(left - 1.0, top - 1.0, w + 2.0, h + 2.0, borderC);
        renderer.quad(left, top, w, h, topC, topC, bottomC, bottomC);

        if (crenellations.get()) {
            double toothW = w / 4.0;
            for (int i = 0; i < 4; i++) {
                double tx = left + i * toothW;
                renderer.triangle(tx, top + h, tx + toothW / 2.0, top + h + crenH, tx + toothW, top + h, bottomC);
            }
        }

        drawMotif(renderer, dim, left, top + 4.0, w, motifH, fade);

        if (showName.get()) {
            double ly = top + 4.0 + motifH + 4.0;
            Color letter = withFade(dim.text, fade);

            for (char c : dim.label.toCharArray()) {
                String s = String.valueOf(c);
                renderer.text(s, left + (w - renderer.textWidth(s, shadow, scale)) / 2.0, ly, letter, shadow, scale);
                ly += lineH * 0.92;
            }
        }

        setSize(w + pad * 2.0, h + crenH + pad * 2.0);
    }

    private void drawMotif(HudRenderer renderer, Dim dim, double left, double top, double w, double motifH, double fade) {
        switch (dim) {
            case Nether -> {
                // The addon's own portal frame, breathing.
                double shimmer = 0.75 + 0.25 * Math.sin(System.currentTimeMillis() / 400.0);
                double pw = 16.0;
                double ph = motifH - 4.0;
                double px = left + (w - pw) / 2.0;
                double py = top + 2.0;
                int alpha = (int) (255 * fade * shimmer);
                renderer.post(() -> renderer.texture(PORTAL_TEXTURE, px, py, pw, ph,
                    new Color(255, 255, 255, Math.max(0, Math.min(255, alpha)))));
            }
            case Overworld -> {
                Color sun = withFade(new Color(255, 248, 207, 255), fade);
                renderer.quad(left + w / 2.0 - 5.0, top + 4.0, 10.0, 10.0, sun);
                Color grass = withFade(new Color(103, 165, 62, 255), fade);
                renderer.quad(left + 4.0, top + motifH - 7.0, w - 8.0, 5.0, grass);
            }
            case End -> {
                Color pale = withFade(new Color(216, 200, 240, 230), fade);
                renderer.quad(left + w * 0.30, top + 6.0, 2.0, 2.0, pale);
                renderer.quad(left + w * 0.62, top + 12.0, 3.0, 3.0, pale);
                renderer.quad(left + w * 0.42, top + 20.0, 2.0, 2.0, pale);
            }
        }
    }

    private static Dim of(RegistryKey<World> key) {
        if (World.NETHER.equals(key)) return Dim.Nether;
        if (World.END.equals(key)) return Dim.End;
        return Dim.Overworld;
    }

    private static Color withFade(Color color, double fade) {
        return new Color(color.r, color.g, color.b, (int) (color.a * fade));
    }
}
