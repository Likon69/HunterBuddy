/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package org.linytech.trader.modules.tradingsystem.stages;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.linytech.trader.modules.tradingsystem.ExperienceTraderModule;
import org.linytech.trader.modules.tradingsystem.utilites.Stage;

import java.util.Objects;

public class OpenTradeMenu extends Stage {
    private int waitTicks = 0;
    private boolean isPacketSent = false;

    public OpenTradeMenu(ExperienceTraderModule main) {
        super(main);
    }

    @Override
    public boolean work() {
        if (!this.isPacketSent) {
            ClientPlayNetworkHandler connection = MinecraftClient.getInstance().getNetworkHandler();

            BlockPos lookAtOffset = super.mainModule.lookAtOffset.get();

            PlayerInteractEntityC2SPacket interactAtPacket = PlayerInteractEntityC2SPacket.interactAt(
                Objects.requireNonNull(super.mainModule.getTarget()),
                false,
                Hand.MAIN_HAND,
                super.mainModule.getTarget().getBlockPos().toCenterPos().add(new Vec3d(lookAtOffset.getX(), lookAtOffset.getY(), lookAtOffset.getZ()))
            );

            PlayerInteractEntityC2SPacket interactPacket = PlayerInteractEntityC2SPacket.interact(
                Objects.requireNonNull(super.mainModule.getTarget()),
                false,
                Hand.MAIN_HAND
            );

            Objects.requireNonNull(connection).sendPacket(interactAtPacket);
            Objects.requireNonNull(connection).sendPacket(interactPacket);

            this.isPacketSent = true;

            if (super.mainModule.doOutput()) {
                super.mainModule.info("Пакет интеракта отправлен.");
            }
        } else {
            this.waitTicks += 1;

            if (super.mainModule.maxServerTimeout.get() * 20 <= this.waitTicks) {
                super.mainModule.nextRound("Меню торговли слишком долго открывалось.");
                reset();
                return false;
            }
        }

        boolean isComplete = client.currentScreen != null && client.currentScreen.getClass().equals(MerchantScreen.class);

        if (isComplete) {
            reset();

            if (super.mainModule.doOutput()) {
                super.mainModule.info("Пакет интеракта успешно обработан.");
            }
        }

        return isComplete;
    }

    public void reset() {
        this.waitTicks = 0;
        this.isPacketSent = false;
    }
}
