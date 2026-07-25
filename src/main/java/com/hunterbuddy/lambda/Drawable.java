/*
 * Port of com.lambda.interaction.construction.simulation.result.Drawable
 * (lambda 1.21.11) — direct 1:1 translation to Java.
 */
package com.hunterbuddy.lambda;

import meteordevelopment.meteorclient.renderer.Renderer3D;

@FunctionalInterface
public interface Drawable {
    void render(Renderer3D renderer);
}
