package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.modules.ShulkerOverviewModule;
import com.hunterbuddy.modules.ItemSearchBar;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin extends Screen {
    @Shadow protected int x;
    @Shadow protected int y;

    @Unique private TextFieldWidget hunterBuddy$itemSearchField;
    @Unique private ItemSearchBar hunterBuddy$itemSearchModule;

    protected HandledScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void hunterBuddy$initItemSearch(CallbackInfo ci) {
        hunterBuddy$itemSearchModule = Modules.get().get(ItemSearchBar.class);
        if (hunterBuddy$itemSearchModule == null || !hunterBuddy$itemSearchModule.isActive() || !hunterBuddy$itemSearchModule.shouldShowSearchField()) return;

        hunterBuddy$itemSearchField = new TextFieldWidget(
            MinecraftClient.getInstance().textRenderer,
            x + hunterBuddy$itemSearchModule.getOffsetX(),
            y + hunterBuddy$itemSearchModule.getOffsetY(),
            hunterBuddy$itemSearchModule.getFieldWidth(),
            hunterBuddy$itemSearchModule.getFieldHeight(),
            Text.of("Search items...")
        );
        hunterBuddy$itemSearchField.setPlaceholder(Text.of("Search items..."));
        hunterBuddy$itemSearchField.setMaxLength(100);

        String query = hunterBuddy$itemSearchModule.searchQuery.get();
        if (query != null && !query.isEmpty()) hunterBuddy$itemSearchField.setText(query);

        hunterBuddy$itemSearchField.setChangedListener(hunterBuddy$itemSearchModule::updateSearchQuery);
        hunterBuddy$itemSearchField.setFocused(false);
        hunterBuddy$itemSearchField.setEditable(true);
        hunterBuddy$itemSearchField.setVisible(true);
        addDrawableChild(hunterBuddy$itemSearchField);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void hunterBuddy$renderItemSearch(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (hunterBuddy$itemSearchModule != null && hunterBuddy$itemSearchModule.isActive()
            && hunterBuddy$itemSearchModule.shouldShowSearchField() && hunterBuddy$itemSearchField != null) {
            hunterBuddy$itemSearchField.setX(x + hunterBuddy$itemSearchModule.getOffsetX());
            hunterBuddy$itemSearchField.setY(y + hunterBuddy$itemSearchModule.getOffsetY());
            hunterBuddy$itemSearchField.setVisible(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void hunterBuddy$keyPressedItemSearch(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (hunterBuddy$itemSearchModule == null || !hunterBuddy$itemSearchModule.isActive()
            || !hunterBuddy$itemSearchModule.shouldShowSearchField() || hunterBuddy$itemSearchField == null) return;

        if (input.key() == 258) {
            setFocused(hunterBuddy$itemSearchField);
            hunterBuddy$itemSearchField.setFocused(true);
            cir.setReturnValue(true);
        } else if (hunterBuddy$itemSearchField.isFocused()) {
            if (input.key() == 256) {
                setFocused(null);
                hunterBuddy$itemSearchField.setFocused(false);
            } else {
                hunterBuddy$itemSearchField.keyPressed(input);
            }
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void hunterBuddy$clickItemSearch(Click click, boolean pressed, CallbackInfoReturnable<Boolean> cir) {
        if (hunterBuddy$itemSearchModule == null || !hunterBuddy$itemSearchModule.isActive()
            || !hunterBuddy$itemSearchModule.shouldShowSearchField() || hunterBuddy$itemSearchField == null) return;

        boolean inField = click.x() >= hunterBuddy$itemSearchField.getX()
            && click.x() < hunterBuddy$itemSearchField.getX() + hunterBuddy$itemSearchField.getWidth()
            && click.y() >= hunterBuddy$itemSearchField.getY()
            && click.y() < hunterBuddy$itemSearchField.getY() + hunterBuddy$itemSearchField.getHeight();
        if (inField) {
            setFocused(hunterBuddy$itemSearchField);
            hunterBuddy$itemSearchField.setFocused(true);
            if (hunterBuddy$itemSearchField.mouseClicked(click, pressed)) cir.setReturnValue(true);
        } else {
            if (getFocused() == hunterBuddy$itemSearchField) setFocused(null);
            hunterBuddy$itemSearchField.setFocused(false);
        }
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (hunterBuddy$itemSearchModule != null && hunterBuddy$itemSearchModule.isActive()
            && hunterBuddy$itemSearchModule.shouldShowSearchField() && hunterBuddy$itemSearchField != null
            && hunterBuddy$itemSearchField.isFocused() && hunterBuddy$itemSearchField.charTyped(input)) {
            return true;
        }

        return super.charTyped(input);
    }

    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void hunterBuddy$highlightMatchingSlot(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        ItemSearchBar module = Modules.get().get(ItemSearchBar.class);
        if (module != null && module.isActive() && slot.hasStack() && module.shouldHighlightSlot(slot.getStack())) {
            context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, module.highlightColor.get().getPacked());
        }
    }

    // Inject into drawSlot which has signature (DrawContext, Slot, int, int) in 1.21.11
    // The DrawContext is already translated to the GUI position, so slot.x/slot.y
    // are GUI-relative and will be drawn at the right screen position.
    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void onDrawSlot(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        ShulkerOverviewModule module = (ShulkerOverviewModule) Modules.get().get(ShulkerOverviewModule.class);
        if (module == null || !module.isActive()) return;

        ItemStack stack = slot.getStack();
        if (stack.isEmpty()) return;
        if (!(stack.getItem() instanceof BlockItem blockItem)) return;
        if (!(blockItem.getBlock() instanceof ShulkerBoxBlock)) return;

        module.renderShulkerOverlay(context, slot.x, slot.y, stack);
    }
}
