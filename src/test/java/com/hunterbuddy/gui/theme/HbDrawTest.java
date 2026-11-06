package com.hunterbuddy.gui.theme;

import meteordevelopment.meteorclient.utils.render.color.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class HbDrawTest {
    @Test
    void effectAlphaMultipliesTheEditableAlphaWithoutChangingIt() {
        Color source = new Color(30, 80, 120, 128);
        Color target = new Color();
        assertSame(target, HbDraw.withAlpha(target, source, 0.4));
        assertEquals(51, target.a);
        assertEquals(30, target.r);
        assertEquals(80, target.g);
        assertEquals(120, target.b);
        assertEquals(128, source.a);
        source.a = 0;
        assertEquals(0, HbDraw.withAlpha(target, source, 0.9).a);
        assertEquals(0, HbDraw.withAlpha(source, 0.9).a);
    }

    @Test
    void readableTextKeepsItsMinimumWithoutMakingEffectsOpaque() {
        Color source = new Color(30, 80, 120, 0);
        Color text = new Color();
        assertEquals(160, HbDraw.textColor(text, source).a);
        assertEquals(0, source.a);
        assertEquals(0, HbDraw.withAlpha(new Color(), source, 1).a);
        source.a = 220;
        assertEquals(220, HbDraw.textColor(text, source).a);
    }
}
