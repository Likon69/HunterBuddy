package com.hunterbuddy.modules.stash.ui;

import com.hunterbuddy.modules.StashFinder.StashChunk;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;

import java.util.Map;
import java.util.TreeMap;

public final class StashWidget {
    private StashWidget() {
    }

    public static WWidget build(GuiTheme theme, StashServices services) {
        services.ledger().ensureLoaded();
        WVerticalList list = theme.verticalList();
        list.add(theme.label("Total Stashes: " + services.ledger().chunks().size()));
        addButtons(theme, list, services);
        if (!services.ledger().chunks().isEmpty()) addDimensionCounts(theme, list, services);
        if (services.privacy().hideCoordinates.get()) {
            list.add(theme.label("Coordinates hidden. Use coordinate list to view.").color(theme.textSecondaryColor()));
        }
        return list;
    }

    private static void addButtons(GuiTheme theme, WVerticalList list, StashServices services) {
        WHorizontalList row = list.add(theme.horizontalList()).widget();
        row.add(theme.button("Open Coordinate List")).widget().action =
            () -> MeteorClient.mc.setScreen(new CoordinateListScreen(theme, services));
        row.add(theme.button("Clear All")).widget().action = () -> clearAll(services);
        row.add(theme.button("Reset Tracers")).widget().action = () -> services.tracers().clear();
        row.add(theme.button("Test Emojis")).widget().action = EmojiWaypointTest::run;
    }

    private static void clearAll(StashServices services) {
        services.ledger().clearAll();
        services.tracers().clear();
        services.ledger().save();
    }

    private static void addDimensionCounts(GuiTheme theme, WVerticalList list, StashServices services) {
        Map<String, Integer> perDimension = new TreeMap<>();
        for (StashChunk chunk : services.ledger().chunks()) perDimension.merge(chunk.dimension, 1, Integer::sum);
        WTable table = list.add(theme.table()).widget();
        for (Map.Entry<String, Integer> entry : perDimension.entrySet()) {
            table.add(theme.label(entry.getKey() + ":"));
            table.add(theme.label(entry.getValue() + " stashes"));
            table.row();
        }
    }
}
