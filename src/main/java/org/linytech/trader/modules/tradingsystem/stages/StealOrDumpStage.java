/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package org.linytech.trader.modules.tradingsystem.stages;

import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import org.linytech.trader.modules.tradingsystem.ExperienceTraderModule;
import org.linytech.trader.modules.tradingsystem.utilites.Stage;

import java.util.concurrent.atomic.AtomicBoolean;

public class StealOrDumpStage extends Stage {
    private final Type type;
    private final AtomicBoolean isComplete = new AtomicBoolean(false);
    private boolean isFirstTick = true;

    public StealOrDumpStage(ExperienceTraderModule main, Type type) {
        super(main);
        this.type = type;
    }

    private void moveSlots(ScreenHandler handler, int start, int end, Type type) {
        long lastMovingTime = 0;
        int stoleCount = 0;
        for (int i = start; i < end; i++) {
            if (!handler.getSlot(i).hasStack()) continue;

            if (!(System.currentTimeMillis() - lastMovingTime > super.mainModule.stealAndDumpDelay.get())) {
                try {
                    Thread.sleep(super.mainModule.stealAndDumpDelay.get() / 2);
                } catch (InterruptedException ignored) { }
            }

            if (client.currentScreen == null || !Utils.canUpdate()) break;

            Item item = handler.getSlot(i).getStack().getItem();
            if (!item.equals(type.getItem())) continue;

            InvUtils.shiftClick().slotId(i);

            if (type.equals(Type.STEAL)) {
                stoleCount++;
                if (stoleCount >= type.stacksCount())
                    break;
            }

            lastMovingTime = System.currentTimeMillis();
        }
        this.isComplete.set(true);
    }

    private void steal(ScreenHandler handler) {
        MeteorExecutor.execute(() -> moveSlots(handler, 0, SlotUtils.indexToId(SlotUtils.MAIN_START), type));
    }

    private void dump(ScreenHandler handler) {
        int playerInvOffset = SlotUtils.indexToId(SlotUtils.MAIN_START);
        MeteorExecutor.execute(() -> moveSlots(handler, playerInvOffset, playerInvOffset + 4 * 9, type));
    }

    @Override
    public boolean work() {
        if (!super.mainModule.isTimeToClear()) return true;

        if (this.isFirstTick) {
            if (requireNonNull(client.currentScreen) instanceof GenericContainerScreen chestScreen) {
                switch (type) {
                    case DUMP -> dump(chestScreen.getScreenHandler());
                    case STEAL -> steal(chestScreen.getScreenHandler());
                }
            } else {
                throw new RuntimeException("Invalid screen");
            }

            this.isFirstTick = false;
        }

        if (this.isComplete.get()) {
            if (client.currentScreen != null) {
                client.currentScreen.close();
            }
        }

        return this.isComplete.get();
    }

    @Override
    public void reset() {
        this.isComplete.set(false);
        this.isFirstTick = true;
    }

    @Override
    public String toShortString() {
        return this.getClass().getSimpleName() + "(%s)".formatted(this.type);
    }

    public enum Type {
        STEAL {
            @Override
            public Item getItem() {
                return Items.EMERALD_BLOCK;
            }
            @Override
            public int stacksCount() {
                return 3;
            }
        },
        DUMP {
            @Override
            public Item getItem() {
                return Items.EXPERIENCE_BOTTLE;
            }
        };

        public abstract Item getItem();
        public int stacksCount() {
            return -1;
        }
    }
}
