package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.bot.BotContext;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.Sensors;
import com.hunterbuddy.behavior.farmer.FarmerCatalog;
import com.hunterbuddy.behavior.farmer.FarmerProfile;
import com.hunterbuddy.behavior.farmer.MissionJournal;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.InventoryLayout;
import com.hunterbuddy.behavior.mc.ShulkerSignature;
import com.hunterbuddy.behavior.mc.TemporaryEquipment;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.If;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.Selector;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.SwapArmor;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.behavior.trees.ContainerTree;
import com.hunterbuddy.behavior.trees.EquipmentTree;
import com.hunterbuddy.util.GliderSwap;
import java.util.ArrayList;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

public record InventoryTask(String scenario, BlockPos chestPos, BlockPos shulkerPos, ItemStack material, BaritoneBridge baritone) implements LabTask {
    @Override
    public String name() { return scenario; }
    @Override
    public String target() { return chestPos.toShortString(); }
    @Override
    public Set<Resource> resources() { return Set.of(Resource.INVENTORY, Resource.SLOT, Resource.INTERACT, Resource.MINING, Resource.BARITONE); }
    @Override
    public void initialize(BotContext context) {}

    @Override
    public Node build(BotContext context, WorldReader world, Outputs outputs) {
        MissionJournal journal = new MissionJournal(8);
        FarmerProfile profile = FarmerCatalog.load().profile("sand").orElseGet(() -> new FarmerProfile("lab", "lab", Set.of(), Set.of(), "pickaxe", "surface", java.util.List.of()));
        InventoryLayout layout = new InventoryLayout(world, profile);
        if (scenario.startsWith("armor")) {
            boolean elytra = scenario.endsWith("elytra");
            Predicate<ItemStack> filter = elytra ? GliderSwap::isGlider : GliderSwap::isChestPiece;
            return new Timeout(600, new Sequence("armor-lab",
                new InventoryMilestone("armor-preflight", ctx -> {
                    if (!filter.test(world.client().player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST)) && layout.find(filter, 0) < 0)
                        throw new IllegalStateException("requested armor missing");
                }),
                new Selector("armor-already-worn", new Condition("requested armor worn", sensors -> filter.test(world.client().player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST))),
                    new Sequence("equip-requested-armor", EquipmentTree.prepareHeld(layout, filter, 8, outputs, journal),
                        new WithLease(Set.of(Resource.SLOT, Resource.INTERACT), LeasePolicy.WAIT, new SwapArmor(elytra, world, outputs, journal))))));
        }
        TemporaryEquipment chest = new TemporaryEquipment("lab-ender-chest", chestPos, true, world, journal);
        TemporaryEquipment shulker = new TemporaryEquipment("lab-shulker", shulkerPos, false, world, journal);
        ArrayList<Node> steps = new ArrayList<>();
        steps.add(new InventoryMilestone("equipment-preflight", ctx -> {
            if (layout.find(InventoryLayout::silkPickaxe, 0) < 0) throw new IllegalStateException("Silk Touch pickaxe required before any placement");
            if (layout.find(stack -> stack.isOf(Items.ENDER_CHEST), 0) < 0) throw new IllegalStateException("ender chest missing");
            if (layout.freeSlot() < 0) throw new IllegalStateException("free inventory slot required");
            String refusal = outputs.mining().refusal();
            if (refusal != null) throw new IllegalStateException(refusal);
            if (scenario.equals("shulker") && (material.isEmpty() || material.isOf(Items.ENDER_CHEST) || ShulkerSignature.isShulker(material) || InventoryLayout.silkPickaxe(material)))
                throw new IllegalStateException("main hand must contain material to deposit");
        }));
        steps.add(EquipmentTree.prepareHeld(layout, InventoryLayout::silkPickaxe, 1, outputs, journal));
        steps.add(EquipmentTree.prepareHeld(layout, stack -> stack.isOf(Items.ENDER_CHEST), 4, outputs, journal));
        steps.add(EquipmentTree.place(chest, world, outputs));
        steps.add(ContainerTree.open(chest, world, outputs, journal));
        if (scenario.equals("shulker")) {
            steps.add(new InventoryMilestone("select-empty-shulker", ctx -> {
                ItemStack chosen = outputs.inventory().containerContents().stream().filter(ShulkerSignature::isShulker)
                    .filter(stack -> ShulkerSignature.of(stack, world).empty()).findFirst().orElseThrow(() -> new IllegalStateException("no empty shulker in ender chest"));
                shulker.signature(ShulkerSignature.of(chosen, world));
                shulker.milestone(ctx, "expected-before-taking");
            }));
            steps.add(ContainerTree.transfer(outputs, () -> outputs.inventory().takeShulker(shulker.signature()), journal, "take empty shulker"));
            steps.add(new InventoryMilestone("shulker-taken", ctx -> shulker.milestone(ctx, "transferred-from-ender-chest")));
            steps.add(ContainerTree.close(outputs));
            steps.add(EquipmentTree.prepareHeld(layout, stack -> shulker.signature().matches(stack), 8, outputs, journal));
            steps.add(EquipmentTree.place(shulker, world, outputs));
            steps.add(ContainerTree.open(shulker, world, outputs, journal));
            steps.add(ContainerTree.deposit(outputs, stack -> ItemStack.areItemsAndComponentsEqual(stack, material), journal, "shulker material"));
            steps.add(new InventoryMilestone("confirmed-shulker-content", ctx -> shulker.contents(ctx, outputs.inventory())));
            steps.add(ContainerTree.close(outputs));
            steps.add(EquipmentTree.recover(shulker, world, baritone, outputs, journal));
            steps.add(ContainerTree.open(chest, world, outputs, journal));
            steps.add(ContainerTree.transfer(outputs, () -> outputs.inventory().storeShulker(shulker.signature()), journal, "store verified shulker"));
            steps.add(new InventoryMilestone("shulker-stored", ctx -> shulker.milestone(ctx, "stored-in-ender-chest")));
        }
        steps.add(ContainerTree.close(outputs));
        steps.add(EquipmentTree.recover(chest, world, baritone, outputs, journal));
        return new Timeout(4_800, new Sequence("inventory-lab", steps.toArray(Node[]::new)).resumable());
    }
}
