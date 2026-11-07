package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.gui.theme.HbGuiTheme;
import com.hunterbuddy.gui.theme.hud.HbHudEditorPainter;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.screens.HudEditorScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = HudEditorScreen.class, remap = false)
public abstract class HudEditorScreenMixin {
    @Shadow @Final private Hud hud;
    @Shadow @Final private List<HudElement> selection;
    @Shadow private boolean pressed;
    @Shadow private boolean dragging;
    @Shadow private boolean moved;
    @Shadow private int clickX;
    @Shadow private int clickY;
    @Shadow private double splitLinesAnimation;

    @Unique private final HbHudEditorPainter hunterBuddy$painter = new HbHudEditorPainter();

    @Shadow
    protected abstract void fillSelection(int mouseX, int mouseY);

    @Shadow
    protected abstract HudElement getHovered(int mouseX, int mouseY);

    @Inject(method = "onRender(II)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void hunterBuddy$themedRender(int mouseX, int mouseY, CallbackInfo ci) {
        if (!(GuiThemes.get() instanceof HbGuiTheme theme)) return;
        ci.cancel();
        boolean marqueeing = pressed && !dragging;
        if (marqueeing) fillSelection(mouseX, mouseY);
        HbHudEditorPainter.Marquee marquee = marqueeing
            ? new HbHudEditorPainter.Marquee(Math.min(clickX, mouseX), Math.min(clickY, mouseY), Math.max(clickX, mouseX), Math.max(clickY, mouseY))
            : null;
        HudElement hovered = pressed ? null : getHovered(mouseX, mouseY);
        try {
            hunterBuddy$painter.paintElements(theme, hud, selection, hovered, marquee, pressed && dragging && moved);
        } finally {
            if (marqueeing) selection.clear();
        }
    }

    @Inject(method = "renderSplitLines(ZD)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void hunterBuddy$themedBackdrop(boolean increment, double delta, CallbackInfo ci) {
        if (!(GuiThemes.get() instanceof HbGuiTheme theme)) return;
        ci.cancel();
        splitLinesAnimation = Math.clamp(splitLinesAnimation + (increment ? delta : -delta) * 6, 0, 1);
        Renderer2D.COLOR.begin();
        hunterBuddy$painter.paintBackdrop(theme, increment, delta, splitLinesAnimation);
        Renderer2D.COLOR.render();
    }
}
