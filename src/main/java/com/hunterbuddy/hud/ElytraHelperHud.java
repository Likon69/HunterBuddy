package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.hud.elytrahelper.FlightSession;
import com.hunterbuddy.hud.elytrahelper.HelperFormats;
import com.hunterbuddy.hud.elytrahelper.HelperLabels;
import com.hunterbuddy.hud.elytrahelper.HelperSettings;
import com.hunterbuddy.hud.elytrahelper.PanelWriter;
import com.hunterbuddy.hud.elytrahelper.TextStyle;
import com.hunterbuddy.util.SessionStats;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import net.minecraft.client.network.ClientPlayerEntity;

public class ElytraHelperHud extends HudElement {
    public static final HudElementInfo<ElytraHelperHud> INFO = new HudElementInfo<>(
        HunterBuddyAddon.HUD_GROUP,
        "elytra-helper",
        "Flight panel with the session time, distance, rockets used and speeds.",
        ElytraHelperHud::new);

    private static final double MIN_WIDTH = 80.0;

    private final HelperSettings config = new HelperSettings(settings);
    private final FlightSession session = new FlightSession();

    public ElytraHelperHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        PanelWriter writer = new PanelWriter(renderer, x, y, TextStyle.from(config));
        ClientPlayerEntity player = MeteorClient.mc.player;
        if (player != null && MeteorClient.mc.world != null) {
            writeLive(writer, player);
        } else if (isInEditor()) {
            writePreview(writer);
        } else {
            return;
        }
        setSize(Math.max(writer.widest(), MIN_WIDTH), writer.height());
    }

    private void writeLive(PanelWriter writer, ClientPlayerEntity player) {
        long now = System.currentTimeMillis();
        session.update(player, now);
        HelperLabels labels = config.compactMode.get() ? HelperLabels.COMPACT : HelperLabels.FULL;
        writeTitle(writer, labels);
        if (config.sections.showSession.get()) writeSession(writer, labels, player, now);
        if (config.sections.showSpeed.get()) writer.pair(labels.speed(), speedText(FlightSession.speedOf(player)));
    }

    private void writeSession(PanelWriter writer, HelperLabels labels, ClientPlayerEntity player, long now) {
        writer.line(labels.header(), config.colors.section.get());
        writer.pair(labels.flying(), HelperFormats.time(session.flyingSeconds()));
        if (session.hasStarted()) writer.pair(labels.session(), HelperFormats.time((long) session.sessionSeconds(now)));
        writer.pair(labels.distance(), HelperFormats.distance(session.distance()));
        writer.pair(labels.rockets(), String.valueOf(SessionStats.get().rocketsUsed()));
        if (config.sections.showAverageSpeed.get()) writer.pair(labels.average(), speedText(session.averageSpeed()));
        if (config.sections.showPeakSpeed.get()) writer.pair(labels.peak(), speedText(session.peakSpeed()));
        if (session.hasStartPosition()) {
            writer.pair(labels.fromStart(), HelperFormats.distance(session.distanceFromStart(player.getEntityPos())));
        }
    }

    private void writePreview(PanelWriter writer) {
        HelperLabels labels = HelperLabels.FULL;
        writeTitle(writer, labels);
        if (config.sections.showSession.get()) {
            writer.line(labels.header(), config.colors.section.get());
            writer.pair(labels.flying(), "12m 34s");
            writer.pair(labels.rockets(), "48");
            writer.pair(labels.session(), "25m 10s");
            writer.pair(labels.distance(), "42.3k");
            writer.pair(labels.fromStart(), "38.1k");
        }
        if (config.sections.showSpeed.get()) writer.pair(labels.speed(), "33.2 b/s");
    }

    private void writeTitle(PanelWriter writer, HelperLabels labels) {
        if (config.sections.showTitle.get()) writer.line(labels.title(), config.colors.title.get());
    }

    private String speedText(double blocksPerSecond) {
        return HelperFormats.speed(blocksPerSecond, config.sections.showSpeedKmh.get());
    }
}
