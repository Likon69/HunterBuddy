/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package org.linytech.trader.modules.tradingsystem.stages;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import org.linytech.trader.modules.tradingsystem.ExperienceTraderModule;
import org.linytech.trader.modules.tradingsystem.utilites.Stage;
import org.lwjgl.glfw.GLFW;

public class CraftStage extends Stage {
    private boolean isFilled = false;
    private long timing = 0;
    private long waitTime = 0;

    public CraftStage(ExperienceTraderModule main) {
        super(main);
    }

    @Override
    public boolean work() {
        if (!super.mainModule.isTimeToClear()) return true;

        if (System.currentTimeMillis() - this.timing < super.mainModule.stealAndDumpDelay.get()) {
            return false;
        } else {
            this.timing = System.currentTimeMillis();
        }

        if (client.currentScreen == null) {
            client.setScreen(new InventoryScreen(getPlayer()));
            return false;
        }

        if (client.currentScreen instanceof InventoryScreen screen) {
            if (!this.isFilled) {
                if (processFillCraftInput(screen)) {
                    super.mainModule.setTimeToClear(false);
                    screen.close();
                    return true;
                }
                else this.isFilled = true;
                return false;
            }
            else {
                if (screen.getScreenHandler().getSlot(0).getStack().getItem().equals(Items.EMERALD)) {
                    processGettingResult(screen);
                    reset();
                    return false;
                } else if (!screen.getScreenHandler().getSlot(0).getStack().getItem().equals(Items.AIR)) {
                    throw new RuntimeException("Got invalid craft.");
                } else {
                    if (this.waitTime >= super.mainModule.maxServerTimeout.get() * 1000) {
                        super.mainModule.toggle();
                        super.mainModule.error("Сервер не ответил результатом крафта.");
                    }

                    this.waitTime += super.mainModule.stealAndDumpDelay.get();
                }
            }
        } else {
            throw new RuntimeException("Invalid screen");
        }

        return false;
    }

    private boolean processFillCraftInput(InventoryScreen screen) {
        FindItemResult result = InvUtils.find(Items.EMERALD_BLOCK);

        if (result.slot() == -1) return true;

        int slot = result.isHotbar() ? result.slot() + 36 : result.slot();
        ItemStack copied = screen.getScreenHandler().getSlot(slot).getStack().copy();

        requireNonNull(client.interactionManager).clickSlot(
            screen.getScreenHandler().syncId,
            slot,
            GLFW.GLFW_MOUSE_BUTTON_LEFT,
            SlotActionType.PICKUP,
            getPlayer()
        );
        requireNonNull(client.interactionManager).clickSlot(
            screen.getScreenHandler().syncId,
            1,
            GLFW.GLFW_MOUSE_BUTTON_LEFT,
            SlotActionType.PICKUP,
            getPlayer()
        );

        screen.getScreenHandler().getSlot(slot).setStack(ItemStack.EMPTY);
        screen.getScreenHandler().getSlot(1).setStack(copied.copy());

        return false;
    }

    private void processGettingResult(InventoryScreen screen) {
        requireNonNull(client.interactionManager).clickSlot(
            screen.getScreenHandler().syncId,
            0,
            GLFW.GLFW_MOUSE_BUTTON_LEFT,
            SlotActionType.QUICK_MOVE,
            getPlayer()
        );

        screen.getScreenHandler().onSlotClick(
            0,
            GLFW.GLFW_MOUSE_BUTTON_LEFT,
            SlotActionType.QUICK_MOVE,
            getPlayer()
        );
    }

    @Override
    public void reset() {
        this.isFilled = false;
        this.waitTime = 0;
        this.timing = 0;
    }
}
