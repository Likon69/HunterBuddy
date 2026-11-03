package com.hunterbuddy.modules.caveair;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import net.minecraft.util.math.BlockPos;

public final class CaveAirRenderer {
    private final CaveAirOptions options;
    private final RenderLists lists;

    CaveAirRenderer(CaveAirOptions options, RenderLists lists) {
        this.options = options;
        this.lists = lists;
    }

    public void render(Render3DEvent event) {
        for (CellBox cluster : lists.clusters()) {
            drawCluster(event, cluster);
        }
        if (options.highlightAllAir.get()) {
            lists.enclosedAir().forEach(pos -> drawEnclosedCell(event, pos));
        }
        if (options.showAllCaveAir.get()) {
            lists.caveAirBoxes().forEach(box -> drawCaveAir(event, box));
        }
    }

    private void drawCluster(Render3DEvent event, CellBox box) {
        event.renderer.box(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ(),
            options.disturbanceColor.get(), options.lineColor.get(), options.shapeMode.get(), 0);
        if (options.beacon.get()) {
            event.renderer.line(box.centerX(), box.maxY(), box.centerZ(), box.centerX(), box.maxY() + options.beaconHeight.get(),
                box.centerZ(), options.lineColor.get());
        }
    }

    private void drawEnclosedCell(Render3DEvent event, BlockPos pos) {
        event.renderer.box(pos, options.disturbanceColor.get(), options.lineColor.get(), options.shapeMode.get(), 0);
    }

    private void drawCaveAir(Render3DEvent event, CellBox box) {
        event.renderer.box(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ(),
            options.caveAirFill.get(), options.caveAirLine.get(), options.caveAirShape.get(), 0);
    }
}
