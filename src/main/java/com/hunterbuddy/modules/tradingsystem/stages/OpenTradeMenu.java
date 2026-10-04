package com.hunterbuddy.modules.tradingsystem.stages;

import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.hit.EntityHitResult;
import com.hunterbuddy.modules.tradingsystem.ExperienceTraderModule;
import com.hunterbuddy.modules.tradingsystem.utilites.Stage;


public class OpenTradeMenu extends Stage {
    private int waitTicks = 0;
    private boolean isPacketSent = false;

    public OpenTradeMenu(ExperienceTraderModule main) {
        super(main);
    }

    @Override
    public boolean work() {
        if (!this.isPacketSent) {
            var player = getPlayer();
            var target = requireNonNull(super.mainModule.getTarget());
            var interactionManager = requireNonNull(client.interactionManager);
            PlayerInput input = player.input.playerInput;
            player.input.playerInput = new PlayerInput(input.forward(), input.backward(), input.left(), input.right(), input.jump(), false, input.sprint());
            try {
                interactionManager.interactEntityAtLocation(player, target, new EntityHitResult(target, target.getBoundingBox().getCenter()), Hand.MAIN_HAND);
                interactionManager.interactEntity(player, target, Hand.MAIN_HAND);
            } finally {
                player.input.playerInput = input;
            }

            this.isPacketSent = true;

            if (super.mainModule.doOutput()) {
                super.mainModule.info("Trade interaction sent.");
            }
        } else {
            this.waitTicks += 1;

            if (super.mainModule.maxServerTimeout.get() * 20 <= this.waitTicks) {
                super.mainModule.nextRound("Trade screen timed out.");
                reset();
                return false;
            }
        }

        boolean isComplete = client.currentScreen != null && client.currentScreen.getClass().equals(MerchantScreen.class);

        if (isComplete) {
            reset();

            if (super.mainModule.doOutput()) {
                super.mainModule.info("Trade screen opened.");
            }
        }

        return isComplete;
    }

    public void reset() {
        this.waitTicks = 0;
        this.isPacketSent = false;
    }
}
