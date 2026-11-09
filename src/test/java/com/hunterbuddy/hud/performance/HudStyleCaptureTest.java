package com.hunterbuddy.hud.performance;

import com.hunterbuddy.hud.text.HudTextScale;
import com.hunterbuddy.hud.toast.ModuleToastTimeline;
import com.hunterbuddy.modules.aura.AuraHealth;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import meteordevelopment.meteorclient.utils.render.color.Color;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class HudStyleCaptureTest {
    @Test
    void capturesTheActualToastPainterAtBothRequestedScales() throws Exception {
        for (double scale : new double[] {1, 0.7}) {
            PainterCaptureLoader loader = new PainterCaptureLoader();
            PerformanceCaptureSurface surface = new PerformanceCaptureSurface(loader);
            Class<?> rendererClass = loader.loadClass("meteordevelopment.meteorclient.systems.hud.HudRenderer");
            Object renderer = rendererClass.getConstructor(PerformanceCaptureSurface.class).newInstance(surface);
            Class<?> painterClass = loader.loadClass("com.hunterbuddy.hud.toast.ModuleToastPainter");
            Object painter = painterClass.getConstructor().newInstance();
            Method height = painterClass.getMethod("height", rendererClass, double.class, double.class, boolean.class);
            Method draw = null;
            for (Method method : painterClass.getMethods()) if (method.getName().equals("draw")) draw = method;
            assertNotNull(draw);
            ModuleToastTimeline.Notice[] notices = {
                new ModuleToastTimeline.Notice("Elytra Auto Fly", "R", true),
                new ModuleToastTimeline.Notice("Kill Aura Plus", "Ctrl + G", false),
                new ModuleToastTimeline.Notice("Pet", "P", true)
            };
            double width = 312 * scale;
            double cardHeight = (double) height.invoke(null, renderer, scale, 1.0, false);
            for (int i = 0; i < notices.length; i++) draw.invoke(painter, renderer, i, notices[i], (560 - width) / 2,
                42.0 + i * 80, width, cardHeight, scale, 1.0, false, i == 2 ? 0.45 : 1.0,
                i == 0 ? 0.8 : i == 1 ? 0.6 : 0.1, true, false,
                notices[i].enabled() ? new Color(106, 231, 193) : new Color(244, 137, 161),
                new Color(17, 24, 35, 222), new Color(236, 243, 252));
            surface.flush();
            assertTrue(surface.atlasCount() <= 3);
            save(surface, "toast-" + scale);
        }
    }

    @Test
    void capturesTheActualGoldAndRedNameplateWithItsHealthCell() throws Exception {
        for (double scale : new double[] {1, 0.7}) {
            PainterCaptureLoader loader = new PainterCaptureLoader();
            PerformanceCaptureSurface surface = new PerformanceCaptureSurface(loader);
            Class<?> rendererClass = loader.loadClass("meteordevelopment.meteorclient.systems.hud.HudRenderer");
            Object renderer = rendererClass.getConstructor(PerformanceCaptureSurface.class).newInstance(surface);
            Class<?> trackClass = loader.loadClass("com.hunterbuddy.modules.aura.AuraTrack");
            Class<?> painterClass = loader.loadClass("com.hunterbuddy.modules.aura.AuraNameplatePainter");
            var constructor = painterClass.getDeclaredConstructor(); constructor.setAccessible(true);
            Object painter = constructor.newInstance();
            Method draw = painterClass.getDeclaredMethod("draw", rendererClass, trackClass, double.class, double.class,
                double.class, double.class, double.class, long.class, Color.class, Color.class, Color.class, Color.class);
            draw.setAccessible(true);
            Method text = painterClass.getDeclaredMethod("text", rendererClass, String.class, double.class, double.class, Color.class, double.class);
            text.setAccessible(true);
            for (int row = 0; row < 2; row++) {
                var trackConstructor = trackClass.getDeclaredConstructor(); trackConstructor.setAccessible(true);
                Object track = trackConstructor.newInstance();
                AuraHealth health = (AuraHealth) field(trackClass, "health").get(track);
                health.observe(row == 0 ? 112 : 13.5f, row == 0 ? 120 : 20, 0, false, 1000);
                float loss = health.observe(row == 0 ? 57 : 7, row == 0 ? 120 : 20, row == 0 ? 4 : 0, false, 1100);
                assertEquals(row == 0 ? 55 : 6.5, loss);
                Method labels = trackClass.getDeclaredMethod("labels"); labels.setAccessible(true); labels.invoke(track);
                String name = row == 0 ? "Guardian" : "Skeleton";
                field(trackClass, "displayName").set(track, name);
                field(trackClass, "nameWidth").setDouble(track, surface.textWidth(name, false, HudTextScale.NORMAL));
                String life = (String) field(trackClass, "life").get(track);
                String gold = (String) field(trackClass, "gold").get(track);
                field(trackClass, "lifeWidth").setDouble(track, surface.textWidth(life, false, HudTextScale.SMALL));
                field(trackClass, "goldWidth").setDouble(track, surface.textWidth(gold, false, HudTextScale.SMALL));
                double width = 160 * scale;
                double x = (560 - width) / 2;
                double y = 90 + row * 130;
                draw.invoke(painter, renderer, track, x, y, width, scale, 1.0, 1200L,
                    new Color(200, 24, 32), new Color(96, 12, 18), new Color(255, 211, 90), new Color(0, 0, 0, 245));
                String damage = row == 0 ? "55" : "6.5";
                text.invoke(painter, renderer, damage, 280 - surface.textWidth(damage, false, HudTextScale.TITLE) / 2,
                    y - 30, row == 0 ? new Color(255, 255, 255) : new Color(255, 173, 80), HudTextScale.TITLE);
            }
            surface.flush();
            assertTrue(surface.atlasCount() <= 3);
            save(surface, "nameplate-" + scale);
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }

    private static void save(PerformanceCaptureSurface surface, String name) throws Exception {
        Path directory = Path.of("build/reports/hud-style"); Files.createDirectories(directory);
        ImageIO.write(surface.image, "png", directory.resolve(name + ".png").toFile());
    }
}
