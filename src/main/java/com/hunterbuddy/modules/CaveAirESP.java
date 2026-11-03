package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.caveair.CaveAirEngine;
import com.hunterbuddy.modules.caveair.CaveAirOptions;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class CaveAirESP extends Module {
    private final CaveAirOptions options = new CaveAirOptions(settings);
    private final CaveAirEngine engine = new CaveAirEngine(options);

    public CaveAirESP() {
        super(HunterBuddyAddon.VISUALS_CATEGORY, "cave-air", "Detects portal-shaped disturbances in cave air.");
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

    public String stats() {
        return engine.stats();
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        engine.onChunkLoaded(event.chunk());
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        engine.onBlockUpdated(event.pos);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        engine.tick();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (engine.isDimensionEnabled()) {
            engine.renderer().render(event);
        }
    }
}
