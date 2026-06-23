/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package org.linytech.trader.modules.tradingsystem.stages;

import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.village.TradeOffer;
import org.linytech.trader.modules.tradingsystem.ExperienceTraderModule;
import org.linytech.trader.modules.tradingsystem.utilites.Stage;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

public class BuyingStage extends Stage {
    private TradeOffer targetOffer;
    private Integer targetIndex;

    public BuyingStage(ExperienceTraderModule main) {
        super(main);
    }

    private int phase = 0;
    private int ignoredTicks = 0;
    @Override
    public boolean work() {
        MerchantScreen tradeScreen = Objects.requireNonNull((MerchantScreen) client.currentScreen);

        if (this.targetOffer == null) {
            this.targetOffer = findTradeOffer(tradeScreen);

            if (this.targetOffer == null) {
                super.mainModule.nextRound("У жителя отсутствуют нужные торги.");
                return false;
            }

            this.targetIndex = tradeScreen.getScreenHandler().getRecipes().indexOf(this.targetOffer);

            setSelectedIndex(tradeScreen, this.targetIndex);

            if (super.mainModule.doOutput()) {
                super.mainModule.info("Нужный трейд установлен.");
            }
        }

        switch (this.phase) {
            case 0 -> {
                fillSellContents(tradeScreen);
                this.phase++;
                return false;
            }
            case 1 -> {
                Item sellItemType = tradeScreen.getScreenHandler().getSlot(2).getStack().getItem();

                if (sellItemType.equals(Items.EXPERIENCE_BOTTLE)) {
                    buyProcess(tradeScreen);
                    resetPhase();
                } else if (!sellItemType.equals(Items.AIR)) {
                    throw new RuntimeException("Invalid target trade offer");
                } else {
                    this.ignoredTicks++;

                    if (this.ignoredTicks >= super.mainModule.maxServerTimeout.get() * 20) {
                        super.mainModule.nextRound("Время ожидания товара в слоте покупки вышло.");
                        return false;
                    }
                }

                return this.targetOffer.isDisabled();
            }
            default -> throw new IndexOutOfBoundsException(phase);
        }
    }

    private void resetPhase() {
        this.ignoredTicks = 0;
        this.phase = 0;
    }

    private TradeOffer findTradeOffer(MerchantScreen tradeScreen) {
        ArrayList<TradeOffer> offers = tradeScreen.getScreenHandler().getRecipes();

        // Log all available offers for debugging
        System.out.println("Available trade offers:");
        offers.forEach(offer -> {
            System.out.println("Sell: " + offer.copySellItem().getItem() +
                " First Buy: " + offer.getOriginalFirstBuyItem().getItem() +
                " Second Buy: " + (offer.getSecondBuyItem() == null ? "None" : offer.getSecondBuyItem().getItem()) +
                " Disabled: " + offer.isDisabled());
        });

        Optional<TradeOffer> targetOffer = offers.parallelStream()
            .filter(tradeOffer -> !tradeOffer.isDisabled())
            .filter(tradeOffer -> tradeOffer.copySellItem().getItem().equals(Items.EXPERIENCE_BOTTLE))
            .filter(tradeOffer -> tradeOffer.getAdjustedFirstBuyItem().getItem().equals(Items.EMERALD))
            .filter(tradeOffer -> tradeOffer.getAdjustedFirstBuyItem().getCount() <= super.mainModule.maxPrice.get())
            .filter(tradeOffer -> tradeOffer.getSecondBuyItem().getItem().equals(Items.AIR))
            .findAny();

        if (targetOffer.isPresent()) {
            System.out.println("Target offer found: Selling EXPERIENCE_BOTTLE for EMERALDS");
        } else {
            System.out.println("No valid trade offer found for EXPERIENCE_BOTTLE.");
        }

        return targetOffer.orElse(null);
    }

    private void fillSellContents(MerchantScreen screen) {
        screen.syncRecipeIndex();
    }

    private void setSelectedIndex(MerchantScreen screen, int index) {
        screen.selectedIndex = index;
    }

    private void buyProcess(MerchantScreen screen) {
        Slot slot = screen.getScreenHandler().getSlot(2);

        if (slot.hasStack()) {
            System.out.println("Attempting to buy: " + slot.getStack().getItem());
        } else {
            System.out.println("No item in slot 2 to buy.");
        }

        try {
            screen.onMouseClick(
                slot,
                -1,
                0,
                SlotActionType.QUICK_MOVE
            );
            System.out.println("Successfully executed the purchase.");
        } catch (Exception e) {
            System.err.println("Failed to execute the purchase: " + e.getMessage());
        }
    }


    public void reset() {
        this.targetOffer = null;
        this.targetIndex = null;
        this.phase = 0;
    }
}
