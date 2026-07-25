/*
 * Port of com.lambda.config.blocks.LineConfig (lambda 1.21.11).
 */
package com.hunterbuddy.lambda;

import java.awt.Color;

public class LineConfig {
    public static class LineDashStyle {
        public final float dashLength;
        public final float gapLength;
        public final float dashOffset;
        public final boolean animated;
        public final float animationSpeed;

        public LineDashStyle(float dashLength, float gapLength, float dashOffset, boolean animated, float animationSpeed) {
            this.dashLength = dashLength;
            this.gapLength = gapLength;
            this.dashOffset = dashOffset;
            this.animated = animated;
            this.animationSpeed = animationSpeed;
        }
    }

    private Color startColor = new Color(255, 255, 255, 255);
    private Color endColor = new Color(255, 255, 255, 255);
    private float width = 1.0f;
    private boolean dashEnabled = false;
    private float dashLength = 1.0f;
    private float gapLength = 1.0f;
    private float dashOffset = 0.0f;
    private boolean animated = false;
    private float animationSpeed = 1.0f;

    public LineDashStyle getDashStyle() {
        if (!dashEnabled) return null;
        return new LineDashStyle(dashLength, gapLength, dashOffset, animated, animationSpeed);
    }

    public Color getStartColor() { return startColor; }
    public void setStartColor(Color v) { this.startColor = v; }
    public Color getEndColor() { return endColor; }
    public void setEndColor(Color v) { this.endColor = v; }
    public float getWidth() { return width; }
    public void setWidth(float v) { this.width = v; }
    public boolean isDashEnabled() { return dashEnabled; }
    public void setDashEnabled(boolean v) { this.dashEnabled = v; }
    public float getDashLength() { return dashLength; }
    public void setDashLength(float v) { this.dashLength = v; }
    public float getGapLength() { return gapLength; }
    public void setGapLength(float v) { this.gapLength = v; }
    public float getDashOffset() { return dashOffset; }
    public void setDashOffset(float v) { this.dashOffset = v; }
    public boolean isAnimated() { return animated; }
    public void setAnimated(boolean v) { this.animated = v; }
    public float getAnimationSpeed() { return animationSpeed; }
    public void setAnimationSpeed(float v) { this.animationSpeed = v; }
}
