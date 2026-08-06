package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.modules.ChunkBatchRate;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ChunkBatchSizeCalculator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The return value of this method is what the client puts in
 * ServerboundChunkBatchReceivedPacket, and the server uses it verbatim as its chunk send
 * rate. Injecting at RETURN rather than HEAD keeps vanilla's own estimate available, which
 * the module reports in its debug output.
 */
@Mixin(ChunkBatchSizeCalculator.class)
public class ChunkBatchSizeCalculatorMixin {
    @Inject(method = "getDesiredChunksPerTick", at = @At("RETURN"), cancellable = true)
    private void hunterBuddy$forceChunkRate(CallbackInfoReturnable<Float> cir) {
        ChunkBatchRate module = Modules.get().get(ChunkBatchRate.class);
        if (module == null || !module.isActive()) return;

        float forced = module.resolveDesiredChunksPerTick(cir.getReturnValueF());
        if (forced > 0.0f) cir.setReturnValue(forced);
    }
}
