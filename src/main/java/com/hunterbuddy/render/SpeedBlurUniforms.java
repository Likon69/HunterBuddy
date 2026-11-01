package com.hunterbuddy.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import java.nio.ByteBuffer;
import net.minecraft.client.gl.DynamicUniformStorage;

public final class SpeedBlurUniforms {
    private static final int POST_SIZE = new Std140SizeCalculator()
        .putVec2()
        .putFloat()
        .get();

    private static final int BLUR_SIZE = new Std140SizeCalculator()
        .putVec4()
        .putVec4()
        .putVec4()
        .get();

    private static final DynamicUniformStorage<PostData> POST =
        new DynamicUniformStorage<>("HunterBuddy - Speed Blur Post UBO", POST_SIZE, 2);

    private static final DynamicUniformStorage<BlurData> BLUR =
        new DynamicUniformStorage<>("HunterBuddy - Speed Blur UBO", BLUR_SIZE, 2);

    private SpeedBlurUniforms() {}

    public static void flipFrame() {
        POST.clear();
        BLUR.clear();
    }

    public static GpuBufferSlice post(float sizeX, float sizeY) {
        return POST.write(new PostData(sizeX, sizeY));
    }

    public static GpuBufferSlice blur(BlurData data) {
        return BLUR.write(data);
    }

    private record PostData(float sizeX, float sizeY) implements DynamicUniformStorage.Uploadable {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                .putVec2(sizeX, sizeY)
                .putFloat(0.0f);
        }
    }

    public record BlurData(float focusU, float focusV, float panU, float panV,
                           float subjectU, float subjectV, float subjectRadius, float cap,
                           float strength) implements DynamicUniformStorage.Uploadable {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                .putVec4(focusU, focusV, panU, panV)
                .putVec4(subjectU, subjectV, subjectRadius, cap)
                .putVec4(strength, 0.0f, 0.0f, 0.0f);
        }
    }
}
