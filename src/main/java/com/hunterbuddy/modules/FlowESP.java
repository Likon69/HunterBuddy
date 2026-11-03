package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.flowesp.FlowEngine;
import com.hunterbuddy.modules.flowesp.FlowOptions;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class FlowESP extends Module {
    private final FlowOptions options = new FlowOptions(settings);
    private final FlowEngine engine = new FlowEngine(options);

    public FlowESP() {
        super(HunterBuddyAddon.VISUALS_CATEGORY, "flow-esp", "Detects chunk activity through analysis of fluid spread.");
    }

    @Override
    public void onActivate() {
        engine.start();
    }

    @Override
    public void onDeactivate() {
        engine.stop();
    }

    public void clearCache() {
        engine.clearCache();
    }

    public void forceSave() {
        engine.forceSave();
    }

    public String stats() {
        return engine.stats();
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        engine.onChunkData(event.chunk());
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        engine.tick();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        engine.render(event);
    }

    public enum GradientSource {
        FlowLength,
        ChunkAge,
        Both
    }
}
