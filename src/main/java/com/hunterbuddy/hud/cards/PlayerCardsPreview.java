package com.hunterbuddy.hud.cards;

import java.util.List;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public final class PlayerCardsPreview {
    private PlayerCardsPreview() {
    }

    public static List<PlayerTrack> tracks(long now) {
        SkinTextures skin = DefaultSkinHelper.getSteve();
        return List.of(
            PlayerTrack.preview("Fit2Win", skin, now, 180.0, 12.0,
                Items.NETHERITE_HELMET.getDefaultStack(), Items.ELYTRA.getDefaultStack(),
                Items.NETHERITE_LEGGINGS.getDefaultStack(), Items.NETHERITE_BOOTS.getDefaultStack(),
                Items.NETHERITE_SWORD.getDefaultStack(), Items.TOTEM_OF_UNDYING.getDefaultStack()),
            PlayerTrack.preview("xX_Hunt3r", skin, now, 410.0, -6.0,
                ItemStack.EMPTY, Items.ELYTRA.getDefaultStack(), ItemStack.EMPTY, ItemStack.EMPTY,
                Items.NETHERITE_PICKAXE.getDefaultStack()));
    }
}
