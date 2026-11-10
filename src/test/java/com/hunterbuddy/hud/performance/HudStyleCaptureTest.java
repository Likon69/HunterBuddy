package com.hunterbuddy.hud.performance;

import com.hunterbuddy.hud.toast.ModuleToastTimeline;
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
                42.0 + i * 80, width, cardHeight, scale, 1.0, false, com.hunterbuddy.hud.glass.GlassStyle.Glass, i == 2 ? 0.45 : 1.0,
                i == 0 ? 0.8 : i == 1 ? 0.6 : 0.1, true, false,
                notices[i].enabled() ? new Color(106, 231, 193) : new Color(244, 137, 161),
                new Color(17, 24, 35, 222), new Color(236, 243, 252));
            surface.flush();
            assertTrue(surface.atlasCount() <= 3);
            save(surface, "toast-" + scale);
        }
    }

    private static void save(PerformanceCaptureSurface surface, String name) throws Exception {
        Path directory = Path.of("build/reports/hud-style"); Files.createDirectories(directory);
        ImageIO.write(surface.image, "png", directory.resolve(name + ".png").toFile());
    }
}
