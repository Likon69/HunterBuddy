package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.vanityesp.VanityEngine;
import com.hunterbuddy.modules.vanityesp.VanityOptions;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class VanityESP extends Module {
    private final VanityOptions options = new VanityOptions(settings);
    private final VanityEngine engine = new VanityEngine(options);

    public VanityESP() {
        super(HunterBuddyAddon.VISUALS_CATEGORY, "VanityESP",
            "Unified ESP for decorative items and special blocks, HunterBuddy edition renamed to avoid a clash with another mod.");
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> engine.joined());
    }

    @Override
    public void onActivate() {
        engine.activate();
    }

    @Override
    public void onDeactivate() {
        engine.deactivate();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        engine.tick();
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        engine.chunkData(event.chunk());
    }

    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        engine.interact(event);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        engine.render(event);
    }
}
