package com.hunterbuddy.modules.scaffold;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public final class ScaffoldEngine {
    private final ScaffoldSettings settings;
    private final RegearMode regear;
    private final PlaceDelay delay = new PlaceDelay();
    private final PlacedBlocks placed = new PlacedBlocks();
    private final NormalPlacer normal;
    private final ScaffoldPlacement placement;
    private final RegearPlacer regearPlacer;

    public ScaffoldEngine(ScaffoldSettings settings, RegearMode regear) {
        this.settings = settings;
        this.regear = regear;
        placement = new ScaffoldPlacement(settings, placed, delay);
        this.normal = new NormalPlacer(settings, placement, delay);
        this.regearPlacer = new RegearPlacer(settings, regear, placement, delay);
    }

    public void reconcile() { placement.reconcile(); }

    public void tick() {
        placement.cancel();
        delay.advance();
        if (regear.enabled()) {
            regearPlacer.tick();
        } else {
            normal.tick();
        }
    }

    public void movementPre() {
        placement.pre();
    }

    public void movementPost() {
        placement.post();
    }

    public void movementSent(PlayerMoveC2SPacket packet) {
        placement.movementSent(packet);
    }

    public void render(Render3DEvent event) {
        placed.render(event, settings.render);
    }

    public void resetDelay() {
        placement.cancel();
        delay.reset();
        normal.reset();
    }

    public void reset() {
        placement.reset();
        delay.reset();
        placed.clear();
        normal.reset();
    }
}
