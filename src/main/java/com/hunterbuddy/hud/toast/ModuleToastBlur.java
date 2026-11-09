package com.hunterbuddy.hud.toast;

import com.hunterbuddy.HunterBuddyAddon;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.renderer.FixedUniformStorage;
import meteordevelopment.meteorclient.renderer.MeshRenderer;
import meteordevelopment.meteorclient.renderer.MeteorRenderPipelines;
import meteordevelopment.meteorclient.renderer.MeteorVertexFormats;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.util.Identifier;

public final class ModuleToastBlur implements AutoCloseable {
    private static final RenderPipeline MASK = RenderPipeline.builder()
        .withLocation(Identifier.of("hunterbuddy", "pipeline/toast/blur"))
        .withVertexFormat(MeteorVertexFormats.POS2, VertexFormat.DrawMode.TRIANGLES)
        .withVertexShader(Identifier.of("hunterbuddy", "shaders/toast/blur.vert"))
        .withFragmentShader(Identifier.of("hunterbuddy", "shaders/toast/blur.frag"))
        .withSampler("u_Texture")
        .withUniform("MaskData", UniformType.UNIFORM_BUFFER)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withCull(false)
        .withBlend(BlendFunction.TRANSLUCENT)
        .build();
    public static final ModuleToastBlur INSTANCE = new ModuleToastBlur();
    private final Framebuffer[] targets = new Framebuffer[3];
    private final GpuBufferSlice[] blurData = new GpuBufferSlice[3];
    private final MaskData mask = new MaskData();
    private FixedUniformStorage<DownData> downUniforms;
    private FixedUniformStorage<MaskData> maskUniforms;
    private int revision;
    private int compiled = -1;
    private int strength;
    private int loadedStrength;
    private boolean available;
    private boolean prepared;

    private ModuleToastBlur() {
    }

    public void invalidate() {
        revision++;
    }

    public void begin(int strength) {
        this.strength = strength;
        prepared = false;
    }

    public void draw(double x, double y, double width, double height, double radius, double alpha) {
        if (alpha < 0.002) return;
        if (!prepared) prepare();
        if (!available) return;
        flush();
        Framebuffer main = MeteorClient.mc.getFramebuffer();
        float sx = (float) main.textureWidth / Utils.getWindowWidth();
        float sy = (float) main.textureHeight / Utils.getWindowHeight();
        mask.x = (float) x * sx;
        mask.y = (float) y * sy;
        mask.width = (float) width * sx;
        mask.height = (float) height * sy;
        mask.screenWidth = main.textureWidth;
        mask.screenHeight = main.textureHeight;
        mask.radius = (float) radius * Math.min(sx, sy);
        mask.alpha = (float) alpha;
        MeshRenderer.begin().attachments(main).pipeline(MASK).fullscreen()
            .uniform("MaskData", maskUniforms.write(mask))
            .sampler("u_Texture", targets[0].getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR))
            .end();
    }

    private void prepare() {
        prepared = true;
        compile();
        if (!available) return;
        Framebuffer main = MeteorClient.mc.getFramebuffer();
        if (main.textureWidth < 4 || main.textureHeight < 4) {
            available = false;
            compiled = -1;
            return;
        }
        if (targets[0] == null || targets[0].textureWidth != main.textureWidth || targets[0].textureHeight != main.textureHeight) {
            close();
            for (int i = 0; i < targets.length; i++) {
                targets[i] = new SimpleFramebuffer("HunterBuddy ModuleToast Blur", Math.max(1, main.textureWidth >> i),
                    Math.max(1, main.textureHeight >> i), false);
            }
            downUniforms = new FixedUniformStorage<>("HunterBuddy ModuleToast BlurData", 16, 3);
            maskUniforms = new FixedUniformStorage<>("HunterBuddy ModuleToast MaskData", 32, 6);
            loadedStrength = 0;
        }
        if (loadedStrength != strength) {
            downUniforms.clear();
            for (int i = 0; i < targets.length; i++) blurData[i] = downUniforms.write(new DownData(
                0.5f / targets[i].textureWidth, 0.5f / targets[i].textureHeight, strength));
            loadedStrength = strength;
        }
        maskUniforms.clear();
        flush();
        pass(main, 1, MeteorRenderPipelines.BLUR_DOWN);
        pass(targets[1], 2, MeteorRenderPipelines.BLUR_DOWN);
        pass(targets[2], 1, MeteorRenderPipelines.BLUR_UP);
        pass(targets[1], 0, MeteorRenderPipelines.BLUR_UP);
    }

    private void pass(Framebuffer source, int target, RenderPipeline pipeline) {
        MeshRenderer.begin().attachments(targets[target]).pipeline(pipeline).fullscreen()
            .uniform("BlurData", blurData[target])
            .sampler("u_Texture", source.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR))
            .end();
    }

    private void compile() {
        if (compiled == revision) return;
        compiled = revision;
        try {
            available = RenderSystem.getDevice().precompilePipeline(MASK, (id, type) -> {
                try (var in = MeteorClient.mc.getResourceManager().getResourceOrThrow(id).getInputStream()) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            }).isValid();
        } catch (RuntimeException exception) {
            available = false;
            HunterBuddyAddon.LOG.error("ModuleToast blur shader failed to load", exception);
        }
        if (!available) HunterBuddyAddon.LOG.warn("ModuleToast background blur is unavailable.");
    }

    private static void flush() {
        Renderer2D.COLOR.render();
        Renderer2D.COLOR.begin();
    }

    @Override
    public void close() {
        for (int i = 0; i < targets.length; i++) {
            if (targets[i] != null) targets[i].delete();
            targets[i] = null;
            blurData[i] = null;
        }
        if (downUniforms != null) downUniforms.close();
        if (maskUniforms != null) maskUniforms.close();
        downUniforms = null;
        maskUniforms = null;
    }

    private record DownData(float x, float y, float offset) implements DynamicUniformStorage.Uploadable {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer).putVec2(x, y).putFloat(offset);
        }
    }

    private static final class MaskData implements DynamicUniformStorage.Uploadable {
        float x;
        float y;
        float width;
        float height;
        float screenWidth;
        float screenHeight;
        float radius;
        float alpha;

        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer).putVec4(x, y, width, height).putVec4(screenWidth, screenHeight, radius, alpha);
        }
    }
}
