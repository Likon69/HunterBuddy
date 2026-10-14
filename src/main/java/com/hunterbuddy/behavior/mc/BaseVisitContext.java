package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.BaseVisitFacts;
import com.hunterbuddy.behavior.farmer.FarmerProfile;
import com.hunterbuddy.behavior.farmer.FarmerSettings;
import com.hunterbuddy.behavior.farmer.FarmerStepConfig;
import com.hunterbuddy.behavior.farmer.FarmerZone;
import com.hunterbuddy.behavior.farmer.LogisticsFacts;
import com.hunterbuddy.behavior.farmer.LogisticsMemory;
import com.hunterbuddy.behavior.farmer.LogisticsRoles;
import com.hunterbuddy.behavior.farmer.MissionJournal;
import com.hunterbuddy.behavior.resource.Outputs;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

public final class BaseVisitContext {
    public final LogisticsContext logistics;
    public final FarmerStepConfig step;
    public final FarmerSettings settings;
    public final BaseVisitMemory memory;
    public BaseVisitFacts facts;
    public int wearBeforeRegear;

    public BaseVisitContext(FarmerStepConfig step, FarmerProfile profile, Supplier<FarmerZone> zone,
                            FarmerSettings settings, WorldReader world, BaritoneBridge baritone,
                            Outputs outputs, MissionJournal journal) {
        this.step = step;
        this.settings = settings;
        memory = new BaseVisitMemory(journal);
        logistics = new LogisticsContext(() -> profile, zone, false, () -> false, () -> false,
            () -> step.index() - 1, world, baritone, outputs, journal);
        logistics.profile = profile;
        logistics.equipmentPrefix = "base";
    }

    public void initialize(TickContext ctx) {
        current = ctx;
        facts = memory.load(ctx);
        logistics.memory = new LogisticsMemory(logistics.mission, logistics.mission.owner(ctx), "base-logistics-facts");
        logistics.facts = logistics.memory.load(ctx);
        if (facts == null || facts.recoveredAt != null) {
            if (LogisticsMemory.active(ctx, logistics.mission)) {
                throw new IllegalStateException("storage active: recover tracked equipment before visiting base");
            }
            facts = new BaseVisitFacts();
            facts.world = logistics.world.storageKey();
            facts.step = step.index() - 1;
            facts.profile = logistics.profile.key();
            facts.target = step.shulkerTarget();
            facts.departure = LogisticsFacts.coordinates(logistics.world.feet());
            facts.delivery = LogisticsFacts.coordinates(step.deliveryChest());
            facts.returns = LogisticsFacts.coordinates(settings.returnChest());
            facts.empties = LogisticsFacts.coordinates(settings.emptyShulkersChest());
            facts.supply = LogisticsFacts.coordinates(settings.resupplyChest());
            logistics.facts = new LogisticsFacts();
            logistics.facts.profile = facts.profile;
            logistics.facts.dimension = logistics.world.identity().dimensionKey();
            logistics.facts.step = facts.step;
            logistics.facts.zone = logistics.zones.get().corners().stream().map(LogisticsFacts::coordinates).toList();
            for (String role : List.of(LogisticsRoles.CHEST, LogisticsRoles.MATERIAL, LogisticsRoles.MISCELLANEOUS, LogisticsRoles.RESERVE)) {
                logistics.mission.equipment(ctx, logistics.equipmentName(role), "");
                logistics.forget(role);
            }
            save(ctx, "base visit requested");
        }
        if (!facts.world.equals(logistics.world.storageKey()) || facts.step != step.index() - 1
            || !facts.profile.equals(logistics.profile.key()) || logistics.facts == null) {
            throw new IllegalStateException("visit recorded for another step or world");
        }
        logistics.layout = new InventoryLayout(logistics.world);
        logistics.requiredFlightWear = facts.wearBudget;
        logistics.restore(ctx);
        logistics.outputs.mining().breakRule(pos -> false);
        String refusal = logistics.outputs.mining().temporaryRefusal();
        if (refusal != null) {
            throw new IllegalStateException(refusal);
        }
        if (logistics.world.countMatching(InventoryLayout::silkPickaxe) == 0) {
            throw new IllegalStateException("Silk Touch pickaxe missing before base visit");
        }
        if (logistics.facts.site == null && logistics.world.count(Items.ENDER_CHEST) == 0) {
            throw new IllegalStateException("ender chest missing before base visit");
        }
        BaseStockPlan.measure(ctx, this);
    }

    public BlockPos position(int[] value) {
        return LogisticsFacts.position(value);
    }

    public boolean configuredChest(BlockPos pos) {
        return List.of(position(facts.delivery), position(facts.returns), position(facts.empties), position(facts.supply)).contains(pos);
    }

    public void save(TickContext ctx, String label) {
        memory.save(ctx, facts);
        logistics.save(ctx, "base: " + label);
    }

    public List<ItemStack> enderContents() {
        if (logistics.facts.enderContents.isEmpty()) {
            throw new IllegalStateException("ender chest not observed");
        }
        return LogisticsInventory.decode(logistics.facts.enderContents, 27, logistics.world);
    }

    public boolean roleSlot(int slot, String role) {
        String encoded = logistics.mission.equipment(current, "logistics-" + role);
        String recorded = logistics.mission.equipment(current, "logistics-" + role + "-slot");
        return !encoded.isEmpty() && recorded.equals(Integer.toString(slot))
            && ShulkerSignature.decode(encoded, logistics.world).matches(enderContents().get(slot));
    }

    private TickContext current;

    public void observe(TickContext ctx) {
        current = ctx;
        LogisticsTransferReceipt.observeEnderChest(ctx, logistics);
    }

    public long delivered() {
        return logistics.mission.delivered(current, facts.step);
    }

    public int fullSlot() {
        List<ItemStack> contents = enderContents();
        for (int i = 0; i < contents.size(); i++) {
            ItemStack stack = contents.get(i);
            if (!roleSlot(i, LogisticsRoles.RESERVE) && !roleSlot(i, LogisticsRoles.MISCELLANEOUS) && ShulkerSignature.isShulker(stack)
                && LogisticsInventory.full(ShulkerSignature.of(stack, logistics.world), logistics.profile)) return i;
        }
        return -1;
    }

    public int emptySlot() {
        List<ItemStack> contents = enderContents();
        for (int i = 0; i < contents.size(); i++) {
            ItemStack stack = contents.get(i);
            if (!roleSlot(i, LogisticsRoles.RESERVE) && !roleSlot(i, LogisticsRoles.MISCELLANEOUS) && ShulkerSignature.isShulker(stack)
                && ShulkerSignature.of(stack, logistics.world).empty()) return i;
        }
        return -1;
    }

    public int emptyCount() {
        int count = 0;
        List<ItemStack> contents = enderContents();
        for (int i = 0; i < contents.size(); i++) {
            ItemStack stack = contents.get(i);
            if (!roleSlot(i, LogisticsRoles.RESERVE) && !roleSlot(i, LogisticsRoles.MISCELLANEOUS) && ShulkerSignature.isShulker(stack)
                && ShulkerSignature.of(stack, logistics.world).empty()) count++;
        }
        return count;
    }
}
