package com.hunterbuddy.chatdock;

import com.hunterbuddy.modules.messenger.ui.Pointer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class HunterBuddyScreen extends Screen {
    public HunterBuddyScreen() {
        super(Text.literal("HunterBuddy"));
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        ChatDock.INSTANCE.render(context);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        return ChatDock.INSTANCE.click(Pointer.fromGui(click.x()), Pointer.fromGui(click.y()), click.button())
            || super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return ChatDock.INSTANCE.scroll(Pointer.fromGui(x), Pointer.fromGui(y), vertical)
            || super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(input);
        return ChatDock.INSTANCE.key(input.key(), input.modifiers()) || super.keyPressed(input);
    }

    @Override
    public void removed() {
        ChatDock.INSTANCE.closed();
    }
}
