package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.modules.TreeJava;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;

public final class BehaviorEvents {
    private final TreeJava module;

    public BehaviorEvents(TreeJava module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onTick(TickEvent.Pre event) {
        module.tickEngine();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        module.stopEngine(ExitReason.DISCONNECTED, "Game session ended");
    }
}
