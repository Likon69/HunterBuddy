package com.hunterbuddy.modules.tradingsystem.stages;

import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import com.hunterbuddy.modules.tradingsystem.ExperienceTraderModule;
import com.hunterbuddy.modules.tradingsystem.utilites.Stage;

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


        boolean isAlreadyWithin = this.chest.getChestPosition(super.mainModule).isWithinDistance(
            getPlayer().getEntityPos(),
            super.mainModule.baritoneStopDistance.get() + 1
        );

        if (isAlreadyWithin) {
            if (this.isFirstTick) {
                if (super.mainModule.doOutput()) {
                    super.mainModule.info("Reached target.");
                }


                super.mainModule.addWaitTicks(20);

                return this.isFirstTick = false;
            } else if (!this.isPacketSent) {
                reset();

                getPlayer().lookAt(
                    EntityAnchorArgumentType.EntityAnchor.EYES,
                    this.chest.getChestPosition(super.mainModule).toCenterPos()
                );

                HitResult hitResult = getPlayer().raycast(5, 0, false);

                if (!hitResult.getType().equals(HitResult.Type.BLOCK)) {
                    super.mainModule.error("Could not target the chest.");
                    super.mainModule.toggle();
                    return false;
                }

                BlockHitResult blockHitResult = (BlockHitResult) hitResult;

                requireNonNull(client.interactionManager).interactBlock(
                    getPlayer(),
                    Hand.MAIN_HAND,
                    blockHitResult
                );

                this.isPacketSent = true;
                this.isFirstTick = false;

                if (super.mainModule.doOutput()) {
                    super.mainModule.info("Chest interaction sent.");
                }

                return false;
            } else if (client.currentScreen == null) {
                this.waitTicks++;

                if (waitTicks > super.mainModule.maxServerTimeout.get() * 20) {
                    super.mainModule.error("Chest screen timed out.");
                    super.mainModule.toggle();
                    return false;
                }
            } else {
                return true;
            }

            return false;
        }

        if (!getGoalProcess().isActive() || getGoalProcess().getGoal() == null) {
            if (this.attempts > 10) {
                super.mainModule.error("Could not reach chest; check coordinates: "
                    + this.chest.getChestPosition(super.mainModule).toShortString());
                super.mainModule.toggle();
                return false;
            }

            getGoalProcess().setGoalAndPath(new GoalBlock(this.chest.getChestPosition(super.mainModule).add(0, 1, 0)));

            if (super.mainModule.doOutput()) {
                super.mainModule.info("Chest goal set: " + this.chest + ".");
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
