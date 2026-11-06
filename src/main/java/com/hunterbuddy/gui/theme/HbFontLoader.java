package com.hunterbuddy.gui.theme;

import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.renderer.Fonts;
import meteordevelopment.meteorclient.renderer.text.CustomTextRenderer;
import meteordevelopment.meteorclient.renderer.text.FontFace;
import meteordevelopment.meteorclient.renderer.text.FontFamily;
import meteordevelopment.meteorclient.renderer.text.FontInfo;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.renderer.text.VanillaTextRenderer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class HbFontLoader {
    private final List<CustomTextRenderer> owned = new ArrayList<>();
    private boolean warned;

    TextRenderer load(String name, boolean strong) {
        FontInfo.Type type = strong ? FontInfo.Type.Bold : FontInfo.Type.Regular;
        Set<FontFace> candidates = new LinkedHashSet<>();
        add(candidates, Fonts.getFamily(name), type);
        add(candidates, Fonts.getFamily(name), FontInfo.Type.Regular);
        add(candidates, Fonts.getFamily("JetBrains Mono"), type);
        add(candidates, Fonts.getFamily("JetBrains Mono"), FontInfo.Type.Regular);
        for (FontFace face : candidates) {
            try {
                CustomTextRenderer renderer = new CustomTextRenderer(face);
                owned.add(renderer);
                return renderer;
            } catch (Exception exception) {
                warn(name);
            }
        }
        warn(name);
        return VanillaTextRenderer.INSTANCE;
    }

    private void add(Set<FontFace> candidates, FontFamily family, FontInfo.Type type) {
        FontFace face = family == null ? null : family.get(type);
        if (face != null) candidates.add(face);
        else if (candidates.isEmpty()) warn(family == null ? "unavailable family" : family.getName());
    }

    private void warn(String name) {
        if (warned) return;
        warned = true;
        HunterBuddyAddon.LOG.warn("Theme font '{}' is unavailable or could not be loaded; using a fallback.", name);
    }

    void reset() {
        for (CustomTextRenderer renderer : owned) renderer.destroy();
        owned.clear();
        warned = false;
    }
}
