package com.hunterbuddy.modules.signs;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.DyeItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class SignStyler {
    private static final int MIN_STEP_TICKS = 5;
    private static final int MAX_STEPS = 3;

    private BlockPos pos;
    private boolean front;
    private SignStyle target;
    private int stepTicks;
    private int ticksLeft;
    private int stepsLeft;

    public void queue(SignRecord original, boolean front, int delayTicks) {
        SignStyle style = original.style(front);
        if (style == null) return;
        pos = original.key().pos();
        this.front = front;
        target = style;
        stepTicks = Math.max(delayTicks, MIN_STEP_TICKS);
        ticksLeft = delayTicks + stepTicks;
        stepsLeft = MAX_STEPS;
    }

    public boolean tick(MinecraftClient mc) {
        if (target == null || ticksLeft-- > 0) return false;
        if (!(mc.world.getBlockEntity(pos) instanceof SignBlockEntity sign) || sign.isPlayerFacingFront(mc.player) != front) {
            clear();
            return false;
        }
        Item item = missingItem(sign.getText(front));
        FindItemResult found = item == null ? null : InvUtils.find(item);
        if (found == null || !found.found() || !(found.isHotbar() || found.isOffhand()) || stepsLeft-- <= 0) {
            clear();
            return false;
        }
        apply(mc, found);
        ticksLeft = stepTicks;
        return true;
    }

    public void clear() {
        target = null;
    }

    private Item missingItem(SignText current) {
        DyeColor wanted = target.dye();
        if (wanted != null && current.getColor() != wanted) return DyeItem.byColor(wanted);
        if (target.glowing() != current.isGlowing()) return target.glowing() ? Items.GLOW_INK_SAC : Items.INK_SAC;
        return null;
    }

    private void apply(MinecraftClient mc, FindItemResult found) {
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
        Hand hand = found.getHand();
        if (hand != null) {
            mc.interactionManager.interactBlock(mc.player, hand, hit);
            return;
        }
        InvUtils.swap(found.slot(), true);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        InvUtils.swapBack();
    }
}
