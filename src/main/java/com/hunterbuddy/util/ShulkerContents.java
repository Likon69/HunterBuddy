package com.hunterbuddy.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public final class ShulkerContents {
    private ShulkerContents() {
    }

    public static Map<Item, Integer> totals(ItemStack shulker) {
        Map<Item, Integer> totals = new LinkedHashMap<>();

        ContainerComponent container = shulker.get(DataComponentTypes.CONTAINER);
        if (container != null) {
            container.stream().forEach(inner -> {
                if (!inner.isEmpty()) totals.merge(inner.getItem(), inner.getCount(), Integer::sum);
            });
            if (!totals.isEmpty()) return totals;
        }

        NbtComponent custom = shulker.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null || custom.isEmpty()) return totals;

        NbtCompound root = custom.copyNbt();
        NbtCompound blockEntity = root.getCompound("BlockEntityTag").orElse(null);
        if (blockEntity == null) return totals;
        NbtList items = blockEntity.getList("Items").orElse(null);
        if (items == null) return totals;

        for (int i = 0; i < items.size(); i++) {
            NbtCompound entry = items.getCompound(i).orElse(null);
            if (entry == null) continue;

            String id = entry.getString("id", "");
            if (id.isEmpty()) continue;

            int count = 1;
            if (entry.contains("count")) count = entry.getInt("count", 1);
            else if (entry.contains("Count")) count = entry.getByte("Count", (byte) 1);

            Identifier identifier = Identifier.tryParse(id);
            if (identifier == null) continue;

            Item item = Registries.ITEM.get(identifier);
            if (item == Items.AIR) continue;

            totals.merge(item, count, Integer::sum);
        }
        return totals;
    }

    public static List<ItemStack> stacks(ItemStack shulker) {
        List<ItemStack> result = new ArrayList<>();

        ContainerComponent container = shulker.get(DataComponentTypes.CONTAINER);
        if (container != null) {
            container.stream().forEach(inner -> {
                if (!inner.isEmpty()) result.add(inner.copy());
            });
            if (!result.isEmpty()) return result;
        }

        for (Map.Entry<Item, Integer> entry : totals(shulker).entrySet()) {
            Item item = entry.getKey();
            int max = Math.max(1, item.getMaxCount());
            int remaining = entry.getValue();
            while (remaining > 0) {
                int size = Math.min(max, remaining);
                result.add(new ItemStack(item, size));
                remaining -= size;
            }
        }
        return result;
    }
}
