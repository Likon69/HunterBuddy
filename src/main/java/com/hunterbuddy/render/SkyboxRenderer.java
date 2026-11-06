package com.hunterbuddy.render;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static org.lwjgl.glfw.GLFW.glfwGetTime;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.Skybox;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import meteordevelopment.meteorclient.renderer.MeshRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;

public final class SkyboxRenderer {
    private static final float TICKS_PER_DAY = 24000.0f;
    private static final float NO_TIME_OF_DAY = -1.0f;
    private static final float COLOR_MAX = 255.0f;

    private static boolean available;

    private SkyboxRenderer() {}

    public static void setAvailable(boolean value) {
        available = value;
    }

    public static boolean replacesOverworld() {
        Skybox module = module();
        return available && module != null && module.isActive();
    }

    public static boolean replacesEnd() {
        return replacesOverworld() && module().endSky();
    }

    public static boolean hidesCelestialBodies() {
        return replacesOverworld() && !module().keepCelestial();
    }

    public static boolean hidesClouds() {
        return replacesOverworld() && !module().keepClouds();
    }

    public static boolean draw(boolean end) {
        try {
            GpuBufferSlice sky = SkyboxUniforms.sky(data(module(), end));
            MeshRenderer.begin()
                .attachments(mc.getFramebuffer())
                .pipeline(HbRenderPipelines.SKYBOX)
                .fullscreen()
                .uniform("SkyData", sky)
                .end();
            return true;
        } catch (RuntimeException e) {
            available = false;
            HunterBuddyAddon.LOG.error("Skybox draw failed, the vanilla sky is used instead", e);
            return false;
        }
    }

    private static Skybox module() {
        return Modules.get().get(Skybox.class);
    }

    private static SkyboxUniforms.SkyData data(Skybox module, boolean end) {
        SkyboxStyle style = module.style(end);
        SettingColor[] palette = module.palette(style);
        Vector4f fog = SkyboxState.fog();

        return new SkyboxUniforms.SkyData(
            SkyboxState.inverseViewProjection(),
            new Vector4f((float) glfwGetTime(), (float) module.speed(), (float) module.intensity(), style.shaderId()),
            color(palette[0]),
            color(palette[1]),
            color(palette[2]),
            new Vector4f(timeOfDay(module, end), rain(module, end), (float) module.starDensity(), end ? 1.0f : 0.0f),
            new Vector4f(fog.x, fog.y, fog.z, (float) module.horizonFogBlend())
        );
    }

    private static Vector4f color(SettingColor color) {
        return new Vector4f(color.r / COLOR_MAX, color.g / COLOR_MAX, color.b / COLOR_MAX, 1.0f);
    }

    private static float timeOfDay(Skybox module, boolean end) {
        ClientWorld world = mc.world;
        if (end || world == null || !module.followWorldTime()) return NO_TIME_OF_DAY;
        return (world.getTimeOfDay() % (long) TICKS_PER_DAY) / TICKS_PER_DAY;
    }

    private static float rain(Skybox module, boolean end) {
        ClientWorld world = mc.world;
        if (end || world == null || !module.rainDims()) return 0.0f;
        return world.getRainGradient(mc.getRenderTickCounter().getTickProgress(false));
    }
}
