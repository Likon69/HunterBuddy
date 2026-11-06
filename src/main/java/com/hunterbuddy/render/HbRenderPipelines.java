package com.hunterbuddy.render;

import com.hunterbuddy.HunterBuddyAddon;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.renderer.MeteorVertexFormats;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.UniformType;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.apache.commons.io.IOUtils;

/**
 * Render pipelines owned by HunterBuddy. Mirrors the structure of Meteor's
 * {@code MeteorRenderPipelines} — the shader sources live under
 * {@code assets/hunterbuddy/shaders/...} and are precompiled from
 * {@link com.hunterbuddy.modules.mixin.render.ShaderLoaderGlowMixin} whenever
 * resources are (re)loaded.
 */
public final class HbRenderPipelines {
    private static final List<RenderPipeline> PIPELINES = new ArrayList<>();

    /** Horizontal half of the separable dilation: silhouette -> scratch buffer. */
    public static final RenderPipeline GLOW_MASK = add(RenderPipeline.builder()
        .withLocation(id("pipeline/post/glow_mask"))
        .withVertexFormat(MeteorVertexFormats.POS2, VertexFormat.DrawMode.TRIANGLES)
        .withVertexShader(id("shaders/post/base.vert"))
        .withFragmentShader(id("shaders/post/glow_mask.frag"))
        .withSampler("u_Texture")
        .withUniform("PostData", UniformType.UNIFORM_BUFFER)
        .withUniform("MaskData", UniformType.UNIFORM_BUFFER)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withCull(false)
        .build());

    /** Distance-field outline and glow, composited onto the main framebuffer. */
    public static final RenderPipeline GLOW_COMPOSITE = add(RenderPipeline.builder()
        .withLocation(id("pipeline/post/glow_composite"))
        .withVertexFormat(MeteorVertexFormats.POS2, VertexFormat.DrawMode.TRIANGLES)
        .withVertexShader(id("shaders/post/base.vert"))
        .withFragmentShader(id("shaders/post/glow_composite.frag"))
        .withSampler("u_Origin")
        .withSampler("u_Mask")
        .withUniform("PostData", UniformType.UNIFORM_BUFFER)
        .withUniform("GlowData", UniformType.UNIFORM_BUFFER)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withBlend(BlendFunction.TRANSLUCENT)
        .withCull(false)
        .build());

    public static final RenderPipeline SPEED_BLUR = add(RenderPipeline.builder()
        .withLocation(id("pipeline/post/speed_blur"))
        .withVertexFormat(MeteorVertexFormats.POS2, VertexFormat.DrawMode.TRIANGLES)
        .withVertexShader(id("shaders/post/base.vert"))
        .withFragmentShader(id("shaders/post/speed_blur.frag"))
        .withSampler("u_Scene")
        .withUniform("PostData", UniformType.UNIFORM_BUFFER)
        .withUniform("BlurData", UniformType.UNIFORM_BUFFER)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withCull(false)
        .build());

    public static final RenderPipeline SKYBOX = RenderPipeline.builder()
        .withLocation(id("pipeline/skybox"))
        .withVertexFormat(MeteorVertexFormats.POS2, VertexFormat.DrawMode.TRIANGLES)
        .withVertexShader(id("shaders/skybox/skybox.vert"))
        .withFragmentShader(id("shaders/skybox/skybox.frag"))
        .withUniform("SkyData", UniformType.UNIFORM_BUFFER)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withCull(false)
        .build();

    private HbRenderPipelines() {}

    public static Identifier id(String path) {
        return Identifier.of("hunterbuddy", path);
    }

    private static RenderPipeline add(RenderPipeline pipeline) {
        PIPELINES.add(pipeline);
        return pipeline;
    }

    public static void precompile() {
        for (RenderPipeline pipeline : PIPELINES) {
            compile(pipeline);
        }

        precompileSkybox();
    }

    private static void precompileSkybox() {
        boolean valid = false;

        try {
            valid = compile(SKYBOX).isValid();
        } catch (RuntimeException e) {
            HunterBuddyAddon.LOG.error("Skybox shader failed to load", e);
        }

        if (!valid) HunterBuddyAddon.LOG.error("Skybox shader is unavailable, the vanilla sky is used instead");
        SkyboxRenderer.setAvailable(valid);
    }

    private static CompiledRenderPipeline compile(RenderPipeline pipeline) {
        GpuDevice device = RenderSystem.getDevice();
        ResourceManager resources = MinecraftClient.getInstance().getResourceManager();

        return device.precompilePipeline(pipeline, (identifier, shaderType) -> {
            var resource = resources.getResource(identifier).orElseThrow();

            try (var in = resource.getInputStream()) {
                return IOUtils.toString(in, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
