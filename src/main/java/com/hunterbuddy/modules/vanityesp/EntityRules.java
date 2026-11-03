package com.hunterbuddy.modules.vanityesp;

import net.minecraft.block.Block;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

final class EntityRules {
    private static final double MAP_FRAME_EXPAND_XY = 0.12;
    private static final double MAP_FRAME_EXPAND_Z = 0.01;
    private static final double FRAME_TRACER_HEIGHT = 0.5;
    private static final double ENDERMAN_TRACER_HEIGHT = 1.0;
    private static final double ORB_TRACER_HEIGHT = 0.2;

    private EntityRules() {
    }

    static EntityRule mapFrames() {
        return new EntityRule(entity -> holdsItem(entity, Items.FILLED_MAP),
            entity -> entity.getBoundingBox().expand(MAP_FRAME_EXPAND_XY, MAP_FRAME_EXPAND_XY, MAP_FRAME_EXPAND_Z),
            entity -> entity.getBoundingBox().expand(MAP_FRAME_EXPAND_XY, MAP_FRAME_EXPAND_XY, MAP_FRAME_EXPAND_Z).getCenter());
    }

    static EntityRule shulkerFrames() {
        return new EntityRule(EntityRules::holdsShulkerBox, Entity::getBoundingBox, entity -> above(entity, FRAME_TRACER_HEIGHT));
    }

    static EntityRule endermenHoldingBlocks() {
        return new EntityRule(entity -> entity instanceof EndermanEntity enderman && enderman.getCarriedBlock() != null,
            Entity::getBoundingBox, entity -> above(entity, ENDERMAN_TRACER_HEIGHT));
    }

    static EntityRule experienceOrbs() {
        return new EntityRule(entity -> entity instanceof ExperienceOrbEntity, Entity::getBoundingBox,
            entity -> above(entity, ORB_TRACER_HEIGHT));
    }

    private static boolean holdsItem(Entity entity, Item item) {
        return entity instanceof ItemFrameEntity frame && frame.getHeldItemStack().isOf(item);
    }

    private static boolean holdsShulkerBox(Entity entity) {
        if (!(entity instanceof ItemFrameEntity frame)) return false;
        ItemStack held = frame.getHeldItemStack();
        return Block.getBlockFromItem(held.getItem()) instanceof ShulkerBoxBlock;
    }

    private static Vec3d above(Entity entity, double height) {
        return entity.getEntityPos().add(0.0, height, 0.0);
    }
}
