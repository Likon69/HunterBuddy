/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package org.linytech.trader.modules.tradingsystem.utilites;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.village.VillagerProfession;

import java.util.Comparator;
import java.util.List;

public class TraderUtils {
    public static VillagerEntity getNearestCleric(PlayerEntity player, List<Integer> blocked, int range) {
        return player.getWorld().getEntitiesByClass(
                VillagerEntity.class,
                new Box(player.getBlockPos()).expand(range),
                (villager) -> {
                    if (blocked.contains(villager.getId())) {
                        return false;
                    }

                    return villager.getVillagerData().getProfession().equals(VillagerProfession.CLERIC);
                }
            )
            .parallelStream()
            .min(Comparator.comparingDouble(villager -> villager.distanceTo(player)))
            .orElse(null);
    }
}
