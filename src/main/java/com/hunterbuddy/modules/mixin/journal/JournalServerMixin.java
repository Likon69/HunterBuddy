package com.hunterbuddy.modules.mixin.journal;

import com.hunterbuddy.journal.JournalContext;
import com.hunterbuddy.journal.JournalCapture;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.network.message.MessageHandler;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MessageHandler.class)
public abstract class JournalServerMixin {
    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;addMessage(Lnet/minecraft/text/Text;)V"))
    private void hunterBuddy$serverMessage(ChatHud hud, Text text, Operation<Void> original) {
        if (!JournalCapture.enabled()) {
            original.call(hud, text);
            return;
        }
        JournalContext context = JournalContext.current();
        context.serverDepth++;
        try {
            original.call(hud, text);
        } finally {
            context.serverDepth--;
        }
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/hud/ChatHud;addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V"))
    private void hunterBuddy$serverChat(ChatHud hud, Text text, MessageSignatureData signature, MessageIndicator indicator, Operation<Void> original) {
        if (!JournalCapture.enabled()) {
            original.call(hud, text, signature, indicator);
            return;
        }
        JournalContext context = JournalContext.current();
        context.serverDepth++;
        try {
            original.call(hud, text, signature, indicator);
        } finally {
            context.serverDepth--;
        }
    }
}
