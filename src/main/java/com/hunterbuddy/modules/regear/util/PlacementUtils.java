package com.hunterbuddy.modules.regear.util;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class PlacementUtils {
    public static final double DEFAULT_REACH = 4.5;

    private static final int LAST_PEARL_SLOT = 44;
    private static final float FULL_TURN = 360.0f;
    private static final float OCTANT = 45.0f;
    private static final float HALF_OCTANT = 22.5f;
    private static final int OCTANTS = 8;
    private static final double HALF_BLOCK = 0.5;
    private static final double AIR_PLACE_DROP = 0.001;
    private static final Item[] RESISTANT_BLOCKS = {
        Items.OBSIDIAN, Items.CRYING_OBSIDIAN, Items.ENDER_CHEST, Items.RESPAWN_ANCHOR, Items.ENCHANTING_TABLE, Items.ANVIL
    };

    private static MinecraftClient client() {
        return MinecraftClient.getInstance();
    }

    public static int grimSequenceId() {
        ClientPlayerEntity player = client().player;
        return player == null ? 0 : player.currentScreenHandler.getRevision() + 2;
    }

    public static void grimPlace(BlockHitResult hit) {
        ClientPlayerEntity player = client().player;
        if (player == null) return;
        PlayerActionC2SPacket swap = new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN);
        player.networkHandler.sendPacket(swap);
        player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(Hand.OFF_HAND, hit, grimSequenceId()));
        player.networkHandler.sendPacket(swap);
    }

    public static boolean placeHit(BlockHitResult hit, Hand hand, boolean swing) {
        MinecraftClient mc = client();
        if (mc.player == null || mc.interactionManager == null) return false;
        if (hit.isInsideBlock()) {
            grimPlace(hit);
        } else {
            mc.interactionManager.interactBlock(mc.player, hand, hit);
        }
        if (swing) {
            if (hand == Hand.MAIN_HAND) {
                mc.player.swingHand(hand);
            } else {
                mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(hand));
            }
        }
        return true;
    }

    public static Direction getPlaceSide(BlockPos pos) {
        World world = client().world;
        if (world == null) return null;
        if (!world.getBlockState(pos.down()).isReplaceable()) return Direction.DOWN;
        for (Direction side : Direction.Type.HORIZONTAL) {
            if (!world.getBlockState(pos.offset(side)).isReplaceable()) return side;
        }
        if (!world.getBlockState(pos.up()).isReplaceable()) return Direction.UP;
        return null;
    }

    public static BlockHitResult resolvePlaceHit(BlockPos pos, double reach) {
        Direction side = getPlaceSide(pos);
        if (side == null) return getAirPlaceHit(pos, reach);
        BlockPos neighbour = pos.offset(side);
        Direction face = side.getOpposite();
        Vec3d point = Vec3d.ofCenter(neighbour).add(face.getOffsetX() * HALF_BLOCK, face.getOffsetY() * HALF_BLOCK, face.getOffsetZ() * HALF_BLOCK);
        return new BlockHitResult(point, face, neighbour, false);
    }

    public static BlockHitResult getAirPlaceHit(BlockPos pos, double reach) {
        MinecraftClient mc = client();
        if (mc.player == null || mc.world == null) return null;
        return AirPlaceGeometry.nearestFaceHit(pos, mc.player.getEyePos(), reach);
    }

    public static BlockHitResult getGrimDirectionalHit(BlockPos pos, Direction direction) {
        return new BlockHitResult(Vec3d.ofCenter(pos).subtract(0.0, AIR_PLACE_DROP, 0.0), direction, pos, true);
    }

    public static float[] rotationsForHit(BlockHitResult hit) {
        ClientPlayerEntity player = client().player;
        if (player == null) return new float[]{0.0f, 0.0f};
        return RotationUtils.getRotationsTo(player.getEyePos(), hit.getPos());
    }

    public static boolean requestRotationForHit(BlockHitResult hit, boolean syncMovement) {
        float[] angles = rotationsForHit(hit);
        RotationUtils rotations = RotationUtils.getInstance();
        if (syncMovement) {
            rotations.setRotationFull(angles[0], angles[1]);
        } else {
            rotations.setRotationSilent(angles[0], angles[1]);
        }
        return rotations.isAligned();
    }

    public static boolean canPlace(BlockPos pos, boolean strictDirection) {
        MinecraftClient mc = client();
        if (mc.player == null || mc.world == null) return false;
        BlockState state = mc.world.getBlockState(pos);
        if (!state.isReplaceable()) return false;
        if (!mc.world.canPlace(Blocks.OBSIDIAN.getDefaultState(), pos, ShapeContext.absent())) return false;
        for (Entity entity : mc.world.getOtherEntities(null, Box.from(Vec3d.ofCenter(pos)))) {
            if (entity instanceof LivingEntity && !entity.isSpectator()) return false;
        }
        return !strictDirection || getPlaceSide(pos) != null;
    }

    public static boolean placeAt(BlockPos pos, FindItemResult block, boolean rotate, boolean swing, boolean strictDirection) {
        return placeAt(pos, block, rotate, swing, strictDirection, DEFAULT_REACH);
    }

    public static boolean placeAt(BlockPos pos, FindItemResult block, boolean rotate, boolean swing, boolean strictDirection, double reach) {
        if (!block.found() || !canPlace(pos, strictDirection)) return false;
        BlockHitResult hit = resolvePlaceHit(pos, reach);
        if (hit == null) return false;
        if (rotate) {
            requestRotationForHit(hit, false);
        }
        Hand hand = block.getHand();
        if (hand == null) {
            if (!InvUtils.swap(block.slot(), false)) return false;
            hand = Hand.MAIN_HAND;
        }
        return placeHit(hit, hand, swing);
    }

    public static FindItemResult findResistantBlock() {
        for (Item item : RESISTANT_BLOCKS) {
            FindItemResult result = InvUtils.findInHotbar(item);
            if (result.found()) return result;
        }
        return new FindItemResult(-1, 0);
    }

    public static boolean placeBlock(BlockPos pos, boolean rotate, boolean swing, boolean strictDirection) {
        FindItemResult block = findResistantBlock();
        return block.found() && placeAt(pos, block, rotate, swing, strictDirection);
    }

    public static boolean placeBlock(BlockPos pos, FindItemResult block, boolean rotate, boolean swing, boolean strictDirection) {
        return placeAt(pos, block, rotate, swing, strictDirection);
    }

    public static BlockPos getDirectionalPlacement(float yaw, BlockPos basePos) {
        float angle = ((yaw % FULL_TURN) + FULL_TURN) % FULL_TURN;
        int octant = (int) ((angle + HALF_OCTANT) / OCTANT) % OCTANTS;
        return switch (octant) {
            case 1 -> basePos.south().west();
            case 2 -> basePos.west();
            case 3 -> basePos.north().west();
            case 4 -> basePos.north();
            case 5 -> basePos.north().east();
            case 6 -> basePos.east();
            case 7 -> basePos.south().east();
            default -> basePos.south();
        };
    }

    public static boolean isPhasing() {
        return PhaseChecks.isInsideBlocks();
    }

    public static boolean isPhased() {
        return isPhasing();
    }

    public static boolean isDoublePhased() {
        return PhaseChecks.isFeetAndHeadBlocked();
    }

    public static int getEnderPearlSlot() {
        ClientPlayerEntity player = client().player;
        if (player == null) return -1;
        for (int slot = 0; slot <= LAST_PEARL_SLOT; slot++) {
            if (player.playerScreenHandler.getSlot(slot).getStack().isOf(Items.ENDER_PEARL)) return slot;
        }
        return -1;
    }

    public static void clickSlot(int slot, SlotActionType type) {
        MinecraftClient mc = client();
        if (mc.interactionManager == null || mc.player == null) return;
        mc.interactionManager.clickSlot(0, slot, 0, type, mc.player);
    }
}
