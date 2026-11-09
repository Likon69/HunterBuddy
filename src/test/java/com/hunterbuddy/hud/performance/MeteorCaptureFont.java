package com.hunterbuddy.hud.performance;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import org.lwjgl.BufferUtils;

final class MeteorCaptureFont {
    private final Object font;
    private final Method width;
    private final Int2ObjectMap<?> characters;
    private final ByteBuffer bitmap;
    private final double baseline;
    private final int height;

    MeteorCaptureFont(ClassLoader loader, int height) throws Exception {
        byte[] bytes;
        try (var stream = getClass().getResourceAsStream("/assets/meteor-client/fonts/JetBrains Mono.ttf")) {
            if (stream == null) throw new IllegalStateException("Meteor font resource missing");
            bytes = stream.readAllBytes();
        }
        ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length).put(bytes).flip();
        Class<?> type = loader.loadClass("meteordevelopment.meteorclient.renderer.text.Font");
        font = type.getConstructor(ByteBuffer.class, int.class).newInstance(buffer, height);
        width = type.getMethod("getWidth", String.class, int.class);
        characters = (Int2ObjectMap<?>) field(type, "charMap").get(font);
        Object texture = type.getField("texture").get(font);
        bitmap = (ByteBuffer) texture.getClass().getField("bitmap").get(texture);
        baseline = field(type, "ascent").getFloat(font) * field(type, "scale").getFloat(font);
        this.height = height;
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }

    double width(String value) throws Exception { return (double) width.invoke(font, value, value.length()); }

    int height() { return height; }

    double draw(Graphics2D graphics, String value, double x, double y, int rgba, double scale) throws Exception {
        for (int i = 0; i < value.length(); i++) {
            Object character = characters.get(value.charAt(i));
            if (character == null) character = characters.get(32);
            int sx = Math.round(component(character, "u0") * 2048);
            int sy = Math.round(component(character, "v0") * 2048);
            int width = Math.round(component(character, "u1") * 2048) - sx;
            int height = Math.round(component(character, "v1") * 2048) - sy;
            if (width > 0 && height > 0) {
                BufferedImage glyph = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                for (int row = 0; row < height; row++) {
                    for (int column = 0; column < width; column++) {
                        int alpha = (bitmap.get((sy + row) * 2048 + sx + column) & 255) * (rgba >>> 24) / 255;
                        glyph.setRGB(column, row, alpha << 24 | rgba & 0xffffff);
                    }
                }
                AffineTransform transform = AffineTransform.getTranslateInstance(x + component(character, "x0") * scale,
                    y + baseline * scale + component(character, "y0") * scale);
                transform.scale((component(character, "x1") - component(character, "x0")) * scale / width,
                    (component(character, "y1") - component(character, "y0")) * scale / height);
                graphics.drawImage(glyph, transform, null);
            }
            x += component(character, "xAdvance") * scale;
        }
        return x;
    }

    private static float component(Object record, String name) throws Exception {
        Method method = record.getClass().getDeclaredMethod(name); method.setAccessible(true); return (float) method.invoke(record);
    }
}
