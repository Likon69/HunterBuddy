package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.boost.VelocityBoost;
import com.hunterbuddy.boost.TimedRocketFly;
import com.hunterbuddy.modules.ElytraBounce;
import com.hunterbuddy.modules.Pitch40;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * After a stall - a chunk batch, a long frame - vanilla runs every tick it
 * owes in the next frame, and their movement packets reach the server in one
 * burst. Past five bunched packets the server holds the whole burst to a
 * single tick's allowance and teleports the rest back, which on a dive reads
 * as a setback out of nowhere. Capped at three ticks a frame here, with the
 * remainder handed back to the progress counter rather than dropped, so the
 * catch-up spreads over the next frames and no tick is lost.
 */
@Mixin(RenderTickCounter.Dynamic.class)
public abstract class CatchUpPacingMixin {
    private static final int MAX_TICKS_PER_FRAME = 3;

    @Shadow
    private float tickProgress;

    @Inject(method = "beginRenderTick(JZ)I", at = @At("RETURN"), cancellable = true)
    private void hunterBuddy$paceCatchUp(long timeMillis, boolean paused, CallbackInfoReturnable<Integer> cir) {
        int ticks = cir.getReturnValue();
        if (ticks > MAX_TICKS_PER_FRAME && hunterBuddy$pacingWanted()) {
            this.tickProgress = this.tickProgress + (float)(ticks - MAX_TICKS_PER_FRAME);
            cir.setReturnValue(MAX_TICKS_PER_FRAME);
        }
    }

    private static boolean hunterBuddy$pacingWanted() {
        Modules modules = Modules.get();
        if (modules == null) {
            return false;
        }

        VelocityBoost boost = modules.get(VelocityBoost.class);
        if (boost == null || !boost.catchUpPacing.get()) {
            return false;
        }

        Pitch40 pitch40 = modules.get(Pitch40.class);
        TimedRocketFly rocketFly = modules.get(TimedRocketFly.class);
        ElytraBounce bounce = modules.get(ElytraBounce.class);
        return pitch40 != null && pitch40.isActive() || rocketFly != null && rocketFly.isActive() || bounce != null && bounce.isActive();
    }
}
