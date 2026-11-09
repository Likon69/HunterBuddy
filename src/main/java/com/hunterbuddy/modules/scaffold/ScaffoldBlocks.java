package com.hunterbuddy.modules.scaffold;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;

final class ScaffoldBlocks {
    private static final FindItemResult NOTHING = new FindItemResult(-1, 0);

    private ScaffoldBlocks() {
    }

    static FindItemResult forRegear(RegearMode mode, boolean air) {
        if (!mode.hasFixedSlot()) return air ? InvUtils.find(ScaffoldBlocks::isRegearBlock, 0, 8) : InvUtils.findInHotbar(ScaffoldBlocks::isRegearBlock);
        ItemStack stack = MinecraftClient.getInstance().player.getInventory().getStack(mode.hotbarSlot());
        if (!isRegearBlock(stack)) return NOTHING;
        return new FindItemResult(mode.hotbarSlot(), stack.getCount());
    }

    static FindItemResult forTarget(BlockPos target, ScaffoldSettings settings, boolean air) {
        return air ? InvUtils.find(stack -> isPlaceableAt(stack, target, settings), 0, 8)
            : InvUtils.findInHotbar(stack -> isPlaceableAt(stack, target, settings));
    }

    private static boolean isRegearBlock(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem item)) return false;
        Block block = item.getBlock();
        return block != Blocks.ENDER_CHEST
            && block.getDefaultState().isSolidBlock(MinecraftClient.getInstance().world, BlockPos.ORIGIN);
    }

    private static boolean isPlaceableAt(ItemStack stack, BlockPos target, ScaffoldSettings settings) {
        if (!(stack.getItem() instanceof BlockItem item)) return false;
        Block block = item.getBlock();
        return isAllowed(block, settings) && isFullCubeAt(block, target) && !fallsThrough(block, target);
    }

    private static boolean isAllowed(Block block, ScaffoldSettings settings) {
        boolean listed = settings.blocks.get().contains(block);
        return settings.blocksFilter.get() == ScaffoldSettings.ListMode.Whitelist ? listed : !listed;
    }

    private static boolean isFullCubeAt(Block block, BlockPos target) {
        BlockState state = block.getDefaultState();
        return Block.isShapeFullCube(state.getCollisionShape(MinecraftClient.getInstance().world, target));
    }

    private static boolean fallsThrough(Block block, BlockPos target) {
        BlockState atTarget = MinecraftClient.getInstance().world.getBlockState(target);
        return block instanceof FallingBlock && FallingBlock.canFallThrough(atTarget);
    }
}
