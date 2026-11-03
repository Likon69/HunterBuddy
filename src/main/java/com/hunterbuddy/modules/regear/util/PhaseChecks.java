package com.hunterbuddy.modules.regear.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

final class PhaseChecks {
    private PhaseChecks() {
    }

    static boolean isInsideBlocks() {
        Box box = playerBox();
        if (box == null) return false;
        BlockPos.Mutable cell = new BlockPos.Mutable();
        for (int x = MathHelper.floor(box.minX); x <= MathHelper.floor(box.maxX); x++) {
            for (int y = MathHelper.floor(box.minY); y <= MathHelper.floor(box.maxY); y++) {
                if (isColumnSolid(cell, box, x, y)) return true;
            }
        }
        return false;
    }

    private static boolean isColumnSolid(BlockPos.Mutable cell, Box box, int x, int y) {
        for (int z = MathHelper.floor(box.minZ); z <= MathHelper.floor(box.maxZ); z++) {
            if (isSolid(cell.set(x, y, z))) return true;
        }
        return false;
    }

    static boolean isFeetAndHeadBlocked() {
        Box box = playerBox();
        if (box == null) return false;
        int feetY = MathHelper.floor(box.minY);
        int headY = MathHelper.floor(box.maxY);
        boolean feetBlocked = false;
        boolean headBlocked = false;
        BlockPos.Mutable cell = new BlockPos.Mutable();
        for (int x = MathHelper.floor(box.minX); x <= MathHelper.floor(box.maxX); x++) {
            for (int z = MathHelper.floor(box.minZ); z <= MathHelper.floor(box.maxZ); z++) {
                feetBlocked |= isSolid(cell.set(x, feetY, z));
                headBlocked |= isSolid(cell.set(x, headY, z));
            }
        }
        return feetBlocked && headBlocked;
    }

    private static Box playerBox() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return null;
        return mc.player.getBoundingBox();
    }

    private static boolean isSolid(BlockPos cell) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return !mc.world.getBlockState(cell).getCollisionShape(mc.world, cell).isEmpty();
    }
}
