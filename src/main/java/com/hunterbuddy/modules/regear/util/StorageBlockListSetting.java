package com.hunterbuddy.modules.regear.util;

import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import meteordevelopment.meteorclient.settings.Setting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StorageBlockListSetting extends Setting<List<BlockEntityType<?>>> {
    public static final List<BlockEntityType<?>> STORAGE_BLOCKS = List.of(
        BlockEntityType.BARREL,
        BlockEntityType.CHEST,
        BlockEntityType.TRAPPED_CHEST,
        BlockEntityType.SHULKER_BOX,
        BlockEntityType.ENDER_CHEST,
        BlockEntityType.FURNACE,
        BlockEntityType.BLAST_FURNACE,
        BlockEntityType.SMOKER,
        BlockEntityType.HOPPER,
        BlockEntityType.DISPENSER,
        BlockEntityType.DROPPER,
        BlockEntityType.CRAFTER
    );

    public StorageBlockListSetting(String name, String description, List<BlockEntityType<?>> defaultValue) {
        super(name, description, defaultValue, null, null, null);
    }

    @Override
    protected List<BlockEntityType<?>> parseImpl(String string) {
        return new ArrayList<>(defaultValue);
    }

    @Override
    protected boolean isValueValid(List<BlockEntityType<?>> value) {
        return true;
    }

    @Override
    protected NbtCompound save(NbtCompound tag) {
        NbtList list = new NbtList();
        for (BlockEntityType<?> type : get()) {
            list.add(NbtString.of(Registries.BLOCK_ENTITY_TYPE.getId(type).toString()));
        }
        tag.put("blocks", list);
        return tag;
    }

    @Override
    protected List<BlockEntityType<?>> load(NbtCompound tag) {
        List<BlockEntityType<?>> result = new ArrayList<>();
        if (tag.contains("blocks")) {
            NbtList list = tag.getList("blocks").orElse(new NbtList());
            for (NbtElement el : list) {
                String id = el.asString().orElse("");
                BlockEntityType<?> type = Registries.BLOCK_ENTITY_TYPE.get(net.minecraft.util.Identifier.tryParse(id));
                if (type != null) result.add(type);
            }
        }
        return result.isEmpty() ? defaultValue : result;
    }

    public static class Builder extends Setting.SettingBuilder<Builder, List<BlockEntityType<?>>, StorageBlockListSetting> {
        public Builder() {
            super(null);
        }

        @Override
        public StorageBlockListSetting build() {
            return new StorageBlockListSetting(name, description, defaultValue);
        }
    }
}