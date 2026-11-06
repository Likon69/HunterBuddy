package com.hunterbuddy.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import java.nio.ByteBuffer;
import net.minecraft.client.gl.DynamicUniformStorage;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class SkyboxUniforms {
    private static final int SKY_SIZE = new Std140SizeCalculator()
        .putMat4f()
        .putVec4()
        .putVec4()
        .putVec4()
        .putVec4()
        .putVec4()
        .putVec4()
        .get();

    private static final DynamicUniformStorage<SkyData> SKY =
        new DynamicUniformStorage<>("HunterBuddy - Skybox UBO", SKY_SIZE, 2);

    private SkyboxUniforms() {}

    public static void flipFrame() {
        SKY.clear();
    }

    public static GpuBufferSlice sky(SkyData data) {
        return SKY.write(data);
    }

    public record SkyData(Matrix4f inverseViewProjection, Vector4f params,
                          Vector4f color0, Vector4f color1, Vector4f color2,
                          Vector4f misc, Vector4f fog) implements DynamicUniformStorage.Uploadable {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                .putMat4f(inverseViewProjection)
                .putVec4(params)
                .putVec4(color0)
                .putVec4(color1)
                .putVec4(color2)
                .putVec4(misc)
                .putVec4(fog);
        }
    }
}
