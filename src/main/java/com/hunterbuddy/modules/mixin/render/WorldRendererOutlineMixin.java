package com.hunterbuddy.modules.mixin.render;

import com.hunterbuddy.modules.Shader;
import com.hunterbuddy.render.HbGlowShader;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.ShaderLoader;
import java.util.Set;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands vanilla's entity outline over to the {@link Shader} module.
 *
 * <p>Vanilla draws it in two steps: the {@code entity_outline} post effect
 * rewrites the silhouette framebuffer in place into a sobel edge, and
 * {@code drawEntityOutlinesFramebuffer} then blits that buffer over the frame.
 * Both are suppressed here, and both have to be — killing only the post effect
 * leaves the blit copying what the buffer really holds, which is flat filled
 * silhouettes.
 *
 * <p>Why replace it at all: vanilla's edge has no width control, so on anything
 * a few pixels wide — a firework rocket, a dropped item at range — it covers the
 * whole target and the shape collapses into a blob. Our pass measures distance
 * to the silhouette instead, giving a line of chosen thickness at any size.
 *
 * <p>Both injections test {@link HbGlowShader#enabled()}, the same predicate
 * that decides whether the silhouettes are captured at all. Sharing it is the
 * point: gating on the settings alone would suppress vanilla in frames where our
 * composite has nothing to draw, and an entity glowing on its own — a
 * luminescence effect, a spectral arrow, a scoreboard team — would simply lose
 * its outline. With no target of ours on screen, vanilla keeps the job.
 *
 * <p>{@code loadPostEffect} is called twice in {@code WorldRenderer}, but only
 * once inside {@code render} — the other call site sits in
 * {@code loadEntityOutlinePostProcessor} and loads the transparency effect, so
 * scoping the redirect to {@code render} is unambiguous. {@code render} itself has
 * no overload.
 */
@Mixin(WorldRenderer.class)
public class WorldRendererOutlineMixin {
    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gl/ShaderLoader;loadPostEffect(Lnet/minecraft/util/Identifier;Ljava/util/Set;)Lnet/minecraft/client/gl/PostEffectProcessor;"
        )
    )
    private @Nullable PostEffectProcessor hb$skipVanillaOutline(ShaderLoader loader, Identifier id, Set<Identifier> stages) {
        if (HbGlowShader.enabled()) return null;

        return loader.loadPostEffect(id, stages);
    }

    /**
     * Stops vanilla painting the raw silhouettes onto the screen.
     *
     * <p>This blit is the second half of vanilla's outline: the sobel post effect
     * rewrites the entity-outline framebuffer in place into a thin edge, and then
     * this copies that edge over the frame. Suppressing only the post effect
     * leaves the blit copying what the buffer actually holds — flat, fully
     * coloured silhouettes. Mobs came out painted solid and dropped items came out
     * solid white, which is simply their target colour.
     *
     * <p>Storage blocks were unaffected because they never enter vanilla's buffer;
     * they are drawn into ours, which nothing blits.
     *
     * <p>Cancelled rather than redirected so it holds whoever the caller is.
     */
    @Inject(method = "drawEntityOutlinesFramebuffer", at = @At("HEAD"), cancellable = true)
    private void hb$skipVanillaOutlineBlit(CallbackInfo ci) {
        if (HbGlowShader.enabled()) ci.cancel();
    }
}
