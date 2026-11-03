package com.hunterbuddy.modules.stash.ui;

import baritone.api.BaritoneAPI;
import baritone.api.command.manager.ICommandManager;
import com.hunterbuddy.modules.StashFinder.StashChunk;
import com.hunterbuddy.modules.regear.util.BaritoneHelper;
import com.hunterbuddy.modules.stash.StashType;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gui.screen.Screen;

public final class ChunkDetailScreen extends WindowScreen {
    private static final Color COUNT_COLOR = new Color(85, 255, 85);

    private final StashServices services;
    private final StashChunk chunk;
    private final Screen parent;

    public ChunkDetailScreen(GuiTheme theme, StashServices services, StashChunk chunk, Screen parent) {
        super(theme, "Stash Details");
        this.services = services;
        this.chunk = chunk;
        this.parent = parent;
    }

    @Override
    public void initWidgets() {
        WVerticalList root = add(theme.verticalList()).expandX().widget();
        WTable table = root.add(theme.table()).expandX().widget();
        addSummary(table);
        root.add(theme.horizontalSeparator()).expandX();
        addBreakdown(root.add(theme.table()).expandX().widget());
        addButtons(root.add(theme.horizontalList()).widget());
    }

    private void addSummary(WTable table) {
        addRow(table, "Coordinates:", CoordinateFormat.shown(chunk, services.privacy()));
        addRow(table, "Dimension:", chunk.dimension);
        addRow(table, "Total:", String.valueOf(chunk.getTotal()));
    }

    private void addBreakdown(WTable table) {
        for (StashType type : StashType.values()) {
            int count = chunk.countOf(type);
            if (!type.showsInDetail() || count == 0) continue;
            table.add(theme.label(type.symbol() + " " + type.label() + ":"));
            table.add(theme.label(String.valueOf(count)).color(COUNT_COLOR));
            table.row();
        }
    }

    private void addButtons(WHorizontalList row) {
        row.add(theme.button("Copy Coordinates")).widget().action =
            () -> CoordinateFormat.copy(chunk, services.privacy());
        if (BaritoneHelper.isAvailable()) row.add(theme.button("Fly Here")).widget().action = this::flyHere;
        row.add(theme.button("Back")).widget().action = () -> MeteorClient.mc.setScreen(parent);
    }

    private void addRow(WTable table, String label, String value) {
        table.add(theme.label(label));
        table.add(theme.label(value));
        table.row();
    }

    private void flyHere() {
        ICommandManager commands = BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager();
        commands.execute("goal " + chunk.x + " " + chunk.z);
        commands.execute("elytra");
        close();
    }
}
