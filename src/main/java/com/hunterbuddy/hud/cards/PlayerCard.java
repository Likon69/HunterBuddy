package com.hunterbuddy.hud.cards;

import meteordevelopment.meteorclient.utils.render.color.Color;

public record PlayerCard(PlayerTrack track, PlayerMovement movement, Color accent, Color pill, double alpha) {
}
