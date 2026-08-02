package com.hunterbuddy.render;

import static meteordevelopment.meteorclient.MeteorClient.mc;

import com.hunterbuddy.modules.Shader;
import meteordevelopment.meteorclient.mixininterface.IWorldRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gl.Framebuffer;

/**
 * Makes the first-person hand (held item + arm) participate in the vanilla
 * entity-outline pass, so the {@link Shader} module's outline and glow cover it
 * like Future 3.0 does.
 *
 * <p>Vanilla already knows how to do this: every render command carries an
 * {@code outlineColor}, and {@code ItemCommandRenderer} /
 * {@code ModelCommandRenderer} re-render the command into the shared
 * {@code OutlineVertexConsumerProvider} whenever that colour is non-zero. The
 * hand simply always submits {@code 0}. So we:
 * <ol>
 *   <li>flag the window in which {@code GameRenderer.renderHand} submits,</li>
 *   <li>rewrite the outline colour of every command submitted inside it,</li>
 *   <li>flush the outline provider right after the hand is dispatched, before
 *       vanilla blits the outline framebuffer onto the screen.</li>
 * </ol>
 * Both the vanilla 1px outline and {@link HbGlowShader} then pick the hand up
 * for free, because both read the same framebuffer.
 */
public final class HandGlowState {
    private static boolean submitting;
    private static boolean pending;
    private static int color;

    private HandGlowState() {}

    public static void begin() {
        Shader shader = Modules.get().get(Shader.class);
        if (shader == null || !shader.isActive() || !shader.handGlowActive()) return;

        submitting = true;
        color = shader.handGlowColor();
    }

    public static void end() {
        submitting = false;
    }

    /** Rewrites an {@code outlineColor} argument while the hand is being submitted. */
    public static int outlineColor(int original) {
        if (!submitting) return original;

        pending = true;
        return color;
    }

    /**
     * Flushes the hand's outline geometry into {@link HbGlowShader}'s capture
     * buffer.
     *
     * <p>It cannot go into vanilla's entity-outline framebuffer: by this point
     * the {@code entity_outline} post effect has already turned that buffer into
     * a thin outline, so a late silhouette would be blitted to the screen as a
     * solid blob. Redirecting the outline render target — Meteor's
     * {@code IWorldRenderer} push/pop — keeps it in our pass only, so the hand
     * gets the glow and the fill but not the vanilla 1px outline.
     */
    public static void flush() {
        if (!pending) return;
        pending = false;

        if (mc.worldRenderer == null || mc.getBufferBuilders() == null) return;

        Framebuffer target = HbGlowShader.captureTarget();
        if (target == null) return;

        IWorldRenderer worldRenderer = (IWorldRenderer) mc.worldRenderer;
        worldRenderer.meteor$pushEntityOutlineFramebuffer(target);

        try {
            mc.getBufferBuilders().getOutlineVertexConsumers().draw();
        } finally {
            worldRenderer.meteor$popEntityOutlineFramebuffer();
        }
    }
}
