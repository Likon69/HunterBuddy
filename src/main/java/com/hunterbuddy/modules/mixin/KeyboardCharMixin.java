package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.modules.messenger.Messenger;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.input.CharInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class KeyboardCharMixin {
    @Inject(method = "onChar", at = @At("HEAD"), cancellable = true)
    private void hunterBuddy$typeIntoMessenger(long window, CharInput input, CallbackInfo ci) {
        if (!(MinecraftClient.getInstance().currentScreen instanceof ChatScreen)) return;

        Messenger messenger = Messenger.get();
        if (messenger == null || !messenger.isActive()) return;

        if (messenger.overlay().charTyped(input.codepoint())) ci.cancel();
    }
}
