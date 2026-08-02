package com.hunterbuddy.render;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static org.lwjgl.glfw.GLFW.glfwGetTime;

import com.hunterbuddy.modules.Shader;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.MeshRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.jetbrains.annotations.Nullable;

/**
 * Glow post-process for the {@link Shader} module.
 *
 * <p>Instead of re-rendering every target into a private framebuffer (what
 * Meteor's {@code EntityShader} does), this reuses the vanilla glowing pipeline
 * the module already drives through {@code EntityGlowMixin} /
 * {@code EntityTeamColorMixin}: vanilla's entity-outline framebuffer already
 * holds flat, team-coloured silhouettes of exactly our targets.
 *
 * <p>That framebuffer cannot be read at HUD time though — vanilla's
 * {@code entity_outline} post effect overwrites it in place with the thin
 * sobel outline. So {@link #captureFrom(Framebuffer)} copies it mid-frame, just
 * after the outline vertices are flushed, into a buffer we own.
 *
 * <p>Frame order:
 * <ol>
 *   <li>{@code WorldRendererGlowMixin} -> {@link #captureFrom(Framebuffer)}</li>
 *   <li>{@code GameRendererHandGlowMixin} -> {@link HandGlowState#flush()},
 *       which draws the first-person hand into the same buffer</li>
 *   <li>{@link #onRender2D} -> separable gaussian + composite onto the screen</li>
 * </ol>
 */
public final class HbGlowShader {
    /** Copy of the silhouettes, plus whatever we add to it (the hand). */
    private static Framebuffer capture;

    /** Ping-pong target for the horizontal half of the blur. */
    private static Framebuffer scratch;

    private static boolean hasContent;

    private HbGlowShader() {}

    private static Shader module() {
        return Modules.get().get(Shader.class);
    }

    public static boolean enabled() {
        Shader shader = module();
        return shader != null && shader.isActive() && shader.postProcessEnabled() && shader.hasPostProcessTargets();
    }

    /**
     * Snapshots vanilla's entity-outline framebuffer. Depth is reset so anything
     * we draw on top afterwards (the hand) is not occluded by world geometry.
     */
    public static void captureFrom(@Nullable Framebuffer source) {
        if (source == null || !enabled()) return;

        int width = mc.getWindow().getFramebufferWidth();
        int height = mc.getWindow().getFramebufferHeight();
        if (width <= 0 || height <= 0) return;

        ensureBuffers(width, height);

        int copyWidth = Math.min(source.textureWidth, capture.textureWidth);
        int copyHeight = Math.min(source.textureHeight, capture.textureHeight);
        if (copyWidth <= 0 || copyHeight <= 0) return;

        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(
            source.getColorAttachment(), capture.getColorAttachment(),
            0, 0, 0, 0, 0, copyWidth, copyHeight
        );
        encoder.clearDepthTexture(capture.getDepthAttachment(), 1.0);

        hasContent = true;
    }

    /**
     * The buffer extra silhouettes should be drawn into, or {@code null} when
     * nothing has been captured this frame.
     */
    public static @Nullable Framebuffer captureTarget() {
        return hasContent ? capture : null;
    }

    @EventHandler
    private static void onRender2D(Render2DEvent event) {
        // Must happen exactly once per frame, whether or not we draw.
        GlowUniforms.flipFrame();

        try {
            render();
        } finally {
            hasContent = false;
        }
    }

    private static void render() {
        if (!hasContent || !enabled()) return;

        Shader shader = module();
        int width = capture.textureWidth;
        int height = capture.textureHeight;

        float time = (float) glfwGetTime();
        float radius = shader.glowEnabled() ? (float) shader.glowWidth() : 0.0f;
        int samples = shader.glowSamples();

        int flags = 0;
        if (shader.glowEnabled()) flags |= 1;
        if (shader.filled()) flags |= 2;

        var linear = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
        var nearest = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);

        // Pass 1 — horizontal gaussian into the scratch buffer.
        MeshRenderer.begin()
            .attachments(scratch.getColorAttachmentView(), null)
            .pipeline(HbRenderPipelines.GLOW_BLUR)
            .fullscreen()
            .uniform("PostData", GlowUniforms.post(width, height, time))
            .uniform("BlurData", GlowUniforms.blur(1.0f, 0.0f, radius, samples))
            .sampler("u_Texture", capture.getColorAttachmentView(), linear)
            .end();

        // Pass 2 — vertical gaussian + fill, straight onto the main framebuffer.
        MeshRenderer.begin()
            .attachments(mc.getFramebuffer())
            .pipeline(HbRenderPipelines.GLOW_COMPOSITE)
            .fullscreen()
            .uniform("PostData", GlowUniforms.post(width, height, time))
            .uniform("GlowData", GlowUniforms.glow(
                0.0f, 1.0f, radius, samples,
                (float) shader.glowIntensity(), (float) shader.fillOpacity(), flags
            ))
            .sampler("u_Texture", scratch.getColorAttachmentView(), linear)
            .sampler("u_Origin", capture.getColorAttachmentView(), nearest)
            .end();
    }

    private static void ensureBuffers(int width, int height) {
        if (capture == null) {
            // Depth is needed so the hand's outline geometry has a depth
            // attachment to render against; the blur scratch never does.
            capture = new SimpleFramebuffer("HunterBuddy Glow Capture", width, height, true);
            scratch = new SimpleFramebuffer("HunterBuddy Glow Blur", width, height, false);
            return;
        }

        if (capture.textureWidth != width || capture.textureHeight != height) {
            capture.resize(width, height);
            scratch.resize(width, height);
        }
    }
}
