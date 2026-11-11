package com.hunterbuddy.hud.performance;

import com.hunterbuddy.hud.system.SystemStatsSampler;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import meteordevelopment.meteorclient.utils.render.color.Color;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SystemStatsCaptureTest {
    @Test
    void capturesGlassAndFlatWithActualMeteorFontAtlases() throws Exception {
        PainterCaptureLoader loader = new PainterCaptureLoader();
        PerformanceCaptureSurface surface = new PerformanceCaptureSurface(loader);
        Class<?> rendererType = loader.loadClass("meteordevelopment.meteorclient.systems.hud.HudRenderer");
        Object renderer = rendererType.getConstructor(PerformanceCaptureSurface.class).newInstance(surface);
        Class<?> painterType = loader.loadClass("com.hunterbuddy.hud.system.SystemStatsPainter");
        Object painter = painterType.getConstructor().newInstance();
        var measure = painterType.getMethod("measure", rendererType, int.class, double.class, boolean.class, int.class);
        var draw = painterType.getMethod("draw", rendererType, SystemStatsSampler.class, double.class, double.class,
            boolean.class, boolean.class, boolean.class, int.class, double.class, double.class, double.class, double.class,
            Color.class, Color.class, Color.class, Color.class, Color.class);
        Class<?> glassType = loader.loadClass("com.hunterbuddy.hud.glass.GlassPainter");
        Object glass = glassType.getConstructor().newInstance();
        SystemStatsSampler data = SystemStatsSampler.preview();
        Color background = new Color(17, 24, 35, 222);
        Color text = new Color(236, 243, 252);
        Color ram = new Color(126, 200, 255);
        Color cpu = new Color(110, 240, 130);
        for (int variant = 0; variant < 4; variant++) {
            boolean flat = variant % 2 != 0;
            boolean graph = variant < 2;
            double left = variant % 2 == 0 ? 16 : 286;
            double top = variant < 2 ? 24 : 190;
            measure.invoke(painter, renderer, data.ramDigits(), 1.0, graph, 2);
            double width = (double) painterType.getMethod("width").invoke(painter);
            double height = (double) painterType.getMethod("height").invoke(painter);
            assertTrue(width + 28 < 250);
            if (flat) glassType.getMethod("flatCard", rendererType, double.class, double.class, double.class,
                double.class, double.class, double.class, Color.class)
                .invoke(glass, renderer, left, top, width + 28, height + 28, 1.0, 1.0, background);
            else {
                glassType.getMethod("card", rendererType, double.class, double.class, double.class,
                    double.class, double.class, double.class, boolean.class, Color.class)
                    .invoke(glass, renderer, left, top, width + 28, height + 28, 1.0, 1.0, true, background);
                glassType.getMethod("accentBar", rendererType, double.class, double.class, double.class,
                    double.class, double.class, Color.class)
                    .invoke(glass, renderer, left, top, height + 28, 1.0, 1.0, ram);
            }
            draw.invoke(painter, renderer, data, left + 14, top + 14, true, true, graph, 60,
                70.0, 85.0, 50.0, 75.0, text, ram, cpu, new Color(255, 200, 80), new Color(255, 80, 80));
            surface.flush();
        }
        assertEquals(2, surface.atlasCount());
        Path output = Path.of("build/reports/systemstats/preview.png");
        Files.createDirectories(output.getParent());
        ImageIO.write(surface.image, "png", output.toFile());
    }
}
