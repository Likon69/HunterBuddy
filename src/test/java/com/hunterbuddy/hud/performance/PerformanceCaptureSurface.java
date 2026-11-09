package com.hunterbuddy.hud.performance;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class PerformanceCaptureSurface {
    private record Text(String value, double x, double y, int color, boolean shadow, double scale) { }
    private final ClassLoader loader;
    private final Map<Integer, MeteorCaptureFont> fonts = new HashMap<>();
    private final ArrayList<Text> texts = new ArrayList<>();
    public final BufferedImage image = new BufferedImage(1120, 650, BufferedImage.TYPE_INT_ARGB);
    private final Graphics2D graphics = image.createGraphics();

    PerformanceCaptureSurface(ClassLoader loader) {
        this.loader = loader;
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setColor(new java.awt.Color(22, 43, 51)); graphics.fillRect(0, 0, 1120, 650);
        for (int row = 0; row < 12; row++) {
            for (int column = 0; column < 22; column++) {
                int gray = (row * 7 + column * 13) % 4 * 5;
                graphics.setColor(new java.awt.Color(31 + gray, 52 + gray, 58 + gray));
                graphics.fillRect(column * 54 + row % 2 * 12, row * 58, 50, 54);
            }
        }
        graphics.scale(2, 2);
    }

    public void quad(double x, double y, double width, double height, Color color) {
        quad(x, y, width, height, color, color, color, color);
    }

    public void quad(double x, double y, double width, double height, Color tl, Color tr, Color br, Color bl) {
        for (int py = Math.max(0, (int) Math.ceil(y * 2 - 0.5)); py < Math.min(image.getHeight(), (int) Math.ceil((y + height) * 2 - 0.5)); py++) {
            double mix = Math.clamp(((py + 0.5) / 2 - y) / height, 0, 1);
            for (int px = Math.max(0, (int) Math.ceil(x * 2 - 0.5)); px < Math.min(image.getWidth(), (int) Math.ceil((x + width) * 2 - 0.5)); px++) {
                blend(px, py, tl.r + (bl.r - tl.r) * mix, tl.g + (bl.g - tl.g) * mix,
                    tl.b + (bl.b - tl.b) * mix, tl.a + (bl.a - tl.a) * mix);
            }
        }
    }

    public void triangle(double ax, double ay, double bx, double by, double cx, double cy, Color color) {
        Path2D path = new Path2D.Double(); path.moveTo(ax, ay); path.lineTo(bx, by); path.lineTo(cx, cy); path.closePath();
        int left = Math.max(0, (int) Math.floor(Math.min(ax, Math.min(bx, cx)) * 2));
        int right = Math.min(image.getWidth(), (int) Math.ceil(Math.max(ax, Math.max(bx, cx)) * 2));
        int top = Math.max(0, (int) Math.floor(Math.min(ay, Math.min(by, cy)) * 2));
        int bottom = Math.min(image.getHeight(), (int) Math.ceil(Math.max(ay, Math.max(by, cy)) * 2));
        for (int py = top; py < bottom; py++) for (int px = left; px < right; px++) {
            if (path.contains((px + 0.5) / 2, (py + 0.5) / 2)) blend(px, py, color.r, color.g, color.b, color.a);
        }
    }

    private void blend(int x, int y, double red, double green, double blue, double alpha) {
        int previous = image.getRGB(x, y);
        double mix = alpha / 255;
        int r = (int) Math.round(red * mix + (previous >>> 16 & 255) * (1 - mix));
        int g = (int) Math.round(green * mix + (previous >>> 8 & 255) * (1 - mix));
        int b = (int) Math.round(blue * mix + (previous & 255) * (1 - mix));
        image.setRGB(x, y, 0xff000000 | r << 16 | g << 8 | b);
    }

    public double textWidth(String value, boolean shadow, double scale) {
        try { return (font(scale).width(value) + (shadow ? 1 : 0)) * scale + (shadow ? 1 : 0); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }

    public double textHeight(boolean shadow, double scale) {
        return (font(scale).height() + 1 + (shadow ? 1 : 0)) * scale;
    }

    public double text(String value, double x, double y, Color color, boolean shadow, double scale) {
        texts.add(new Text(value, x, y, color.a << 24 | color.r << 16 | color.g << 8 | color.b, shadow, scale));
        return x + textWidth(value, shadow, scale);
    }

    void flush() throws Exception {
        for (Text text : texts) {
            MeteorCaptureFont font = font(text.scale());
            if (text.shadow()) font.draw(graphics, text.value(), text.x() + 1, text.y() + 1,
                (text.color() >>> 24) * 180 / 255 << 24 | 0x3c3c3c, text.scale());
            font.draw(graphics, text.value(), text.x(), text.y(), text.color(), text.scale());
        }
        texts.clear();
    }

    private MeteorCaptureFont font(double scale) {
        int height = (int) Math.round(18 * scale);
        return fonts.computeIfAbsent(height, key -> {
            try { return new MeteorCaptureFont(loader, key); }
            catch (Exception error) { throw new IllegalStateException(error); }
        });
    }

    int atlasCount() { return fonts.size(); }

}
