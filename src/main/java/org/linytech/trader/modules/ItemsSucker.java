package org.linytech.trader.modules;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class ItemsSucker extends Module {
    private final Setting<Integer> range;
    private final SettingGroup sgGeneral;
    private final HashSet<BlockPos> itemPositions = new HashSet<>();


    private final Setting<List<Item>> itemFilter;
    private Timer timer;

    public ItemsSucker(Category category) {
        super(category, "Items Sucker", "Automatically picks up items on the ground using Baritone.");

        sgGeneral = settings.createGroup("General");
        range = sgGeneral.add(new IntSetting.Builder()
            .name("range")
            .description("Range within which items are collected.")
            .defaultValue(100)
            .sliderMax(100)
            .min(1)
            .max(100)
            .build()
        );
        itemFilter = sgGeneral.add(new ItemListSetting.Builder()
            .name("item-filter")
            .description("Only collect items with these names.")
            //.defaultValue(Arrays.asList(Items.DIAMOND))
            .build()
        );
    }


    @Override
    public void onActivate() {
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                collectItems();
            }
        }, 0, 1000);
    }

    @Override
    public void onDeactivate() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(null);
    }

    private void collectItems() {
        itemPositions.clear();
        List<Item> filters = itemFilter.get();
        for (ItemEntity item : mc.world.getEntitiesByClass(ItemEntity.class, mc.player.getBoundingBox().expand(range.get()), entity -> true)) {
            if (filters.contains(item.getStack().getItem())) {
                itemPositions.add(item.getBlockPos());
            }
        }

        for (BlockPos pos : itemPositions) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(pos));
        }
    }
}
