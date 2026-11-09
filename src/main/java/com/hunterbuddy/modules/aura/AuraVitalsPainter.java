package com.hunterbuddy.modules.aura;

import com.hunterbuddy.hud.text.HudTextScale;
import com.hunterbuddy.hud.text.OverlayPass;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3d;

final class AuraVitalsPainter {
    private final AuraVitalsSettings settings;
    private final Color textColor = new Color();
    private final Color fill = new Color();
    private final AuraNameplatePainter nameplate = new AuraNameplatePainter();
    private final Vector3d screen = new Vector3d();
    private final double[] occupiedX = new double[16];
    private final double[] occupiedY = new double[16];
    private int occupied;

    AuraVitalsPainter(AuraVitalsSettings settings) { this.settings = settings; }

    void measure(AuraTrack track) {
        double size = settings.size.get();
        if (track.measuredSize == size) return;
        track.measuredSize = size;
        var font = MeteorClient.mc.textRenderer;
        track.lifeWidth = font.getWidth(track.life) * HudTextScale.SMALL * 2;
        track.goldWidth = font.getWidth(track.gold) * HudTextScale.SMALL * 2;
        double available = width() - Math.max(22 * size, track.lifeWidth + 8) - 8;
        String name = track.name;
        while (!name.isEmpty() && font.getWidth(name) * HudTextScale.NORMAL * 2 > available) {
            int end = name.offsetByCodePoints(name.length(), -1);
            name = name.substring(0, end);
        }
        track.displayName = name;
        track.nameWidth = font.getWidth(name) * HudTextScale.NORMAL * 2;
    }

    void render(Render2DEvent event, AuraVitals owner, AuraTrack[] tracks, int count, AuraDamage[] numbers, long now) {
        boolean any = settings.bars.get() && count > 0;
        if (settings.damage.get()) for (int i = 0; i < numbers.length; i++) any |= numbers[i].owner != null;
        if (!any) return;
        OverlayPass.begin(event.drawContext);
        try {
            occupied = 0;
            if (settings.bars.get()) {
                for (int i = 0; i < count; i++) {
                    AuraTrack track = tracks[i];
                    if (track.entity.isRemoved() || !track.entity.isAlive() || !track.health.trusted(track.player(), now)) continue;
                    double alpha = owner.persistent(track) ? 1 : Math.clamp((track.hit + settings.duration.get() * 1000 - now) / 600.0, 0, 1);
                    if (alpha <= 0 || !project(track.entity, event.tickDelta)) continue;
                    double width = width();
                    double height = height();
                    double x = screen.x - width / 2;
                    double y = screen.y - height - 8;
                    for (int attempt = 0; attempt < 3 && overlaps(x, y, width, height); attempt++) y -= height + 5;
                    if (overlaps(x, y, width, height) || x < 0 || y < 0 || x + width > MeteorClient.mc.getWindow().getFramebufferWidth()) continue;
                    occupiedX[occupied] = x; occupiedY[occupied++] = y;
                    bar(track, x, y, width, alpha, now);
                }
            }
            if (settings.damage.get()) {
                for (int i = 0; i < numbers.length; i++) {
                    AuraDamage number = numbers[i];
                    if (number.owner == null || !selected(number.owner, tracks, count) || !number.owner.health.trusted(number.owner.player(), now)) continue;
                    if (number.owner.entity.squaredDistanceTo(MeteorClient.mc.player) > settings.distance.get() * settings.distance.get()) continue;
                    double progress = (now - number.born) / (settings.damageDuration.get() * 1000);
                    if (progress < 0 || progress >= 1) continue;
                    screen.set(number.x, number.y, number.z);
                    if (!NametagUtils.to2D(screen, 1, false) || screen.z < 0 || screen.z > 1) continue;
                    double textScale = HudTextScale.TITLE;
                    double alpha = Math.pow(1 - progress, 1.4);
                    Color color = number.critical ? settings.criticalDamage.get() : settings.normalDamage.get();
                    textColor.set(color.r, color.g, color.b, (int) (color.a * alpha));
                    nameplate.text(HudRenderer.INSTANCE, number.text, screen.x - number.width * textScale + number.lane * (9 + progress * 12),
                        screen.y - height() - 32 - progress * 34, textColor, textScale);
                }
            }
        } finally { OverlayPass.end(event.drawContext); Utils.unscaledProjection(); }
    }

    private void bar(AuraTrack track, double x, double y, double width, double alpha, long now) {
        HudRenderer renderer = HudRenderer.INSTANCE;
        double s = settings.size.get();
        double ratio = track.health.fraction();
        Color from = ratio < 0.5 ? settings.low.get() : settings.warning.get();
        Color to = ratio < 0.5 ? settings.warning.get() : settings.healthy.get();
        double mix = ratio < 0.5 ? ratio * 2 : (ratio - 0.5) * 2;
        fill.set((int) MathHelper.lerp(mix, from.r, to.r), (int) MathHelper.lerp(mix, from.g, to.g),
            (int) MathHelper.lerp(mix, from.b, to.b), (int) MathHelper.lerp(mix, from.a, to.a));
        nameplate.draw(renderer, track, x, y, width, s, alpha, now, fill, settings.trail.get(), settings.absorption.get(), settings.background.get());
    }

    private static boolean selected(AuraTrack owner, AuraTrack[] tracks, int count) {
        for (int i = 0; i < count; i++) if (tracks[i] == owner) return true;
        return false;
    }

    private boolean project(LivingEntity entity, float delta) {
        screen.set(MathHelper.lerp(delta, entity.lastX, entity.getX()), MathHelper.lerp(delta, entity.lastY, entity.getY()) + entity.getHeight() + 0.3,
            MathHelper.lerp(delta, entity.lastZ, entity.getZ()));
        return NametagUtils.to2D(screen, 1, false) && screen.z >= 0 && screen.z <= 1;
    }

    private boolean overlaps(double x, double y, double width, double height) {
        for (int i = 0; i < occupied; i++) if (x < occupiedX[i] + width + 4 && x + width + 4 > occupiedX[i]
            && y < occupiedY[i] + height + 4 && y + height + 4 > occupiedY[i]) return true;
        return false;
    }

    private double width() { return Math.clamp(160 * settings.size.get(), 96, 248); }

    private double height() { return 36 + 11 * settings.size.get(); }
}
