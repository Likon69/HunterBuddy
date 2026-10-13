package com.hunterbuddy.behavior.mc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public final class MineDropReceipt {
    private final MineSiteContext mine;
    private final BlockPos origin;
    private boolean first = true;

    public MineDropReceipt(MineSiteContext mine, BlockPos origin) {
        this.mine = mine;
        this.origin = origin;
    }

    public void begin() {
        mine.facts.before = inventory();
        mine.facts.entities.clear();
        mine.facts.drops.clear();
        mine.facts.dropCounts.clear();
        mine.facts.receiptVersion = mine.outputs.inventory().playerVersion();
        for (ItemEntity entity : nearby(3)) mine.facts.entities.put(entityKey(entity), entity.getStack().getCount());
    }

    public boolean observe() {
        Map<String, Integer> visible = new HashMap<>();
        for (ItemEntity entity : nearby(3)) {
            int added = entity.getStack().getCount() - mine.facts.entities.getOrDefault(entityKey(entity), 0);
            if (added > 0) visible.merge(id(entity.getStack()), added, Integer::sum);
        }
        Map<String, Integer> carried = inventory();
        if (mine.outputs.inventory().playerVersion() > mine.facts.receiptVersion) {
            carried.forEach((item, count) -> {
                int added = Math.max(0, count - mine.facts.before.getOrDefault(item, 0));
                if (added > 0) visible.merge(item, added, Math::max);
            });
        }
        visible.forEach((item, count) -> {
            mine.facts.drops.add(item);
            mine.facts.dropCounts.merge(item, count, Math::max);
        });
        return !mine.facts.drops.isEmpty();
    }

    public boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && mine.facts.drops.contains(id(stack));
    }

    public int baseline() {
        return mine.facts.drops.stream().mapToInt(item -> mine.facts.before.getOrDefault(item, 0)).sum();
    }

    public int pickupBaseline() {
        int result = first ? baseline() : mine.outputs.inventory().confirmedCount(this::accepts);
        first = false;
        return result;
    }

    public boolean collected() {
        observe();
        if (mine.facts.drops.isEmpty() || nearby(8).stream().anyMatch(e -> accepts(e.getStack()))) return false;
        for (String item : mine.facts.drops) {
            int needed = mine.facts.before.getOrDefault(item, 0) + mine.facts.dropCounts.getOrDefault(item, 1);
            if (mine.outputs.inventory().confirmedCount(stack -> id(stack).equals(item)) < needed
                || mine.world.countMatching(stack -> !stack.isEmpty() && id(stack).equals(item)) < needed) return false;
        }
        return true;
    }

    public boolean safePickup(BlockPos feet) {
        return pickupPoint(feet) != null;
    }

    public BlockPos pickupPoint(BlockPos item) {
        BlockPos best = null;
        double score = Double.POSITIVE_INFINITY;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = -1; y <= 1; y++) {
                    BlockPos feet = item.add(x, y, z);
                    if (!mine.inside(feet) || !(MineGeometry.standing(mine.world, feet)
                        || MineGeometry.swimmable(mine.world, feet))) continue;
                    double cost = feet.getSquaredDistance(item) * 16 + feet.getSquaredDistance(mine.world.feet());
                    if (cost < score) {
                        score = cost;
                        best = feet;
                    }
                }
            }
        }
        return best;
    }

    private List<ItemEntity> nearby(int radius) {
        return mine.world.client().world.getEntitiesByClass(ItemEntity.class, new Box(origin).expand(radius), ItemEntity::isAlive);
    }

    private Map<String, Integer> inventory() {
        Map<String, Integer> result = new HashMap<>();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mine.world.client().player.getInventory().getStack(slot);
            if (!stack.isEmpty()) result.merge(id(stack), stack.getCount(), Integer::sum);
        }
        return result;
    }

    private String id(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).toString();
    }

    private String entityKey(ItemEntity entity) {
        return entity.getUuidAsString() + "/" + id(entity.getStack());
    }
}
