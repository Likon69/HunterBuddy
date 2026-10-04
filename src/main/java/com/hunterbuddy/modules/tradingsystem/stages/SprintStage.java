package com.hunterbuddy.modules.tradingsystem.stages;

import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import com.hunterbuddy.modules.tradingsystem.ExperienceTraderModule;
import com.hunterbuddy.modules.tradingsystem.utilites.Stage;
import com.hunterbuddy.modules.tradingsystem.utilites.TraderUtils;

public class SprintStage extends Stage {
    private BlockPos targetPosition;
    private long waitTicks = 0;

    public SprintStage(ExperienceTraderModule main) {
        super(main);
    }

    @Override
    public boolean work() {
        if (client.currentScreen instanceof HandledScreen<?>) {
            client.player.closeHandledScreen();
            return false;
        }
        if (super.mainModule.getTarget() == null) {
            super.mainModule.setTarget(TraderUtils.getNearestCleric(getPlayer(), super.mainModule.blocked, super.mainModule.searchingRadius.get()));

            if (super.mainModule.getTarget() == null) {
                super.mainModule.info("Trading finished.");
                super.mainModule.toggle();
                reset();
                return false;
            }

            if (super.mainModule.doOutput()) {
                super.mainModule.info("Villager found at: " + super.mainModule.getTarget().getBlockPos().toShortString());
            }
        }

        if (this.targetPosition == null) {
            this.targetPosition = findStandingSpot();
            if (this.targetPosition == null) {
                super.mainModule.nextRound("no standing spot next to the villager");
                return false;
            }

            if (super.mainModule.doOutput()) {
                super.mainModule.info("Target set to: " + this.targetPosition.toShortString());
            }
        }

        boolean isAlreadyWithin = targetPosition.isWithinDistance(
            getPlayer().getEntityPos(),
            super.mainModule.baritoneStopDistance.get()
        );

        if (isAlreadyWithin) {
            if (this.waitTicks == 0) this.waitTicks = System.currentTimeMillis();

            if (System.currentTimeMillis() - this.waitTicks >= 200) {
                BlockPos lookAtOffset = super.mainModule.lookAtOffset.get();

                getPlayer().lookAt(
                    EntityAnchorArgumentType.EntityAnchor.EYES,
                    super.mainModule.getTarget().getBlockPos().toCenterPos().add(new Vec3d(lookAtOffset.getX(), lookAtOffset.getY(), lookAtOffset.getZ()))
                );

                reset();

                if (super.mainModule.doOutput()) {
                    super.mainModule.info("Reached target.");
                }

                return true;
            }

            getGoalProcess().setGoalAndPath(null);
        }

        if ((!getGoalProcess().isActive() || getGoalProcess().getGoal() == null) && this.waitTicks == 0) {
            getGoalProcess().setGoalAndPath(new GoalBlock(this.targetPosition));

            if (super.mainModule.doOutput()) {
                super.mainModule.info("Baritone goal set.");
            }
        }

        return false;
    }

    private BlockPos findStandingSpot() {
        var target = super.mainModule.getTarget();
        BlockPos origin = target.getBlockPos();
        var world = client.world;
        var box = target.getBoundingBox();
        Vec3d playerPosition = getPlayer().getEntityPos();
        BlockPos best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (x == 0 && z == 0) continue;
                for (int y = origin.getY() + 1; y <= origin.getY() + 5; y++) {
                    BlockPos pos = new BlockPos(origin.getX() + x, y, origin.getZ() + z);
                    BlockPos below = pos.down();
                    BlockPos above = pos.up();
                    if (!world.getBlockState(below).isSideSolidFullSquare(world, below, Direction.UP)
                        || !world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()
                        || !world.getBlockState(above).getCollisionShape(world, above).isEmpty()) continue;
                    Vec3d eye = new Vec3d(pos.getX() + 0.5, y + 1.62, pos.getZ() + 0.5);
                    Vec3d closestPoint = new Vec3d(
                        MathHelper.clamp(eye.x, box.minX, box.maxX),
                        MathHelper.clamp(eye.y, box.minY, box.maxY),
                        MathHelper.clamp(eye.z, box.minZ, box.maxZ)
                    );
                    if (eye.squaredDistanceTo(closestPoint) <= 2.8 * 2.8) {
                        double distance = playerPosition.squaredDistanceTo(Vec3d.ofBottomCenter(pos));
                        if (distance < bestDistance) {
                            best = pos;
                            bestDistance = distance;
                        }
                    }
                    break;
                }
            }
        }
        return best;
    }

    public void reset() {
        this.targetPosition = null;
        this.waitTicks = 0;
        getGoalProcess().setGoalAndPath(null);
    }
}
