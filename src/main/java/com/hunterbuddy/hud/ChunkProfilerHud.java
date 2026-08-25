package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.ChunkProfiler;
import com.hunterbuddy.util.ChunkStreamSampler;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import java.util.Locale;

/**
 * Four numbers for the collection run: chunks written, backlog, chunks arriving
 * per second, and the verdict XaeroPlus gave the last one.
 *
 * <p>Deliberately nothing else. This is a control panel for a measurement, not a
 * readout to fly by: the moment it starts showing conclusions, it is answering
 * the question the data is supposed to answer.
 */
public class ChunkProfilerHud extends HudElement {
    public static final HudElementInfo<ChunkProfilerHud> INFO = new HudElementInfo<>(
        HunterBuddyAddon.HUD_GROUP, "ChunkProfiler",
        "Collection counters for the chunk-profiler module.",
        ChunkProfilerHud::new);

    public ChunkProfilerHud() {
        super(INFO);
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("text-scale").description("Scale of the text.")
        .defaultValue(1.0).min(0.1).sliderRange(0.1, 3.0).build());

    private final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow").description("Render a shadow behind the text.")
        .defaultValue(true).build());

    private final Setting<SettingColor> textColor = sgGeneral.add(new ColorSetting.Builder()
        .name("text-color").description("Colour of the counters.")
        .defaultValue(new SettingColor(200, 220, 255, 255)).build());

    private final Setting<SettingColor> warnColor = sgGeneral.add(new ColorSetting.Builder()
        .name("warn-color").description("Colour used once chunks have been dropped.")
        .defaultValue(new SettingColor(255, 140, 60, 255)).build());

    @Override
    public void render(HudRenderer renderer) {
        ChunkProfiler profiler = ChunkProfiler.get();

        if (profiler == null || !profiler.isActive()) {
            if (!isInEditor()) {
                setSize(0.0, 0.0);
                return;
            }

            draw(renderer, "profiled 0  queue 0  0.0/s  -", textColor.get());
            return;
        }

        int dropped = profiler.droppedCount();
        String line = String.format(Locale.ROOT, "profiled %d  queue %d  %.1f/s  %s",
            profiler.profiledCount(),
            profiler.queueSize(),
            ChunkStreamSampler.get().chunksPerSecond(),
            profiler.lastVerdict());

        // The dropped count only appears once it is not zero, and it takes the
        // whole line with it: a backlog that overflows is the one thing on this
        // panel that invalidates the run, so it should not be a quiet suffix.
        if (dropped > 0) line = line + "  dropped " + dropped;

        draw(renderer, line, dropped > 0 ? warnColor.get() : textColor.get());
    }

    private void draw(HudRenderer renderer, String line, Color color) {
        renderer.text(line, x, y, color, textShadow.get(), textScale.get());
        setSize(
            renderer.textWidth(line, textShadow.get(), textScale.get()),
            renderer.textHeight(textShadow.get(), textScale.get()));
    }
}
