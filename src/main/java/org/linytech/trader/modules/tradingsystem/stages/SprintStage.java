/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package org.linytech.trader.modules.tradingsystem.stages;

import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.linytech.trader.modules.tradingsystem.ExperienceTraderModule;
import org.linytech.trader.modules.tradingsystem.utilites.Stage;
import org.linytech.trader.modules.tradingsystem.utilites.TraderUtils;

public class SprintStage extends Stage {
    private BlockPos targetPosition;
    private long waitTicks = 0;

    public SprintStage(ExperienceTraderModule main) {
        super(main);
    }

    @Override
    public boolean work() {
        if (super.mainModule.getTarget() == null) {
            super.mainModule.setTarget(TraderUtils.getNearestCleric(getPlayer(), super.mainModule.blocked, super.mainModule.searchingRadius.get()));

            if (super.mainModule.getTarget() == null) {
                super.mainModule.info("Торговля закончилась!");
                super.mainModule.toggle();
                reset();
                return false;
            }

            if (super.mainModule.doOutput()) {
                super.mainModule.info("Найден житель на координатах: " + super.mainModule.getTarget().getBlockPos().toShortString());
            }
        }

        if (this.targetPosition == null) {
            this.targetPosition = super.mainModule.getTarget().getBlockPos().add(1, 2, 1);

            if (super.mainModule.doOutput()) {
                super.mainModule.info("Установлена таргет позиция на: " + this.targetPosition.toShortString());
            }
        }

        boolean isAlreadyWithin = targetPosition.isWithinDistance(
            getPlayer().getPos(),
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
                    super.mainModule.info("Игрок на нужных координатах.");
                }

                return true;
            }

            getGoalProcess().setGoalAndPath(null);
        }

        if ((!getGoalProcess().isActive() || getGoalProcess().getGoal() == null) && this.waitTicks == 0) {
            getGoalProcess().setGoalAndPath(new GoalBlock(this.targetPosition));

            if (super.mainModule.doOutput()) {
                super.mainModule.info("Установлена цель для баритона.");
            }
        }

        return false;
    }

    public void reset() {
        this.targetPosition = null;
        this.waitTicks = 0;
        getGoalProcess().setGoalAndPath(null);
    }
}
