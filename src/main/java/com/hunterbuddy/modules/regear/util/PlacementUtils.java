package com.hunterbuddy.modules.regear.util;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Type;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;
import java.util.List;

/**
 * Reference parity: mlep.util.PlacementUtils. Helpers for placing blocks
 * (resistant blocks, hotbar swap, grim air-place, etc.).
 */
public class PlacementUtils {
    public static final double DEFAULT_REACH = 4.5;

    private static final List<Block> RESISTANT_BLOCKS = Arrays.asList(
        Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.ENDER_CHEST, Blocks.RESPAWN_ANCHOR, Blocks.ENCHANTING_TABLE, Blocks.ANVIL
    );

    public static int grimSequenceId() {
        return MeteorClient.mc.player.currentScreenHandler.getRevision() + 2;
    }

    public static void grimPlace(BlockHitResult hit) {
        var handler = MeteorClient.mc.getNetworkHandler();
        handler.sendPacket(new PlayerActionC2SPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
        handler.sendPacket(new PlayerInteractBlockC2SPacket(Hand.OFF_HAND, hit, grimSequenceId()));
        handler.sendPacket(new PlayerActionC2SPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
    }

    public static boolean placeHit(BlockHitResult hit, Hand hand, boolean swing) {
        if (MeteorClient.mc.player == null || MeteorClient.mc.interactionManager == null) return false;

        if (hit.isInsideBlock()) grimPlace(hit);
        else MeteorClient.mc.interactionManager.interactBlock(MeteorClient.mc.player, hand, hit);

        if (swing) {
            if (hand == Hand.MAIN_HAND) MeteorClient.mc.player.swingHand(Hand.MAIN_HAND);
            else MeteorClient.mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
        }
        return true;
    }

    public static BlockHitResult resolvePlaceHit(BlockPos pos, double reach) {
        Direction side = getPlaceSide(pos);
        if (side != null) {
            BlockPos neighbor = pos.offset(side);
            Vec3d hitVec = Vec3d.ofCenter(neighbor).add(Vec3d.of(side.getOpposite().getVector()).multiply(0.5));
            return new BlockHitResult(hitVec, side.getOpposite(), neighbor, false);
        }
        return getAirPlaceHit(pos, reach);
    }

    public static float[] rotationsForHit(BlockHitResult hit) {
        return RotationUtils.getRotationsTo(MeteorClient.mc.player.getEyePos(), hit.getPos());
    }

    public static boolean requestRotationForHit(BlockHitResult hit, boolean syncMovement) {
        float[] rotations = rotationsForHit(hit);
        RotationUtils rotationUtils = RotationUtils.getInstance();
        if (syncMovement) rotationUtils.setRotationFull(rotations[0], rotations[1]);
        else rotationUtils.setRotationSilent(rotations[0], rotations[1]);
        return rotationUtils.isAligned();
    }

    public static boolean placeAt(BlockPos pos, FindItemResult block, boolean rotate, boolean swing, boolean strictDirection) {
        return placeAt(pos, block, rotate, swing, strictDirection, DEFAULT_REACH);
    }

    public static boolean placeAt(BlockPos pos, FindItemResult block, boolean rotate, boolean swing, boolean strictDirection, double reach) {
        if (!block.found() || !canPlace(pos, strictDirection)) return false;
        BlockHitResult hitResult = resolvePlaceHit(pos, reach);
        if (hitResult == null) return false;
        if (rotate) requestRotationForHit(hitResult, false);
        if (block.getHand() == null && !InvUtils.swap(block.slot(), false)) return false;
        Hand hand = block.getHand() != null ? block.getHand() : Hand.MAIN_HAND;
        return placeHit(hitResult, hand, swing);
    }

    public static FindItemResult findResistantBlock() {
        for (Block block : RESISTANT_BLOCKS) {
            FindItemResult result = InvUtils.findInHotbar(new Item[]{block.asItem()});
            if (result.found()) return result;
        }
        return InvUtils.findInHotbar(itemStack -> false);
    }

    public static boolean placeBlock(BlockPos pos, boolean rotate, boolean swing, boolean strictDirection) {
        FindItemResult block = findResistantBlock();
        return !block.found() ? false : placeBlock(pos, block, rotate, swing, strictDirection);
    }

    public static boolean placeBlock(BlockPos pos, FindItemResult block, boolean rotate, boolean swing, boolean strictDirection) {
        return placeAt(pos, block, rotate, swing, strictDirection);
    }

    public static BlockHitResult getAirPlaceHit(BlockPos pos, double reach) {
        if (MeteorClient.mc.player == null || MeteorClient.mc.world == null) return null;
        Vec3d eye = MeteorClient.mc.player.getEyePos();
        double minX = pos.getX();
        double minY = pos.getY();
        double minZ = pos.getZ();
        double maxX = minX + 1.0;
        double maxY = minY + 1.0;
        double maxZ = minZ + 1.0;
        double s = 0.001;
        double reachSq = reach * reach;
        BlockHitResult best = null;
        double bestDist = Double.MAX_VALUE;

        for (Direction side : Direction.values()) {
            boolean visible;
            Vec3d point;
            switch (side) {
                case DOWN:
                    visible = eye.y < minY;
                    point = new Vec3d(clamp(eye.x, minX, maxX), minY - s, clamp(eye.z, minZ, maxZ));
                    break;
                case UP:
                    visible = eye.y > maxY;
                    point = new Vec3d(clamp(eye.x, minX, maxX), maxY + s, clamp(eye.z, minZ, maxZ));
                    break;
                case NORTH:
                    visible = eye.z < minZ;
                    point = new Vec3d(clamp(eye.x, minX, maxX), clamp(eye.y, minY, maxY), minZ - s);
                    break;
                case SOUTH:
                    visible = eye.z > maxZ;
                    point = new Vec3d(clamp(eye.x, minX, maxX), clamp(eye.y, minY, maxY), maxZ + s);
                    break;
                case WEST:
                    visible = eye.x < minX;
                    point = new Vec3d(minX - s, clamp(eye.y, minY, maxY), clamp(eye.z, minZ, maxZ));
                    break;
                case EAST:
                    visible = eye.x > maxX;
                    point = new Vec3d(maxX + s, clamp(eye.y, minY, maxY), clamp(eye.z, minZ, maxZ));
                    break;
                default:
                    visible = false;
                    point = null;
            }
            if (visible) {
                double dist = eye.squaredDistanceTo(point);
                if (!(dist > reachSq) && dist < bestDist) {
                    bestDist = dist;
                    best = new BlockHitResult(point, side, pos, true);
                }
            }
        }
        return best;
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : Math.min(v, max);
    }

    public static boolean canPlace(BlockPos pos, boolean strictDirection) {
        if (MeteorClient.mc.player == null || MeteorClient.mc.world == null) return false;
        if (!MeteorClient.mc.world.getBlockState(pos).isReplaceable()) return false;
        if (!MeteorClient.mc.world.canPlace(Blocks.OBSIDIAN.getDefaultState(), pos, ShapeContext.absent())) return false;

        Box checkBox = Box.from(Vec3d.ofCenter(pos));
        for (Entity entity : MeteorClient.mc.world.getOtherEntities(null, checkBox)) {
            if (!entity.isSpectator() && entity.isAlive()) return false;
        }
        return !strictDirection || getPlaceSide(pos) != null;
    }

    public static Direction getPlaceSide(BlockPos pos) {
        if (MeteorClient.mc.world == null) return null;
        if (!MeteorClient.mc.world.getBlockState(pos.down()).isReplaceable()) return Direction.DOWN;
        for (Direction side : Type.HORIZONTAL) {
            BlockPos neighbor = pos.offset(side);
            if (!MeteorClient.mc.world.getBlockState(neighbor).isReplaceable()) return side;
        }
        return !MeteorClient.mc.world.getBlockState(pos.up()).isReplaceable() ? Direction.UP : null;
    }

    public static BlockPos getDirectionalPlacement(float yaw, BlockPos basePos) {
        float normalizedYaw = yaw % 360.0F;
        if (normalizedYaw < 0.0F) normalizedYaw += 360.0F;

        if (normalizedYaw >= 22.5 && normalizedYaw < 67.5) return basePos.south().west();
        else if (normalizedYaw >= 67.5 && normalizedYaw < 112.5) return basePos.west();
        else if (normalizedYaw >= 112.5 && normalizedYaw < 157.5) return basePos.north().west();
        else if (normalizedYaw >= 157.5 && normalizedYaw < 202.5) return basePos.north();
        else if (normalizedYaw >= 202.5 && normalizedYaw < 247.5) return basePos.north().east();
        else if (normalizedYaw >= 247.5 && normalizedYaw < 292.5) return basePos.east();
        else return (normalizedYaw >= 292.5 && normalizedYaw < 337.5) ? basePos.south().east() : basePos.south();
    }

    public static boolean isPhasing() {
        if (MeteorClient.mc.player == null) return false;
        Box bb = MeteorClient.mc.player.getBoundingBox();
        int minX = MathHelper.floor(bb.minX);
        int maxX = MathHelper.floor(bb.maxX) + 1;
        int minY = MathHelper.floor(bb.minY);
        int maxY = MathHelper.floor(bb.maxY) + 1;
        int minZ = MathHelper.floor(bb.minZ);
        int maxZ = MathHelper.floor(bb.maxZ) + 1;

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!MeteorClient.mc.world.getBlockState(pos).getCollisionShape(MeteorClient.mc.world, pos).isEmpty()) {
                        Box blockBox = new Box(x, y, z, x + 1.0, y + 1.0, z + 1.0);
                        if (bb.intersects(blockBox)) return true;
                    }
                }
            }
        }
        return false;
    }

    public static int getEnderPearlSlot() {
        if (MeteorClient.mc.player == null) return -1;
        for (int i = 0; i < 45; i++) {
            ItemStack stack = MeteorClient.mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.ENDER_PEARL) return i;
        }
        return -1;
    }

    public static void clickSlot(int slot, SlotActionType actionType) {
        if (MeteorClient.mc.interactionManager != null && MeteorClient.mc.player != null) {
            MeteorClient.mc.interactionManager.clickSlot(0, slot, 0, actionType, MeteorClient.mc.player);
        }
    }

    public static boolean isPhased() {
        return isPhasing();
    }

    public static boolean isDoublePhased() {
        if (MeteorClient.mc.player == null || MeteorClient.mc.world == null) return false;
        Box playerBox = MeteorClient.mc.player.getBoundingBox();
        boolean feetBlocked = false;
        boolean headBlocked = false;

        for (int x = (int) Math.floor(playerBox.minX); x <= Math.floor(playerBox.maxX); x++) {
            for (int z = (int) Math.floor(playerBox.minZ); z <= Math.floor(playerBox.maxZ); z++) {
                BlockPos feetPos = new BlockPos(x, (int) Math.floor(playerBox.minY), z);
                if (!MeteorClient.mc.world.getBlockState(feetPos).getCollisionShape(MeteorClient.mc.world, feetPos).isEmpty()) feetBlocked = true;

                BlockPos headPos = new BlockPos(x, (int) Math.floor(playerBox.maxY), z);
                if (!MeteorClient.mc.world.getBlockState(headPos).getCollisionShape(MeteorClient.mc.world, headPos).isEmpty()) headBlocked = true;

                if (feetBlocked && headBlocked) return true;
            }
        }
        return false;
    }
}
