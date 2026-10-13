package com.hunterbuddy.behavior.mc;

import com.mojang.serialization.JsonOps;
import java.util.List;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

public final class ShulkerSignature {
    private final String item;
    private final ContainerComponent contents;
    private final String encoded;

    private ShulkerSignature(String item, ContainerComponent contents, WorldReader world) {
        this.item = item;
        this.contents = ContainerComponent.fromStacks(contents.stream().toList());
        com.google.gson.JsonObject data = new com.google.gson.JsonObject();
        data.addProperty("item", item);
        data.add("contents", ContainerComponent.CODEC.encodeStart(world.client().world.getRegistryManager().getOps(JsonOps.INSTANCE), this.contents).getOrThrow());
        encoded = data.toString();
    }

    public static boolean isShulker(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock;
    }

    public static ShulkerSignature of(ItemStack stack, WorldReader world) {
        if (!isShulker(stack)) throw new IllegalArgumentException("shulker required");
        return new ShulkerSignature(Registries.ITEM.getId(stack.getItem()).toString(), stack.getOrDefault(DataComponentTypes.CONTAINER, ContainerComponent.DEFAULT), world);
    }

    public static ShulkerSignature decode(String encoded, WorldReader world) {
        com.google.gson.JsonObject data = com.google.gson.JsonParser.parseString(encoded).getAsJsonObject();
        return new ShulkerSignature(data.get("item").getAsString(), ContainerComponent.CODEC.parse(
            world.client().world.getRegistryManager().getOps(JsonOps.INSTANCE), data.get("contents")).getOrThrow(), world);
    }

    public List<ItemStack> contents() {
        net.minecraft.util.collection.DefaultedList<ItemStack> result = net.minecraft.util.collection.DefaultedList.ofSize(27, ItemStack.EMPTY);
        contents.copyTo(result);
        return List.copyOf(result);
    }

    public ShulkerSignature withContents(List<ItemStack> stacks, WorldReader world) {
        if (stacks.size() != 27) throw new IllegalArgumentException("27 confirmed shulker slots required");
        return new ShulkerSignature(item, ContainerComponent.fromStacks(stacks), world);
    }

    public boolean matches(ItemStack stack) {
        return isShulker(stack) && Registries.ITEM.getId(stack.getItem()).toString().equals(item)
            && contents.equals(ContainerComponent.fromStacks(stack.getOrDefault(DataComponentTypes.CONTAINER, ContainerComponent.DEFAULT).stream().toList()));
    }

    public boolean empty() { return contents.streamNonEmpty().findAny().isEmpty(); }
    public boolean same(ShulkerSignature other) { return item.equals(other.item) && contents.equals(other.contents); }
    public boolean matchesContents(List<ItemStack> stacks) { return stacks.size() == 27 && contents.equals(ContainerComponent.fromStacks(stacks)); }
    public boolean matchesBlock(net.minecraft.block.BlockState state) {
        return state.getBlock() instanceof ShulkerBoxBlock && Registries.ITEM.getId(state.getBlock().asItem()).toString().equals(item);
    }
    public String encoded() { return encoded; }
}
