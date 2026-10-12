package com.hunterbuddy.behavior.resource;

public record MovementIntent(boolean forward, boolean back, boolean left, boolean right,
                             boolean jump, boolean sneak, boolean sprint) {
}
