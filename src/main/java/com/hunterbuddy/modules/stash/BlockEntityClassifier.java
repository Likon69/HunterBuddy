package com.hunterbuddy.modules.stash;

import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.CrafterBlockEntity;
import net.minecraft.block.entity.DecoratedPotBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.HangingSignBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;

import java.util.List;
import java.util.function.Predicate;

public final class BlockEntityClassifier {
    private static final List<Rule> RULES = List.of(
        rule(StashType.TRAPPED_CHEST, TrappedChestBlockEntity.class),
        rule(StashType.CHEST, ChestBlockEntity.class),
        rule(StashType.BARREL, BarrelBlockEntity.class),
        rule(StashType.SHULKER, ShulkerBoxBlockEntity.class),
        rule(StashType.ENDER_CHEST, EnderChestBlockEntity.class),
        rule(StashType.FURNACE, AbstractFurnaceBlockEntity.class),
        rule(StashType.DISPENSER, DispenserBlockEntity.class),
        rule(StashType.HOPPER, HopperBlockEntity.class),
        rule(StashType.BREWING_STAND, BrewingStandBlockEntity.class),
        rule(StashType.CRAFTER, CrafterBlockEntity.class),
        rule(StashType.DECORATED_POT, DecoratedPotBlockEntity.class),
        rule(StashType.BANNER, BannerBlockEntity.class),
        rule(StashType.HANGING_SIGN, HangingSignBlockEntity.class),
        rule(StashType.SIGN, SignBlockEntity.class));

    private final StashDetectionSettings detection;

    public BlockEntityClassifier(StashDetectionSettings detection) {
        this.detection = detection;
    }

    public StashType classify(BlockEntity entity) {
        for (Rule rule : RULES) {
            if (detection.detects(rule.type()) && rule.matches().test(entity)) return rule.type();
        }
        return null;
    }

    private static Rule rule(StashType type, Class<? extends BlockEntity> entityClass) {
        return new Rule(type, entityClass::isInstance);
    }

    private record Rule(StashType type, Predicate<BlockEntity> matches) {
    }
}
