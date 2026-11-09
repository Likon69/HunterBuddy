package com.hunterbuddy.modules.mixin.journal;

import com.hunterbuddy.journal.JournalCapture;
import com.hunterbuddy.journal.JournalContext;
import com.hunterbuddy.journal.JournalSource;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ChatUtils.class, remap = false)
public abstract class JournalMeteorMixin {
    @WrapOperation(method = "sendMsg(ILjava/lang/String;Lnet/minecraft/util/Formatting;Lnet/minecraft/text/Text;)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/MinecraftClient;execute(Ljava/lang/Runnable;)V", remap = true))
    private static void hunterBuddy$journalOrigin(MinecraftClient client, Runnable action, Operation<Void> original) {
        if (JournalCapture.enabled()) {
            JournalSource source = JournalCapture.caller();
            action = JournalContext.current().carry(source == null ? JournalSource.METEOR : source, action);
        }
        original.call(client, action);
    }
}
