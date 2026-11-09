package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.modules.messenger.Messenger;
import com.hunterbuddy.chatdock.ChatDock;
import com.hunterbuddy.modules.messenger.ui.MessengerOverlay;
import com.hunterbuddy.modules.messenger.ui.Pointer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenOverlayMixin {
    @Shadow
    protected TextFieldWidget chatField;

    @Unique
    private boolean hunterBuddy$ownsInput;

    @Unique
    private static MessengerOverlay hunterBuddy$overlay() {
        Messenger messenger = Messenger.get();
        return messenger != null && messenger.isActive() ? messenger.overlay() : null;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void hunterBuddy$renderOverlay(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MessengerOverlay overlay = hunterBuddy$overlay();
        if (overlay != null) overlay.render(context);
        ChatDock.INSTANCE.render(context);
        boolean owns = overlay != null && overlay.inputFocused() || ChatDock.INSTANCE.inputFocused();
        if (owns != hunterBuddy$ownsInput) {
            hunterBuddy$ownsInput = owns;
            chatField.setFocused(!owns);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void hunterBuddy$click(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (ChatDock.INSTANCE.click(Pointer.fromGui(click.x()), Pointer.fromGui(click.y()), click.button())) {
            cir.setReturnValue(true);
            return;
        }
        MessengerOverlay overlay = hunterBuddy$overlay();
        if (overlay == null) return;

        if (overlay.mouseClicked(Pointer.fromGui(click.x()), Pointer.fromGui(click.y()), click.button(), doubled)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void hunterBuddy$scroll(double mouseX, double mouseY, double horizontal, double vertical, CallbackInfoReturnable<Boolean> cir) {
        if (ChatDock.INSTANCE.scroll(Pointer.fromGui(mouseX), Pointer.fromGui(mouseY), vertical)) {
            cir.setReturnValue(true);
            return;
        }
        MessengerOverlay overlay = hunterBuddy$overlay();
        if (overlay == null) return;

        if (overlay.mouseScrolled(Pointer.fromGui(mouseX), Pointer.fromGui(mouseY), vertical)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void hunterBuddy$key(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (ChatDock.INSTANCE.key(input.key(), input.modifiers())) {
            cir.setReturnValue(true);
            return;
        }
        MessengerOverlay overlay = hunterBuddy$overlay();
        if (overlay == null) return;

        if (overlay.keyPressed(input.key(), input.modifiers())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void hunterBuddy$closed(CallbackInfo ci) {
        ChatDock.INSTANCE.closed();
        MessengerOverlay overlay = hunterBuddy$overlay();
        if (overlay != null) overlay.reset();
    }
}
