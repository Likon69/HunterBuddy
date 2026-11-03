package com.hunterbuddy.modules.vanityesp;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

final class VaultFeature implements VanityFeature {
    private final VanityOptions options;
    private final VaultRegistry registry = new VaultRegistry();
    private final VaultScheduler scheduler = new VaultScheduler(registry);

    VaultFeature(VanityOptions options) {
        this.options = options;
    }

    @Override
    public void deactivate() {
        scheduler.reset();
    }

    @Override
    public void joined() {
        scheduler.startInitialScan();
    }

    @Override
    public void tick() {
        if (options.ominousVaults.get()) {
            scheduler.tick();
        }
    }

    @Override
    public void render(Render3DEvent event) {
        if (!options.ominousVaults.get() || !options.vaultStyle.drawsShapes()) return;
        for (BlockPos vault : registry.all()) {
            Outline.block(event, vault, options.vaultStyle);
            Outline.tracer(event, Vec3d.ofCenter(vault), options.vaultStyle);
        }
    }
}
