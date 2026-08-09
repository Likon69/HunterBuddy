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

    // MaskData — horizontal dilation pass.

    private static final int MASK_SIZE = new Std140SizeCalculator()
        .putFloat()
        .get();

    private static final DynamicUniformStorage<MaskData> MASK =
        new DynamicUniformStorage<>("HunterBuddy - Glow Mask UBO", MASK_SIZE, 8);

    // GlowData — distance-field outline, glow and fill.

    private static final int GLOW_SIZE = new Std140SizeCalculator()
        .putFloat()
        .putFloat()
        .putFloat()
        .putFloat()
        .putInt()
        .putInt()
        .get();

    private static final DynamicUniformStorage<GlowData> GLOW =
        new DynamicUniformStorage<>("HunterBuddy - Glow Composite UBO", GLOW_SIZE, 8);

    public static void flipFrame() {
        POST.clear();
        MASK.clear();
        GLOW.clear();
    }

    public static GpuBufferSlice post(float sizeX, float sizeY, float time) {
        return POST.write(new PostData(sizeX, sizeY, time));
    }

    public static GpuBufferSlice mask(float radius) {
        return MASK.write(new MaskData(radius));
    }

    public static GpuBufferSlice glow(float thickness, float radius, float intensity,
                                      float fillOpacity, int quality, int flags) {
        return GLOW.write(new GlowData(thickness, radius, intensity, fillOpacity, quality, flags));
    }

    private record PostData(float sizeX, float sizeY, float time) implements DynamicUniformStorage.Uploadable {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                .putVec2(sizeX, sizeY)
                .putFloat(time);
        }
    }

    private record MaskData(float radius) implements DynamicUniformStorage.Uploadable {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                .putFloat(radius);
        }
    }

    private record GlowData(float thickness, float radius, float intensity,
                            float fillOpacity, int quality, int flags) implements DynamicUniformStorage.Uploadable {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                .putFloat(thickness)
                .putFloat(radius)
                .putFloat(intensity)
                .putFloat(fillOpacity)
                .putInt(quality)
                .putInt(flags);
        }
    }
}
