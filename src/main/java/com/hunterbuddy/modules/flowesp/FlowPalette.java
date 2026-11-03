package com.hunterbuddy.modules.flowesp;

import meteordevelopment.meteorclient.utils.render.color.Color;

final class FlowPalette {
    private static final double FLOW_SCALE = 0.7;
    private static final double AGED_RGB_SCALE = 1.3;
    private static final double AGED_ALPHA_SCALE = 1.2;
    private static final double ROOF_RGB_SCALE = 1.5;
    private static final double ROOF_ALPHA_SCALE = 1.2;
    private static final double AGED_RATIO = 2.0;
    private static final int MAX_CHANNEL = 255;

    private Color lavaSource = new Color();
    private Color lavaFlow = new Color();
    private Color lavaAged = new Color();
    private Color lavaRoof = new Color();
    private Color waterSource = new Color();
    private Color waterFlow = new Color();
    private Color waterAged = new Color();

    void refresh(GeneralOptions options) {
        Color lava = options.lavaColor.get();
        Color water = options.waterColor.get();
        lavaSource = new Color(lava);
        lavaFlow = scaled(lava, FLOW_SCALE, 1.0);
        lavaAged = scaled(lava, AGED_RGB_SCALE, AGED_ALPHA_SCALE);
        lavaRoof = scaled(lava, ROOF_RGB_SCALE, ROOF_ALPHA_SCALE);
        waterSource = new Color(water);
        waterFlow = scaled(water, FLOW_SCALE, 1.0);
        waterAged = scaled(water, AGED_RGB_SCALE, AGED_ALPHA_SCALE);
    }

    Color solidColor(FlowChunk chunk, FlowColumn column) {
        FlowColumn.ColumnFlags flags = column.flags();
        if (flags.lava() && flags.roof()) return lavaRoof;
        if (chunk.flowRatio() > AGED_RATIO) return flags.lava() ? lavaAged : waterAged;
        if (flags.hasSource()) return flags.lava() ? lavaSource : waterSource;
        return flags.lava() ? lavaFlow : waterFlow;
    }

    private static Color scaled(Color base, double rgbScale, double alphaScale) {
        return new Color(clamp(base.r * rgbScale), clamp(base.g * rgbScale), clamp(base.b * rgbScale), clamp(base.a * alphaScale));
    }

    private static int clamp(double channel) {
        return (int) Math.max(0, Math.min(MAX_CHANNEL, channel));
    }
}
