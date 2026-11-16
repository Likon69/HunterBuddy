package com.hunterbuddy.modules.nuker;

import com.hunterbuddy.modules.HMine;
import java.util.function.Predicate;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ToolComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;

public final class NukerTargets {
    private static final Direction[] SIDES = Direction.values();
    private final MinecraftClient mc = MeteorClient.mc;
    private final NukerSettings config;
    private final BlockPos.Mutable cursor = new BlockPos.Mutable();

    public NukerTargets(NukerSettings config) {
        this.config = config;
    }

    public BlockPos next(HMine mine, Predicate<BlockPos> unavailable) {
        View view = view(mine);
        Vec3d eye = view.eye;
        double range = view.range;
        int minX = MathHelper.floor(eye.x - range) - 1;
        int minY = Math.max(mc.world.getBottomY(), MathHelper.floor(eye.y - range) - 1);
        int minZ = MathHelper.floor(eye.z - range) - 1;
        int maxX = MathHelper.floor(eye.x + range);
        int maxY = Math.min(mc.world.getTopYInclusive(), MathHelper.floor(eye.y + range));
        int maxZ = MathHelper.floor(eye.z + range);
        BlockPos best = null;
        double bestDistance = 0;
        float bestSpeed = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    if (unavailable.test(cursor) || !eligible(mine, cursor, view)) continue;
                    BlockState state = mc.world.getBlockState(cursor);
                    float speed = mine.breakDelta(state, cursor);
                    if (Float.isNaN(speed) || speed <= 0) continue;
                    double distance = NukerRules.distanceSquared(eye.x, eye.y, eye.z, x, y, z);
                    if (best != null && !NukerRules.better(config.order.get(), distance, speed, bestDistance, bestSpeed)) continue;
                    if (config.visibleOnly.get() && !visible(cursor, state, eye, range)) continue;
                    best = cursor.toImmutable();
                    bestDistance = distance;
                    bestSpeed = speed;
                }
            }
        }
        return best;
    }

    public boolean accepts(HMine mine, BlockPos pos) {
        if (mc.player == null || mc.world == null) return false;
        View view = view(mine);
        return eligible(mine, pos, view)
            && (!config.visibleOnly.get() || visible(pos, mc.world.getBlockState(pos), view.eye, view.range));
    }

    private View view(HMine mine) {
        return new View(mc.player.getEyePos(), mc.player.getBoundingBox(), mc.player.getVelocityAffectingPos(),
            mc.player.getBlockY(), Math.min(config.range.get(), mine.miningRange()));
    }

    private boolean eligible(HMine mine, BlockPos pos, View view) {
        if (!NukerRules.shapeAllows(config.shape.get(), pos.getY(), view.feetY)) return false;
        if (NukerRules.distanceSquared(view.eye.x, view.eye.y, view.eye.z, pos.getX(), pos.getY(), pos.getZ()) > view.range * view.range) return false;
        if (!mc.world.isChunkLoaded(pos)) return false;
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir() || state.getBlock() instanceof FluidBlock || state.getHardness(mc.world, pos) < 0) return false;
        if (mine.excludedFromAutomation(state)) return false;
        if (!NukerRules.filterAllows(config.filter.get(), config.blocks.get().contains(state.getBlock()))) return false;
        if (config.ignoreStandingBlock.get()) {
            Box body = view.body;
            if (pos.equals(view.support)
                || NukerRules.supportsPlayer(pos.getX(), pos.getY(), pos.getZ(), MathHelper.floor(body.minY - 0.00001),
                    body.minX, body.maxX, body.minZ, body.maxZ)) return false;
        }
        if (!config.antiBreak.get()) return true;
        ItemStack tool = mine.miningTool(state);
        ToolComponent component = tool.get(DataComponentTypes.TOOL);
        if (component == null || component.damagePerBlock() == 0) return true;
        return NukerRules.toolSafe(tool.getMaxDamage(), tool.getDamage(), config.minimumDurability.get(),
            component.damagePerBlock() * mine.miningCapacity());
    }

    private boolean visible(BlockPos pos, BlockState state, Vec3d eye, double range) {
        VoxelShape shape = state.getOutlineShape(mc.world, pos);
        if (shape.isEmpty()) return false;
        Box bounds = shape.getBoundingBox().offset(pos);
        Vec3d center = bounds.getCenter();
        for (Direction side : SIDES) {
            double x = center.x + side.getOffsetX() * Math.max(0, bounds.getLengthX() * 0.5 - 0.00001);
            double y = center.y + side.getOffsetY() * Math.max(0, bounds.getLengthY() * 0.5 - 0.00001);
            double z = center.z + side.getOffsetZ() * Math.max(0, bounds.getLengthZ() * 0.5 - 0.00001);
            BlockHitResult hit = mc.world.raycast(new RaycastContext(eye, new Vec3d(x, y, z),
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
            if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)
                && eye.squaredDistanceTo(hit.getPos()) <= range * range) return true;
        }
        return false;
    }

    private record View(Vec3d eye, Box body, BlockPos support, int feetY, double range) {
    }
}
