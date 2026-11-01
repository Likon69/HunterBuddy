package com.hunterbuddy.render;

import static meteordevelopment.meteorclient.MeteorClient.mc;

import com.hunterbuddy.modules.SpeedBlur;
import com.hunterbuddy.modules.mixin.accessors.GameRendererFovAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import meteordevelopment.meteorclient.renderer.MeshRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2d;
import org.joml.Vector3f;

public final class SpeedBlurPass {
    private static final double IDLE_SPEED = 0.6;
    private static final double FULL_SPEED = 3.0;
    private static final double TELEPORT_SPEED = 12.0;
    private static final double DIRECTION_MIN_SPEED = 0.1;
    private static final double RISE_TAU = 0.06;
    private static final double FALL_TAU = 0.35;
    private static final double DIRECTION_TAU = 0.04;
    private static final double PAN_TAU = 0.03;
    private static final double SHUTTER_SECONDS = 1.0 / 60.0;
    private static final double MIN_FRAME_SECONDS = 1.0e-4;
    private static final double MAX_FRAME_SECONDS = 0.1;
    private static final double MIN_DEPTH = 0.02;
    private static final double MAX_FOCUS_NDC = 60.0;
    private static final double MIN_STRENGTH = 0.005;
    private static final double MIN_PAN = 1.0e-4;
    private static final double LINE_CAP = 0.04;
    private static final double SUBJECT_HALF_WIDTH = 1.25;
    private static final double TICKS_PER_SECOND = 20.0;

    private static Framebuffer scene;
    private static Vec3d previousPosition;
    private static float previousYaw;
    private static float previousPitch;
    private static Vec3d direction;
    private static double strength;
    private static double panX;
    private static double panY;
    private static long lastFrameNanos;

    private SpeedBlurPass() {}

    public static void apply() {
        SpeedBlur module = Modules.get().get(SpeedBlur.class);
        ClientPlayerEntity player = mc.player;
        if (module == null || !module.isActive() || player == null || mc.world == null) {
            reset();
            return;
        }

        Camera camera = mc.gameRenderer.getCamera();
        long now = System.nanoTime();
        double dt = (now - lastFrameNanos) / 1.0e9;
        boolean first = lastFrameNanos == 0L || previousPosition == null;
        lastFrameNanos = now;

        Vec3d position = camera.getCameraPos();
        float yaw = camera.getYaw();
        float pitch = camera.getPitch();

        if (first || dt < MIN_FRAME_SECONDS) {
            previousPosition = position;
            previousYaw = yaw;
            previousPitch = pitch;
            return;
        }

        dt = Math.min(dt, MAX_FRAME_SECONDS);

        float fov = ((GameRendererFovAccessor) mc.gameRenderer).hb$getFov(camera, camera.getLastTickProgress(), true);
        double tanHalf = Math.tan(Math.toRadians(fov) * 0.5);
        double aspect = (double) mc.getWindow().getFramebufferWidth() / mc.getWindow().getFramebufferHeight();

        trackTranslation(position, dt, module.strength());
        trackRotation(yaw, pitch, dt, tanHalf, aspect, module.strength());

        previousPosition = position;
        previousYaw = yaw;
        previousPitch = pitch;

        boolean streaking = strength >= MIN_STRENGTH && direction != null;
        boolean panning = Math.hypot(panX, panY) > MIN_PAN;
        if (!streaking && !panning) return;

        Vector2d focus = streaking ? focus(camera, tanHalf, aspect) : new Vector2d(0.5, 0.5);
        double[] subject = subject(camera, player, tanHalf, aspect);

        draw(new SpeedBlurUniforms.BlurData(
            (float) focus.x, (float) focus.y,
            (float) panX, (float) panY,
            (float) subject[0], (float) subject[1], (float) subject[2],
            (float) (LINE_CAP * module.strength()),
            streaking ? (float) strength : 0.0f
        ));
    }

    private static void trackTranslation(Vec3d position, double dt, double setting) {
        Vec3d moved = position.subtract(previousPosition);
        double speed = moved.length() / dt / TICKS_PER_SECOND;

        if (speed > TELEPORT_SPEED) {
            strength = 0.0;
            direction = null;
            return;
        }

        double target = MathHelper.clamp((speed - IDLE_SPEED) / (FULL_SPEED - IDLE_SPEED), 0.0, 1.0) * setting;
        double tau = target > strength ? RISE_TAU : FALL_TAU;
        strength += (target - strength) * DroneEasing.follow(dt, tau);

        if (speed < DIRECTION_MIN_SPEED) return;

        Vec3d unit = moved.normalize();
        if (direction == null) {
            direction = unit;
            return;
        }

        Vec3d blended = direction.lerp(unit, DroneEasing.follow(dt, DIRECTION_TAU));
        direction = blended.lengthSquared() < 1.0e-6 ? unit : blended.normalize();
    }

    private static void trackRotation(float yaw, float pitch, double dt, double tanHalf, double aspect, double setting) {
        double yawRate = Math.toRadians(MathHelper.wrapDegrees(yaw - previousYaw)) / dt;
        double pitchRate = Math.toRadians(pitch - previousPitch) / dt;

        double targetX = -yawRate / (tanHalf * aspect) * 0.5 * SHUTTER_SECONDS * setting;
        double targetY = pitchRate / tanHalf * 0.5 * SHUTTER_SECONDS * setting;

        double follow = DroneEasing.follow(dt, PAN_TAU);
        panX += (targetX - panX) * follow;
        panY += (targetY - panY) * follow;
    }

    private static Vector2d focus(Camera camera, double tanHalf, double aspect) {
        Vector3f view = new Vector3f((float) direction.x, (float) direction.y, (float) direction.z);
        camera.getRotation().transformInverse(view);
        if (view.z > 0.0f) view.negate();

        double depth = Math.max(-view.z, MIN_DEPTH);
        double ndcX = MathHelper.clamp(view.x / depth / (tanHalf * aspect), -MAX_FOCUS_NDC, MAX_FOCUS_NDC);
        double ndcY = MathHelper.clamp(view.y / depth / tanHalf, -MAX_FOCUS_NDC, MAX_FOCUS_NDC);

        return new Vector2d(ndcX * 0.5 + 0.5, ndcY * 0.5 + 0.5);
    }

    private static double[] subject(Camera camera, ClientPlayerEntity player, double tanHalf, double aspect) {
        double[] none = {0.0, 0.0, 0.0};
        if (!camera.isThirdPerson()) return none;

        Vec3d centre = player.getLerpedPos(camera.getLastTickProgress()).add(0.0, player.getHeight() * 0.5, 0.0);
        Vec3d offset = centre.subtract(camera.getCameraPos());
        Vector3f view = new Vector3f((float) offset.x, (float) offset.y, (float) offset.z);
        camera.getRotation().transformInverse(view);

        double depth = -view.z;
        if (depth < MIN_DEPTH) return none;

        double ndcX = view.x / depth / (tanHalf * aspect);
        double ndcY = view.y / depth / tanHalf;
        double radius = SUBJECT_HALF_WIDTH / (2.0 * tanHalf * depth);

        return new double[] {ndcX * 0.5 + 0.5, ndcY * 0.5 + 0.5, radius};
    }

    private static void draw(SpeedBlurUniforms.BlurData data) {
        Framebuffer main = mc.getFramebuffer();
        ensureScene(main.textureWidth, main.textureHeight);

        int width = Math.min(main.textureWidth, scene.textureWidth);
        int height = Math.min(main.textureHeight, scene.textureHeight);
        if (width <= 0 || height <= 0) return;

        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
            main.getColorAttachment(), scene.getColorAttachment(), 0, 0, 0, 0, 0, width, height
        );

        SpeedBlurUniforms.flipFrame();

        MeshRenderer.begin()
            .attachments(main)
            .pipeline(HbRenderPipelines.SPEED_BLUR)
            .fullscreen()
            .uniform("PostData", SpeedBlurUniforms.post(main.textureWidth, main.textureHeight))
            .uniform("BlurData", SpeedBlurUniforms.blur(data))
            .sampler("u_Scene", scene.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR))
            .end();
    }

    private static void ensureScene(int width, int height) {
        if (scene == null) {
            scene = new SimpleFramebuffer("HunterBuddy Speed Blur Scene", width, height, false);
        } else if (scene.textureWidth != width || scene.textureHeight != height) {
            scene.resize(width, height);
        }
    }

    private static void reset() {
        strength = 0.0;
        panX = 0.0;
        panY = 0.0;
        direction = null;
        previousPosition = null;
        lastFrameNanos = 0L;
    }
}
