package com.hunterbuddy.hud.performance;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import meteordevelopment.meteorclient.utils.render.color.Color;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PerformanceCaptureTest {
    @Test
    void capturesTheCompiledPainterWithActualMeteorFontAtlases() throws Exception {
        capture("after", 1);
        capture("scale-0.7", 0.7);
    }

    private static void capture(String name, double scale) throws Exception {
        PainterCaptureLoader loader = new PainterCaptureLoader();
        PerformanceCaptureSurface surface = new PerformanceCaptureSurface(loader);
        Class<?> rendererClass = loader.loadClass("meteordevelopment.meteorclient.systems.hud.HudRenderer");
        Object renderer = rendererClass.getConstructor(PerformanceCaptureSurface.class).newInstance(surface);
        Class<?> painterClass = loader.loadClass("com.hunterbuddy.hud.performance.PerformancePainter");
        var measure = painterClass.getMethod("measure", rendererClass, PerformanceMetric[].class, double.class, double.class,
            boolean.class, boolean.class, boolean.class);
        var draw = painterClass.getMethod("draw", rendererClass, PerformanceMetric[].class, double.class, double.class, long.class,
            boolean.class, boolean.class, Color.class, Color.class, Color.class);
        PerformanceMetric[] metrics = {
            new PerformanceMetric("FPS", "8888", ""), new PerformanceMetric("Ping", "8888 ms", " ms"), new PerformanceMetric("TPS", "88.8", "")
        };
        for (int row = 0; row < 3; row++) {
            Object painter = painterClass.getConstructor().newInstance();
            metrics[0].integer(row == 1 ? 36 : 144);
            metrics[1].integer(row == 1 ? 520 : 43);
            metrics[2].decimal(row == 1 ? 7.8f : 20);
            for (int i = 0; i < 3; i++) {
                metrics[i].level = row == 1 ? i == 0 ? 1 : 2 : 0;
                metrics[i].color.set(row == 1 ? i == 0 ? new Color(255, 200, 80) : new Color(255, 80, 80)
                    : i == 0 ? new Color(0, 255, 0) : new Color(110, 240, 130));
            }
            measure.invoke(painter, renderer, metrics, scale, 1.0, row == 2, row == 2, false);
            double width = (double) painterClass.getMethod("width").invoke(painter);
            assertTrue(width < 520);
            draw.invoke(painter, renderer, metrics, 24.0, 42.0 + row * 90, 1000L,
                false, true, new Color(17, 24, 35, 184), new Color(255, 255, 255), new Color(255, 80, 80));
            surface.flush();
        }
        assertTrue(surface.atlasCount() <= 3);
        Path directory = Path.of("build/reports/performance-painter"); Files.createDirectories(directory);
        ImageIO.write(surface.image, "png", directory.resolve(name + ".png").toFile());
    }
}
