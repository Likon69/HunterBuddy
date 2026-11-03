package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.rotationdetector.DetectionEngine;
import com.hunterbuddy.modules.rotationdetector.DetectionRenderer;
import com.hunterbuddy.modules.rotationdetector.RotationDetectorOptions;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import xaeroplus.XaeroPlus;
import xaeroplus.event.ChunkDataEvent;

public class RotationDetector extends Module {
    private final RotationDetectorOptions options = new RotationDetectorOptions(settings);
    private final DetectionEngine engine = new DetectionEngine(options);
    private final DetectionRenderer renderer = new DetectionRenderer(options, engine);

    public RotationDetector() {
        super(HunterBuddyAddon.VISUALS_CATEGORY, "rotation-detector",
            "Finds horizontal blocks that natural generation only makes vertical.");
    }

    @Override
    public void onActivate() {
        engine.reset();
        engine.queueViewChunks();
        XaeroPlus.EVENT_BUS.register(this);
    }

    @Override
    public void onDeactivate() {
        XaeroPlus.EVENT_BUS.unregister(this);
        engine.reset();
    }

    @Override
    public String getInfoString() {
        return String.valueOf(engine.blockCount());
    }

    @net.lenni0451.lambdaevents.EventHandler
    public void onXaeroChunkData(ChunkDataEvent event) {
        engine.requeue(event.chunk());
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        engine.tick();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        renderer.render(event);
    }
}
