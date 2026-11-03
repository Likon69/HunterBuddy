package com.hunterbuddy.modules.scaffold;

import meteordevelopment.meteorclient.events.render.Render3DEvent;

public final class ScaffoldEngine {
    private final ScaffoldSettings settings;
    private final RegearMode regear;
    private final PlaceDelay delay = new PlaceDelay();
    private final PlacedBlocks placed = new PlacedBlocks();
    private final NormalPlacer normal;
    private final RegearPlacer regearPlacer;

    public ScaffoldEngine(ScaffoldSettings settings, RegearMode regear) {
        this.settings = settings;
        this.regear = regear;
        this.normal = new NormalPlacer(settings, placed, delay);
        this.regearPlacer = new RegearPlacer(settings, regear, placed, delay);
    }

    public void tick() {
        if (regear.enabled()) {
            regearPlacer.tick();
        } else {
            normal.tick();
        }
    }

    public void render(Render3DEvent event) {
        placed.render(event, settings.render);
    }

    public void resetDelay() {
        delay.reset();
    }

    public void reset() {
        delay.reset();
        placed.clear();
        normal.reset();
    }
}
