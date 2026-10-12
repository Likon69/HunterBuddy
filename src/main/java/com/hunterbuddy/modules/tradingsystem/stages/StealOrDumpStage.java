package com.hunterbuddy.modules.tradingsystem.stages;

import com.hunterbuddy.util.QuietClicks;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import com.hunterbuddy.modules.tradingsystem.ExperienceTraderModule;
import com.hunterbuddy.modules.tradingsystem.utilites.Stage;


public class StealOrDumpStage extends Stage {
    private final Type type;
    private boolean isFirstTick = true;
    private int nextSlot = 0;
    private int stoleCount = 0;
    private long lastMovingTime = 0;

    public StealOrDumpStage(ExperienceTraderModule main, Type type) {
        super(main);
        this.type = type;
    }

    @Override
    public boolean work() {
        if (!super.mainModule.isTimeToClear()) return true;
        if (!(client.currentScreen instanceof GenericContainerScreen chestScreen)) return finish();
        if (type == Type.STEAL && this.stoleCount >= type.stacksCount()) return finish();
        ScreenHandler handler = chestScreen.getScreenHandler();
        int playerInvOffset = SlotUtils.indexToId(SlotUtils.MAIN_START);
        int end = type == Type.STEAL ? playerInvOffset : playerInvOffset + 4 * 9;
        if (this.isFirstTick) {
            this.nextSlot = type == Type.STEAL ? 0 : playerInvOffset;
            this.isFirstTick = false;
        }
        while (this.nextSlot < end) {
            Slot slot = handler.getSlot(this.nextSlot);
            if (!slot.hasStack() || !slot.getStack().getItem().equals(type.getItem())) {
                this.nextSlot++;
                continue;
            }
            if (System.currentTimeMillis() - this.lastMovingTime < super.mainModule.stealAndDumpDelay.get()) return false;
            if (!QuietClicks.readyOrSettle()) return false;

            InvUtils.shiftClick().slotId(this.nextSlot++);
            this.lastMovingTime = System.currentTimeMillis();
            if (type == Type.STEAL && ++this.stoleCount >= type.stacksCount()) return finish();
            return this.nextSlot >= end ? finish() : false;
        }
        return finish();
    }

    private boolean finish() {
        if (client.currentScreen != null) client.currentScreen.close();
        return true;
    }

    @Override
    public void reset() {
        this.isFirstTick = true;
        this.nextSlot = 0;
        this.stoleCount = 0;
        this.lastMovingTime = 0;
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
