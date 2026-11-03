package com.hunterbuddy.modules.stash.ui;

import com.hunterbuddy.modules.StashFinder.StashChunk;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.util.math.BlockPos;

import java.util.Comparator;
import java.util.List;

public final class CoordinateListScreen extends WindowScreen {
    private static final Color WARNING_COLOR = new Color(255, 170, 0);
    private static final int TRAVEL_HEIGHT = 64;

    private final StashServices services;
    private DimensionFilter filter = DimensionFilter.ALL;

    public CoordinateListScreen(GuiTheme theme, StashServices services) {
        super(theme, "Stash Coordinates");
        this.services = services;
    }

    @Override
    public void initWidgets() {
        services.ledger().ensureLoaded();
        List<StashChunk> sorted = services.ledger().chunks().stream()
            .sorted(Comparator.comparingInt(StashChunk::getTotal).reversed())
            .toList();
        WVerticalList root = add(theme.verticalList()).expandX().widget();
        root.add(theme.label("⚠ Coordinates - Do not share! ⚠").color(WARNING_COLOR));
        root.add(theme.horizontalSeparator()).expandX();
        if (sorted.isEmpty()) {
            root.add(theme.label("No stashes found yet."));
            return;
        }
        addFilterRow(root);
        addTable(root, sorted);
    }

    private void addFilterRow(WVerticalList root) {
        WHorizontalList row = root.add(theme.horizontalList()).widget();
        row.add(theme.label("Filter:"));
        for (DimensionFilter option : DimensionFilter.values()) {
            row.add(theme.button(option.label())).widget().action = () -> {
                filter = option;
                reload();
            };
        }
    }

    private void addTable(WVerticalList root, List<StashChunk> sorted) {
        WTable table = root.add(theme.table()).expandX().widget();
        table.add(theme.label("Coordinates"));
        table.add(theme.label("Dim"));
        table.add(theme.label("Total"));
        table.add(theme.label("Actions"));
        table.row();
        for (StashChunk chunk : sorted) {
            if (filter.accepts(chunk)) addRow(table, chunk);
        }
    }

    private void addRow(WTable table, StashChunk chunk) {
        table.add(theme.label(CoordinateFormat.shown(chunk, services.privacy())));
        table.add(theme.label(DimensionBadge.shortLabel(chunk.dimension)).color(DimensionBadge.color(chunk.dimension)));
        table.add(theme.label(String.valueOf(chunk.getTotal())));
        addActions(table.add(theme.horizontalList()).widget(), chunk);
        table.row();
    }

    private void addActions(WHorizontalList actions, StashChunk chunk) {
        actions.add(theme.button("Info")).widget().action =
            () -> MeteorClient.mc.setScreen(new ChunkDetailScreen(theme, services, chunk, this));
        actions.add(theme.button("Copy")).widget().action = () -> CoordinateFormat.copy(chunk, services.privacy());
        actions.add(theme.label("Trace:"));
        WCheckbox trace = actions.add(theme.checkbox(services.tracers().has(chunk))).widget();
        trace.action = () -> services.tracers().toggle(chunk, MeteorClient.mc.player);
        actions.add(theme.button("Go")).widget().action = () -> PathManagers.get()
            .moveTo(new BlockPos(chunk.x, TRAVEL_HEIGHT, chunk.z), true);
        actions.add(theme.minus()).widget().action = () -> delete(chunk);
    }

    private void delete(StashChunk chunk) {
        services.ledger().remove(chunk);
        services.tracers().remove(chunk);
        services.ledger().save();
        reload();
    }
}
