package com.hunterbuddy.events;

import net.minecraft.text.Text;

/**
 * Fired when the connection ends, whatever ended it.
 *
 * <p>Hooked at the single point every case funnels through, so a server kick, a
 * timeout and our own {@code AutoLogPlus} all arrive here. That last one needs
 * no special casing: it already prefixes its own reason with
 * {@code [AutoLogPlus]}, so the text says why without any coupling between the
 * two modules.
 */
public record ServerDisconnectEvent(Text reason) {
}
