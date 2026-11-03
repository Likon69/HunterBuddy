package com.hunterbuddy.hud.movement;

import com.hunterbuddy.hud.MovementStatusHud.DisplayMode;
import com.hunterbuddy.hud.movement.StatusEntries.Status;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public final class MovementStatusView {
    private static final Size ACTION_BAR_SIZE = new Size(100.0, 16.0);
    private static final String ACTION_BAR_PREVIEW = "[ActionBar Mode]";

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final MovementStatusOptions options;
    private final StatusEntries entries;
    private final ActionBarStatus actionBar;

    public MovementStatusView(MovementStatusOptions options) {
        this.options = options;
        this.entries = new StatusEntries(options);
        this.actionBar = new ActionBarStatus(options);
    }

    public Size renderPreview(HudRenderer renderer, int x, int y) {
        if (options.displayMode.get() != DisplayMode.ActionBar) {
            return stack(renderer).drawPreview(entries.preview(), x, y);
        }
        boolean shadow = options.textShadow.get();
        double scale = options.textScale.get();
        renderer.text(ACTION_BAR_PREVIEW, x, y, options.sneakingColor.get(), shadow, scale);
        return new Size(renderer.textWidth(ACTION_BAR_PREVIEW, shadow, scale), renderer.textHeight(shadow, scale));
    }

    public Size renderLive(HudRenderer renderer, int x, int y) {
        if (mc.world == null || mc.player == null) return null;
        if (options.displayMode.get() == DisplayMode.ActionBar) {
            actionBar.update(mc.player);
            return ACTION_BAR_SIZE;
        }
        return liveStatus(renderer, x, y, mc.player);
    }

    private Size liveStatus(HudRenderer renderer, int x, int y, ClientPlayerEntity player) {
        Set<Status> active = EnumSet.noneOf(Status.class);
        if (player.isSneaking()) {
            active.add(Status.SNEAKING);
        }
        if (player.isSprinting()) {
            active.add(Status.SPRINTING);
        }
        List<StatusEntry> shown = entries.live(active);
        StatusStack stack = stack(renderer);
        return shown.isEmpty() ? stack.placeholder() : stack.draw(shown, x, y);
    }

    private StatusStack stack(HudRenderer renderer) {
        EntryPainter painter = options.displayMode.get() == DisplayMode.Icon
            ? new IconPainter(renderer, options)
            : new TextPainter(renderer, options);
        return new StatusStack(painter, options.layout.get());
    }
}
