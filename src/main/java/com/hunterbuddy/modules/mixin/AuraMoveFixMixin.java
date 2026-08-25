package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.modules.KillAuraPlus;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turns the movement input with the aura's aim, so the walk matches the yaw that
 * was sent.
 *
 * <p>A silent rotation sends the yaw of the target while the client keeps walking
 * along the camera. The server predicts the movement from the yaw on the packet
 * and, from 1.21.2 on, from the real keys on the player input packet, so it works
 * out a step in one direction while the client takes one in another. Every swing
 * disagrees, and the disagreement is answered with a setback -- for the tick of
 * the swing and for the ticks after it where Meteor keeps re-sending the same
 * rotation.
 *
 * <p>The keys are left exactly as they are. Only the vector is turned, by the
 * difference between the two yaws, so that
 * {@code R(camera)·(s', f') == R(sent)·(s, f)}: the same step in the world, from
 * the yaw the server is holding. The rotation is the one in
 * {@code Entity.getInputVector}, a plain rotation of {@code (sideways, forward)}
 * by the yaw, which is what makes the inverse a rotation by the difference.
 *
 * <p>The scale factor is the part that is easy to miss.
 * {@code ClientPlayerEntity.applyDirectionalMovementSpeedFactors} normalises the
 * vector and then multiplies it by {@code min(length * sqrt(1 + h^2), 1)} with
 * {@code h} the ratio of the smaller component to the larger -- so the length
 * depends on the <em>direction</em>. Vanilla only ever hands it one of eight
 * directions; a turned vector points anywhere, and since the base length is 0.98
 * rather than 1 the cap does not hide the difference. Walking straight ahead
 * would come out two percent fast, and while sneaking, where the length is a
 * third, up to forty percent fast. Multiplying by the ratio of the two factors
 * puts the speed back exactly where the keys alone would have put it.
 *
 * <p>Consequence, and it is deliberate: during a swing and the hold ticks that
 * follow it, forward is toward the target rather than toward the camera. Outside
 * that window nothing is touched.
 */
@Mixin(KeyboardInput.class)
public abstract class AuraMoveFixMixin extends Input {
    @Inject(method = "tick", at = @At("TAIL"))
    private void hunterBuddy$turnMovementWithTheAim(CallbackInfo ci) {
        float aim = KillAuraPlus.moveFixYaw();
        if (Float.isNaN(aim)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || movementVector == null) return;

        float sideways = movementVector.x;
        float forward = movementVector.y;
        if (sideways == 0.0F && forward == 0.0F) return;

        // Vanilla has already normalised what the keys gave, and the yaw here is
        // still the camera's: Meteor writes the aim onto the movement packet at
        // the end of the tick, long after this and after travel().
        double delta = Math.toRadians(MathHelper.wrapDegrees(aim - mc.player.getYaw()));
        float cos = (float) Math.cos(delta);
        float sin = (float) Math.sin(delta);

        float turnedSideways = sideways * cos - forward * sin;
        float turnedForward = forward * cos + sideways * sin;

        float scale = hunterBuddy$spread(sideways, forward) / hunterBuddy$spread(turnedSideways, turnedForward);

        // Written straight onto the field. The addon's Input mixin renormalises to
        // one when both components are set through it, which would throw the scale
        // above away and leave the speed wrong in exactly the case it fixes.
        movementVector = new Vec2f(turnedSideways * scale, turnedForward * scale);
    }

    /** The direction factor of {@code getDirectionalMovementSpeedMultiplier}, same formula. */
    @Unique
    private static float hunterBuddy$spread(float x, float y) {
        float ax = Math.abs(x);
        float ay = Math.abs(y);
        float ratio = ay > ax ? ax / ay : ay / ax;

        return (float) Math.sqrt(1.0F + ratio * ratio);
    }
}
