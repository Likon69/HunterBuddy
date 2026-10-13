package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.MissionJournal;
import java.util.function.Predicate;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

public final class TemporaryEquipment {
    private final String name;
    private final BlockPos pos;
    private final boolean enderChest;
    private final WorldReader world;
    private final MissionJournal journal;
    private ShulkerSignature signature;
    private WorldIdentity placedWorld;
    private int beforePickup;
    private boolean placed;
    private boolean broken;
    private boolean placementRequested;
    private boolean breakRequested;
    private boolean pickupConfirmed;

    public TemporaryEquipment(String name, BlockPos pos, boolean enderChest, WorldReader world, MissionJournal journal) {
        this.name = name;
        this.pos = pos.toImmutable();
        this.enderChest = enderChest;
        this.world = world;
        this.journal = journal;
    }

    public BlockPos pos() {
        return pos;
    }

    public boolean enderChest() {
        return enderChest;
    }

    public ShulkerSignature signature() {
        if (signature == null) throw new IllegalStateException("expected shulker signature missing");
        return signature;
    }

    public void signature(ShulkerSignature expected) {
        signature = expected;
    }

    public Predicate<ItemStack> filter() {
        return enderChest ? stack -> stack.isOf(Items.ENDER_CHEST) : signature()::matches;
    }

    public int beforePickup() {
        return beforePickup;
    }

    public boolean tracked() {
        return placed && placedWorld != null && placedWorld.equals(world.identity());
    }

    public boolean broken() {
        return broken;
    }

    public boolean placementRequested() {
        return placementRequested;
    }

    public boolean breakRequested() {
        return breakRequested;
    }

    public boolean pickedUpConfirmed() {
        return pickupConfirmed;
    }

    public void restore(TickContext ctx) {
        String saved = journal.equipment(ctx, name);
        if (saved.isEmpty()) return;
        com.google.gson.JsonObject state = com.google.gson.JsonParser.parseString(saved).getAsJsonObject();
        if (state.get("x").getAsInt() != pos.getX() || state.get("y").getAsInt() != pos.getY() || state.get("z").getAsInt() != pos.getZ()
            || !state.get("dimension").getAsString().equals(world.identity().dimensionKey())) throw new
                IllegalStateException("temporary equipment journal position or dimension changed");
        if (!enderChest) signature = ShulkerSignature.decode(state.get("signature").toString(), world);
        beforePickup = state.get("baseline").getAsInt();
        placed = state.get("placed").getAsBoolean();
        broken = state.get("broken").getAsBoolean();
        placementRequested = state.has("placementRequested") && state.get("placementRequested").getAsBoolean();
        breakRequested = state.has("breakRequested") && state.get("breakRequested").getAsBoolean();
        pickupConfirmed = state.has("pickupConfirmed") && state.get("pickupConfirmed").getAsBoolean();
        placedWorld = world.identity();
    }

    public void preparePlacement(TickContext ctx) {
        placementRequested = true;
        pickupConfirmed = false;
        beforePickup = Math.max(0, world.countMatching(filter()) - 1);
        milestone(ctx, "placing");
    }

    public void prepareBreak(TickContext ctx, InventoryBridge inventory) {
        breakRequested = true;
        inventory.observePlayer();
        beforePickup = world.countMatching(filter());
        milestone(ctx, "breaking");
    }

    public void placed(TickContext ctx, InventoryBridge inventory) {
        placed = true;
        placementRequested = true;
        pickupConfirmed = false;
        breakRequested = false;
        broken = false;
        placedWorld = world.identity();
        inventory.observePlayer();
        beforePickup = world.countMatching(filter());
        milestone(ctx, "placed");
    }

    public void contents(TickContext ctx, InventoryBridge inventory) {
        signature = signature().withContents(inventory.containerContents(), world);
        inventory.observePlayer();
        beforePickup = world.countMatching(filter());
        milestone(ctx, "transferred");
    }

    public void broken(TickContext ctx) {
        broken = true;
        milestone(ctx, "broken");
    }

    public void pickedUp(TickContext ctx) {
        if (world.countMatching(filter()) != beforePickup + 1) throw new
            IllegalStateException("temporary equipment recovery count is ambiguous");
        placed = false;
        pickupConfirmed = true;
        milestone(ctx, "picked-up");
    }

    public void milestone(TickContext ctx, String event) {
        com.google.gson.JsonObject state = new com.google.gson.JsonObject();
        state.addProperty("placementRequested", placementRequested);
        state.addProperty("breakRequested", breakRequested);
        state.addProperty("pickupConfirmed", pickupConfirmed);
        state.addProperty("x", pos.getX());
        state.addProperty("y", pos.getY());
        state.addProperty("z", pos.getZ());
        state.addProperty("dimension", world.identity().dimensionKey());
        state.addProperty("enderChest", enderChest);
        if (!enderChest) state.add("signature", com.google.gson.JsonParser.parseString(signature().encoded()));
        state.addProperty("baseline", beforePickup);
        state.addProperty("placed", placed);
        state.addProperty("broken", broken);
        journal.equipment(ctx, name, state.toString());
        journal.milestone(ctx, name + " " + event + " at=" + pos.toShortString());
    }
}
