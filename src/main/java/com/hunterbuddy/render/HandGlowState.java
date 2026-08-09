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
 *   <li>redirect the outline render target to {@link HbGlowShader}'s capture for
 *       the whole window in which {@code GameRenderer.renderHand} submits,</li>
 *   <li>rewrite the outline colour of every command submitted inside it,</li>
 *   <li>drain the outline provider once the hand has been dispatched, still
 *       inside that window.</li>
 * </ol>
 * {@link HbGlowShader} then picks the hand up with everything else. Vanilla's own
 * outline is not involved: {@code WorldRendererOutlineMixin} suppresses both its
 * sobel pass and its blit while the module is compositing.
 */
public final class HandGlowState {
    private static boolean submitting;
    private static int color;

    /** The renderer we redirected, kept so the pop cannot land on another one. */
    private static IWorldRenderer pushedRenderer;

    private HandGlowState() {}

    public static void begin() {
        // Self-heal: renderItem throwing, or a third-party mod cancelling it,
        // skips the AFTER injection and leaves two states orphaned. Both are
        // cleared here so a single broken frame cannot poison every frame after
        // it.
        //
        // The stuck flag is the worse of the two: end() lives in that same
        // skipped injection, and while it stays armed the queue mixin rewrites
        // the outline colour of everything submitted, not just the hand. The
        // world pass runs before renderHand, so the next frame would paint every
        // mob, player and block entity in the hand's colour.
        submitting = false;

        if (pushedRenderer != null) {
            IWorldRenderer stale = pushedRenderer;
            pushedRenderer = null;
            stale.meteor$popEntityOutlineFramebuffer();
        }

        Shader shader = Modules.get().get(Shader.class);
        if (shader == null || !shader.isActive() || !shader.handGlowActive()) return;

        // Only arm the rewrite when there is somewhere to send the result.
        // OutlineVertexConsumerProvider is shared with the world pass: colouring
        // commands we then fail to drain leaves the hand geometry sitting in it,
        // and next frame's WorldRenderer flush draws it into vanilla's outline
        // framebuffer under the world's matrices and FOV — a frame-late, offset
        // copy of the hand.
        Framebuffer target = HbGlowShader.captureTarget();
        if (target == null || mc.worldRenderer == null) return;

        // Redirect for the WHOLE hand window, not just the final drain.
        //
        // RenderLayer.draw resolves the outline target through the frame graph
        // handle, which is stale by the time renderHand runs, and falls back to
        // the main framebuffer. That matters because the vertex consumer provider
        // draws on layer switch: the moment a second outline layer appears during
        // renderItem's own dispatch, the first one is flushed there and then. A
        // chest, a shulker, any block-entity item uses a different layer from the
        // arm, so its silhouette was drawn straight to the screen — a flat
        // block of the hand colour, with no halo, because our capture never saw
        // it. Items that stay on one layer, an apple or a firework, waited for the
        // drain and looked right, which is exactly the split observed in game.
        ((IWorldRenderer) mc.worldRenderer).meteor$pushEntityOutlineFramebuffer(target);
        pushedRenderer = (IWorldRenderer) mc.worldRenderer;

        submitting = true;
        color = shader.handGlowColor();
    }

    public static void end() {
        submitting = false;
    }

    /** Rewrites an {@code outlineColor} argument while the hand is being submitted. */
    public static int outlineColor(int original) {
        return submitting ? color : original;
    }

    /**
     * Flushes the hand's outline geometry into {@link HbGlowShader}'s capture
     * buffer.
     *
     * <p>It cannot go into vanilla's entity-outline framebuffer: the world pass
     * finished with that buffer long ago, and anything landing in it now would
     * either be ignored or blitted to the screen as a flat blob. The redirect
     * opened in {@link #begin()} keeps every outline draw of the hand in our own
     * capture instead.
     *
     * <p>Must be called from inside {@code renderHand}'s model-view push:
     * {@link net.minecraft.client.render.RenderLayer#draw} reads the model-view
     * at flush time, and the hand's vertices are baked against the camera
     * rotation that is only on the stack there.
     */
    public static void flush() {
        if (pushedRenderer == null) return;

        try {
            // Drained whenever the window was opened, not only when we recoloured
            // something. Anything left in the shared provider would otherwise be
            // picked up by the next world pass, and an empty draw costs nothing.
            if (mc.getBufferBuilders() != null) {
                mc.getBufferBuilders().getOutlineVertexConsumers().draw();
            }
        } finally {
            // Closed here rather than around the drain alone, so the redirect
            // spans every draw renderItem triggers on its own. Guarded by the
            // stored renderer: a world swap between begin and flush must not pop
            // a stack we never pushed.
            IWorldRenderer renderer = pushedRenderer;
            pushedRenderer = null;
            renderer.meteor$popEntityOutlineFramebuffer();
        }
    }
}
