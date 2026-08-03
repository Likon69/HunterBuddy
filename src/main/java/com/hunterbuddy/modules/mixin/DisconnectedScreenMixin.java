package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.events.DisconnectedScreenEvent;
import meteordevelopment.meteorclient.MeteorClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

@Mixin(DisconnectedScreen.class)
public class DisconnectedScreenMixin extends Screen {
    protected DisconnectedScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("HEAD"))
    public void hunterBuddy$onInit(CallbackInfo ci) {
        MeteorClient.EVENT_BUS.post(new DisconnectedScreenEvent());
    }
}
