package com.hunterbuddy.hud.movement;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

public final class ActionBarStatus {
    private static final long MS_PER_TICK = 50;

    private final MovementStatusOptions options;
    private long lastMessageAt;
    private boolean wasActive;

    public ActionBarStatus(MovementStatusOptions options) {
        this.options = options;
    }

    public void update(ClientPlayerEntity player) {
        boolean sneaking = options.showSneaking.get() && player.isSneaking();
        boolean sprinting = options.showSprinting.get() && player.isSprinting();
        if (sneaking || sprinting) {
            announce(player, message(sneaking, sprinting));
        } else {
            fadeOut(player);
        }
    }

    private String message(boolean sneaking, boolean sprinting) {
        StringBuilder text = new StringBuilder(options.prefix.get());
        if (sneaking) {
            text.append(options.sneakingText.get());
        }
        if (sneaking && sprinting) {
            text.append(options.separator.get());
        }
        if (sprinting) {
            text.append(options.sprintingText.get());
        }
        return text.append(options.suffix.get()).toString();
    }

    private void announce(ClientPlayerEntity player, String message) {
        if (message.isEmpty()) return;
        player.sendMessage(Text.literal(message), true);
        lastMessageAt = System.currentTimeMillis();
        wasActive = true;
    }

    private void fadeOut(ClientPlayerEntity player) {
        if (!wasActive) return;
        long lingerMs = options.fadeDelay.get() * MS_PER_TICK;
        if (System.currentTimeMillis() - lastMessageAt < lingerMs) return;
        player.sendMessage(Text.literal(""), true);
        wasActive = false;
    }
}
