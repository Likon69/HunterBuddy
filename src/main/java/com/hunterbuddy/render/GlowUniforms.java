package com.hunterbuddy.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import java.nio.ByteBuffer;
import net.minecraft.client.gl.DynamicUniformStorage;

/**
 * Uniform buffers for the glow post-process passes.
 *
 * <p>{@link #flipFrame()} must be called exactly once per frame, otherwise the
 * ring buffers keep growing.
 */
public final class GlowUniforms {
    private GlowUniforms() {}

    // PostData — shared by both passes (consumed by base.vert).

    private static final int POST_SIZE = new Std140SizeCalculator()
        .putVec2()
        .putFloat()
        .get();

    private static final DynamicUniformStorage<PostData> POST =
        new DynamicUniformStorage<>("HunterBuddy - Glow Post UBO", POST_SIZE, 8);

    // BlurData — horizontal pass.

    private static final int BLUR_SIZE = new Std140SizeCalculator()
        .putVec2()
        .putFloat()
        .putInt()
        .get();

    private static final DynamicUniformStorage<BlurData> BLUR =
        new DynamicUniformStorage<>("HunterBuddy - Glow Blur UBO", BLUR_SIZE, 8);

    // GlowData — vertical pass + compositing.

    private static final int GLOW_SIZE = new Std140SizeCalculator()
        .putVec2()
        .putFloat()
        .putInt()
        .putFloat()
        .putFloat()
        .putInt()
        .get();

    private static final DynamicUniformStorage<GlowData> GLOW =
        new DynamicUniformStorage<>("HunterBuddy - Glow Composite UBO", GLOW_SIZE, 8);

    public static void flipFrame() {
        POST.clear();
        BLUR.clear();
        GLOW.clear();
    }

    public static GpuBufferSlice post(float sizeX, float sizeY, float time) {
        return POST.write(new PostData(sizeX, sizeY, time));
    }

    public static GpuBufferSlice blur(float dirX, float dirY, float radius, int samples) {
        return BLUR.write(new BlurData(dirX, dirY, radius, samples));
    }

    public static GpuBufferSlice glow(float dirX, float dirY, float radius, int samples,
                                      float intensity, float fillOpacity, int flags) {
        return GLOW.write(new GlowData(dirX, dirY, radius, samples, intensity, fillOpacity, flags));
    }

    private record PostData(float sizeX, float sizeY, float time) implements DynamicUniformStorage.Uploadable {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                .putVec2(sizeX, sizeY)
                .putFloat(time);
        }
    }

    private record BlurData(float dirX, float dirY, float radius, int samples) implements DynamicUniformStorage.Uploadable {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                .putVec2(dirX, dirY)
                .putFloat(radius)
                .putInt(samples);
        }
    }

    private record GlowData(float dirX, float dirY, float radius, int samples,
                            float intensity, float fillOpacity, int flags) implements DynamicUniformStorage.Uploadable {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                .putVec2(dirX, dirY)
                .putFloat(radius)
                .putInt(samples)
                .putFloat(intensity)
                .putFloat(fillOpacity)
                .putInt(flags);
        }
    }
}
