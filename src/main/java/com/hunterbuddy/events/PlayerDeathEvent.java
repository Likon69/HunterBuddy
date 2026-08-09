package com.hunterbuddy.events;

import net.minecraft.text.Text;

/**
 * Fired when the server announces your own death.
 *
 * <p>{@code message} is the server's own wording — "X was slain by Y whilst
 * trying to escape Z" — so the cause and the killer are already in it and there
 * is nothing to reconstruct client side.
 */
public record PlayerDeathEvent(Text message) {
}
