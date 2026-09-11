package com.hunterbuddy.modules.mixin.render;

import com.hunterbuddy.modules.mixin.accessors.ElytraEntityModelAccessor;
import com.hunterbuddy.util.WingTipTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.entity.model.ElytraEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reads the exact world position of the elytra's two wingtips at the one point they are actually drawn.
 *
 * <p>Elytra rendering in this Minecraft version is deferred: {@code ElytraFeatureRenderer.render} does not
 * draw anything itself, it only <em>submits</em> a {@code ModelCommand} - model, render state and a
 * <b>snapshot</b> of the matrix stack at that moment - onto a queue. The actual {@code model.render(...)}
 * call (the one that walks down into the wing {@link ModelPart}s and poses them) happens later, in
 * {@link ModelCommandRenderer}'s own private per-command {@code render}, once every entity in the frame has
 * already been submitted. An earlier version of this hooked {@code ElytraFeatureRenderer.render} itself and
 * flagged "this render pass is the local player's" around it - which looked right reading only that one
 * method, but was wrong: the flag was long since reset by the time this later, real render actually ran, so
 * it never captured anything. Hooking the one place the wing's transform is genuinely computed sidesteps that
 * entirely - no cross-mixin flag, no timing to get right.
 *
 * <p>This fires for every model command in the whole frame - every mob, every piece of equipment, everything
 * that uses the {@code Model} system - so the very first thing it does is throw away anything that is not an
 * {@link ElytraEntityModel}, which is rare on its own (only entities actually wearing and showing an elytra).
 */
@Mixin(ModelCommandRenderer.class)
public abstract class ElytraWingTipCaptureMixin {
    /** Generous on purpose: only needs to rule out a stranger standing yards away, not pin the exact tick. */
    private static final double POSITION_MATCH_RADIUS_SQ = 9.0;

    @Inject(
        method = "render(Lnet/minecraft/client/render/command/OrderedRenderCommandQueueImpl$ModelCommand;Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/client/render/OutlineVertexConsumerProvider;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;)V",
        // Not HEAD: at HEAD, model2.setAngles(model.state()) has not run yet, so the shared wing ModelParts
        // still hold whatever pose the previous setAngles+render call (last frame's, or another elytra-
        // wearing entity queued earlier this same frame) left in them. Landing right after this exact call,
        // confirmed against the real bytecode (Model.setAngles:(Ljava/lang/Object;)V), is what makes the
        // angles this reads actually this command's.
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/model/Model;setAngles(Ljava/lang/Object;)V",
            shift = At.Shift.AFTER
        )
    )
    private <S> void hb$captureWingTips(
        OrderedRenderCommandQueueImpl.ModelCommand<S> command, RenderLayer renderLayer, VertexConsumer vertexConsumer,
        OutlineVertexConsumerProvider outlineVertexConsumers, VertexConsumerProvider.Immediate crumblingOverlayVertexConsumers, CallbackInfo ci
    ) {
        if (!(command.model() instanceof ElytraEntityModel elytraModel)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        S state = command.state();
        if (mc.player == null || !(state instanceof BipedEntityRenderState bipedState)) return;

        // The render state carries no id to compare directly (see WingTipTracker); either signal matching is
        // treated as enough.
        boolean nameMatches = state instanceof PlayerEntityRenderState playerState
            && playerState.playerName != null
            && playerState.playerName.getString().equals(mc.player.getGameProfile().name());
        double dx = bipedState.x - mc.player.getX();
        double dy = bipedState.y - mc.player.getY();
        double dz = bipedState.z - mc.player.getZ();
        boolean positionMatches = dx * dx + dy * dy + dz * dz < POSITION_MATCH_RADIUS_SQ;
        if (!nameMatches && !positionMatches) return;

        ElytraEntityModelAccessor accessor = (ElytraEntityModelAccessor) (Object) elytraModel;
        long now = System.currentTimeMillis();

        MatrixStack matrices = new MatrixStack();
        matrices.push();
        matrices.peek().copy(command.matricesEntry());
        hb$captureWing(matrices, accessor.getLeftWing(), WingTipTracker.LEFT_LOCAL_TIP, now, true);
        hb$captureWing(matrices, accessor.getRightWing(), WingTipTracker.RIGHT_LOCAL_TIP, now, false);
        matrices.pop();
    }

    private static void hb$captureWing(MatrixStack matrices, ModelPart wing, Vec3d local, long now, boolean isLeft) {
        matrices.push();
        // The same real transform the wing itself is about to render with, read one tick early instead of
        // guessed at: pivot, then the pitch/roll/yaw the game already computed for this exact frame.
        wing.applyTransform(matrices);
        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();

        Vector4f point = new Vector4f((float) local.x, (float) local.y, (float) local.z, 1.0F);
        point.mul(positionMatrix);

        // Entity rendering is camera-relative for float precision at large coordinates; adding the camera's
        // own world position back is what turns this into an absolute position FlightTrail can use directly.
        Vec3d cameraPos = MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos();
        Vec3d world = new Vec3d(point.x() + cameraPos.x, point.y() + cameraPos.y, point.z() + cameraPos.z);

        if (isLeft) WingTipTracker.captureLeft(world, now);
        else WingTipTracker.captureRight(world, now);

        matrices.pop();
    }
}
