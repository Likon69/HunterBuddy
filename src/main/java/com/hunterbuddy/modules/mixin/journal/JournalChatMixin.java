package com.hunterbuddy.modules.mixin.journal;

import com.hunterbuddy.journal.JournalCapture;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChatHud.class, priority = 1200)
public abstract class JournalChatMixin {
    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
        at = @At("HEAD"))
    private void hunterBuddy$localJournal(Text text, MessageSignatureData signature, MessageIndicator indicator, CallbackInfo info) {
        JournalCapture.chat(text, signature != null);
    }
}
