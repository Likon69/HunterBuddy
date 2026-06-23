/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package org.linytech.trader.modules.tradingsystem.stages;

import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.linytech.trader.modules.tradingsystem.ExperienceTraderModule;
import org.linytech.trader.modules.tradingsystem.utilites.Stage;

public class SprintToChest extends Stage {
    private boolean isPacketSent = false;
    private boolean isFirstTick = false;
    private final NeededChest chest;
    private int waitTicks = 0;
    private int attempts = 0;

    public SprintToChest(ExperienceTraderModule main, NeededChest neededChest) {
        super(main);

        this.chest = neededChest;
    }

    @Override
    public boolean work() {
        if (!super.mainModule.isTimeToClear()) return true;

        //NOTE: "+ 1" because block not fluid, can't go to "< 1" distance
        boolean isAlreadyWithin = this.chest.getChestPosition(super.mainModule).isWithinDistance(
            getPlayer().getPos(),
            super.mainModule.baritoneStopDistance.get() + 1
        );

        if (isAlreadyWithin) {
            if (this.isFirstTick) {
                if (super.mainModule.doOutput()) {
                    super.mainModule.info("Игрок на нужных координатах.");
                }

                //baritone can jump on block and lookAt can be uncorrected
                super.mainModule.addWaitTicks(20);

                return this.isFirstTick = false;
            } else if (!this.isPacketSent) {
                reset(); //JOKE: method resetting this.isPacketSent

                getPlayer().lookAt(
                    EntityAnchorArgumentType.EntityAnchor.EYES,
                    this.chest.getChestPosition(super.mainModule).toCenterPos()
                );

                HitResult hitResult = getPlayer().raycast(5, 0, false);

                if (!hitResult.getType().equals(HitResult.Type.BLOCK)) {
                    super.mainModule.toggle();
                    super.mainModule.error("Ошибка при рейкастинге на сундук!");
                }

                BlockHitResult blockHitResult = (BlockHitResult) hitResult;

                requireNonNull(client.interactionManager).interactBlock(
                    getPlayer(),
                    Hand.MAIN_HAND,
                    blockHitResult
                );

                this.isPacketSent = true; //JOKE continue: setting this.isPacketSent
                this.isFirstTick = false;

                if (super.mainModule.doOutput()) {
                    super.mainModule.info("Пакет интеракта отправлен!");
                }

                return false;
            } else if (client.currentScreen == null) {
                this.waitTicks++;

                if (waitTicks > super.mainModule.maxServerTimeout.get() * 20) {
                    super.mainModule.error("Слишком долго открывается сундук.");
                    super.mainModule.toggle();
                }
            } else {
                return true;
            }

            return false;
        }

        if (!getGoalProcess().isActive() || getGoalProcess().getGoal() == null) {
            if (this.attempts > 10) {
                super.mainModule.toggle();
                super.mainModule.error("Баритон не может дойти до цели. Убедитесь в правильности и чистоте координат: "
                    + this.chest.getChestPosition(super.mainModule).toShortString());
            }

            getGoalProcess().setGoalAndPath(new GoalBlock(this.chest.getChestPosition(super.mainModule).add(0, 1, 0)));

            if (super.mainModule.doOutput()) {
                super.mainModule.info("Установлена цель на сундук " + this.chest + ".");
            }

            this.attempts++;
        }

        return false;
    }

    @Override
    public void reset() {
        this.isPacketSent = false;
        this.isFirstTick = true;
        this.waitTicks = 0;
        this.attempts = 0;
        getGoalProcess().setGoalAndPath(null);
    }

    @Override
    public String toShortString() {
        return this.getClass().getSimpleName() + "(%s)".formatted(this.chest);
    }

    public enum NeededChest {
        EXPERIENCE {
            @Override
            public BlockPos getChestPosition(ExperienceTraderModule module) {
                return module.experienceChestPosition.get();
            }
        },
        EMERALDS {
            @Override
            public BlockPos getChestPosition(ExperienceTraderModule module) {
                return module.emeraldChestPosition.get();
            }
        };

        public abstract BlockPos getChestPosition(ExperienceTraderModule module);
    }
}
