package com.hunterbuddy.hud.movement;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public final class StatusEntries {
    enum Status {
        SNEAKING,
        SPRINTING
    }

    private final MovementStatusOptions options;
    private ItemStack sneakIcon;
    private ItemStack sprintIcon;

    public StatusEntries(MovementStatusOptions options) {
        this.options = options;
    }

    List<StatusEntry> preview() {
        return entriesFor(enabledStatuses());
    }

    List<StatusEntry> live(Set<Status> active) {
        Set<Status> shown = enabledStatuses();
        shown.retainAll(active);
        return entriesFor(shown);
    }

    private Set<Status> enabledStatuses() {
        Set<Status> enabled = EnumSet.noneOf(Status.class);
        if (options.showSneaking.get()) {
            enabled.add(Status.SNEAKING);
        }
        if (options.showSprinting.get()) {
            enabled.add(Status.SPRINTING);
        }
        return enabled;
    }

    private List<StatusEntry> entriesFor(Set<Status> statuses) {
        List<StatusEntry> entries = new ArrayList<>();
        if (statuses.contains(Status.SNEAKING)) {
            entries.add(new StatusEntry(options.sneakingText.get(), options.sneakingColor.get(), sneakIcon()));
        }
        if (statuses.contains(Status.SPRINTING)) {
            entries.add(new StatusEntry(options.sprintingText.get(), options.sprintingColor.get(), sprintIcon()));
        }
        return entries;
    }

    private ItemStack sneakIcon() {
        if (sneakIcon == null) {
            sneakIcon = new ItemStack(Items.LEATHER_BOOTS);
        }
        return sneakIcon;
    }

    private ItemStack sprintIcon() {
        if (sprintIcon == null) {
            sprintIcon = new ItemStack(Items.FEATHER);
        }
        return sprintIcon;
    }
}
