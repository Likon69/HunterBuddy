package com.hunterbuddy.modules.mine;

import java.util.function.Predicate;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.friends.Friends;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class EnemyBlocks {
    private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    private EnemyBlocks() {
    }

    private static MinecraftClient mc() {
        return MeteorClient.mc;
    }

    public static PlayerEntity closestEnemy(double range) {
        MinecraftClient mc = mc();
        if (mc.player == null || mc.world == null) {
            return null;
        }

        PlayerEntity best = null;
        double bestDist = range * range;

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || p.isSpectator() || !p.isAlive() || Friends.get().isFriend(p)) {
                continue;
            }

            double d = mc.player.squaredDistanceTo(p);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }

        return best;
    }

    public static boolean resistant(BlockState s) {
        Block b = s.getBlock();
        if (b == Blocks.BEDROCK || b == Blocks.REINFORCED_DEEPSLATE || b == Blocks.BARRIER || b == Blocks.COMMAND_BLOCK || b == Blocks.STRUCTURE_BLOCK) {
            return false;
        }

        return b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN || b == Blocks.ENDER_CHEST || b == Blocks.ANCIENT_DEBRIS || b == Blocks.RESPAWN_ANCHOR;
    }

    public static boolean ownSurround(BlockPos pos) {
        BlockPos me = mc().player.getBlockPos();
        return pos.equals(me.north()) || pos.equals(me.south()) || pos.equals(me.east()) || pos.equals(me.west()) || pos.equals(me.up()) || pos.equals(me.up(2));
    }

    public static BlockPos enemyBlock(PlayerEntity enemy, double range, boolean head, Predicate<BlockPos> busy) {
        double limit = range * range;
        BlockPos feet = enemy.getBlockPos();

        if (usable(feet, limit, busy)) {
            return feet;
        }

        BlockPos best = null;
        double bestDist = limit;
        for (Direction d : SIDES) {
            BlockPos c = feet.offset(d);
            if (!usable(c, limit, busy)) {
                continue;
            }

            double dist = distSq(c);
            if (dist < bestDist) {
                bestDist = dist;
                best = c;
            }
        }

        if (best != null) {
            return best;
        }

        if (head) {
            BlockPos top = feet.up(2);
            if (usable(top, limit, busy)) {
                return top;
            }
        }

        return null;
    }

    public static BlockPos crawlBlock(double range) {
        MinecraftClient mc = mc();
        BlockPos above = mc.player.getBlockPos().up();
        BlockState s = mc.world.getBlockState(above);
        if (s.isAir() || s.getHardness(mc.world, above) == -1.0F) {
            return null;
        }

        Block b = s.getBlock();
        if (b == Blocks.BEDROCK || b == Blocks.REINFORCED_DEEPSLATE || b == Blocks.BARRIER) {
            return null;
        }

        return distSq(above) <= range * range ? above : null;
    }

    public static Direction facing(BlockPos pos) {
        Vec3d diff = mc().player.getEyePos().subtract(Vec3d.ofCenter(pos)).normalize();
        Direction best = Direction.UP;
        double bestDot = -1.0;

        for (Direction d : Direction.values()) {
            double dot = diff.dotProduct(Vec3d.of(d.getVector()));
            if (dot > bestDot) {
                bestDot = dot;
                best = d;
            }
        }

        return best;
    }

    public static float[] aimAt(Vec3d from, Vec3d to) {
        Vec3d d = to.subtract(from);
        double yaw = Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0;
        double pitch = Math.toDegrees(-Math.atan2(d.y, Math.hypot(d.x, d.z)));
        return new float[] {MathHelper.wrapDegrees((float) yaw), MathHelper.wrapDegrees((float) pitch)};
    }

    private static double distSq(BlockPos pos) {
        return mc().player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos));
    }

    private static boolean breakable(BlockPos pos) {
        BlockState s = mc().world.getBlockState(pos);
        return !s.isAir() && s.getHardness(mc().world, pos) != -1.0F;
    }

    private static boolean usable(BlockPos pos, double limit, Predicate<BlockPos> busy) {
        return breakable(pos)
            && distSq(pos) <= limit
            && !busy.test(pos)
            && resistant(mc().world.getBlockState(pos))
            && !ownSurround(pos);
    }
}
