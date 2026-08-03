package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.modules.InvFix2b2t;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.network.ClientConnectionState;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.SetCursorItemS2CPacket;
import net.minecraft.network.packet.s2c.play.SetPlayerInventoryS2CPacket;

@Mixin(value = ClientPlayNetworkHandler.class, priority = 900)
public abstract class Fix2b2tBundlesMixin extends ClientCommonNetworkHandler {
    protected Fix2b2tBundlesMixin(MinecraftClient client, ClientConnection connection, ClientConnectionState connectionState) {
        super(client, connection, connectionState);
    }

    @Unique
    private void hunterBuddy$fixBundle(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;

        InvFix2b2t module = Modules.get().get(InvFix2b2t.class);
        if (module == null || !module.isActive() || !module.fixBundles.get()) return;

        if (stack.contains(DataComponentTypes.BUNDLE_CONTENTS)) {
            stack.get(DataComponentTypes.BUNDLE_CONTENTS).iterate().forEach(this::hunterBuddy$fixBundle);
        } else if (stack.contains(DataComponentTypes.CONTAINER)) {
            stack.get(DataComponentTypes.CONTAINER).stream().forEach(this::hunterBuddy$fixBundle);
        }

        if (stack.contains(DataComponentTypes.BUNDLE_CONTENTS)) {
            BundleContentsComponent contents = stack.get(DataComponentTypes.BUNDLE_CONTENTS);
            stack.set(DataComponentTypes.BUNDLE_CONTENTS, new BundleContentsComponent(contents.stream().toList().reversed()));
        }
    }

    @Inject(method = "onInventory", at = @At("HEAD"))
    public void hunterBuddy$onInventory(InventoryS2CPacket packet, CallbackInfo info) {
        if (this.client.isOnThread()) {
            packet.contents().forEach(this::hunterBuddy$fixBundle);
            this.hunterBuddy$fixBundle(packet.cursorStack());
        }
    }

    @Inject(method = "onScreenHandlerSlotUpdate", at = @At("HEAD"))
    public void hunterBuddy$onScreenHandlerSlotUpdate(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo info) {
        if (this.client.isOnThread()) {
            this.hunterBuddy$fixBundle(packet.getStack());
        }
    }

    @Inject(method = "onSetPlayerInventory", at = @At("HEAD"))
    public void hunterBuddy$onSetPlayerInventory(SetPlayerInventoryS2CPacket packet, CallbackInfo info) {
        if (this.client.isOnThread()) {
            this.hunterBuddy$fixBundle(packet.contents());
        }
    }

    @Inject(method = "onSetCursorItem", at = @At("HEAD"))
    public void hunterBuddy$onSetCursorItem(SetCursorItemS2CPacket packet, CallbackInfo info) {
        if (this.client.isOnThread()) {
            this.hunterBuddy$fixBundle(packet.contents());
        }
    }
}
