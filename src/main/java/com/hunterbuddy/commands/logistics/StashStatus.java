package com.hunterbuddy.commands.logistics;

import com.hunterbuddy.modules.logistics.StashMover;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;

public class StashStatus extends Command {
   public StashStatus() {
      super("stashstatus", "Check StashMover areas and configuration", new String[0]);
   }

   public void build(LiteralArgumentBuilder<CommandSource> builder) {
      builder.executes(context -> {
         if (mc.player == null) {
            return 0;
         }

         StashMover module = (StashMover)Modules.get().get(StashMover.class);
         if (module != null) {
            String prefix = (String)Config.get().prefix.get();
            this.info("\u00a76=== StashMover Status ===", new Object[0]);
            if (module.isActive()) {
               this.info("\u00a7aModule: \u00a7fACTIVE", new Object[0]);
               this.info("\u00a77State: \u00a7f" + module.getCurrentState(), new Object[0]);
               this.info("\u00a77Items moved: \u00a7f" + module.getItemsTransferred(), new Object[0]);
               this.info("\u00a77Containers processed: \u00a7f" + module.getContainersProcessed(), new Object[0]);
            } else {
               this.info("\u00a7cModule: \u00a7fINACTIVE", new Object[0]);
               this.info("\u00a77Use \u00a7f" + prefix + "stash-mover \u00a77to activate", new Object[0]);
            }

            this.info("", new Object[0]);
            boolean hasInput = module.hasInputArea();
            boolean hasOutput = module.hasOutputArea();
            if (hasInput) {
               this.info("\u00a7aInput Area: \u00a7fSET", new Object[0]);
               this.info("\u00a77  Containers: \u00a7f" + module.getInputContainerCount(), new Object[0]);
            } else {
               this.info("\u00a7cInput Area: \u00a7fNOT SET", new Object[0]);
               this.info("\u00a77  Use \u00a7f" + prefix + "setinput \u00a77to select", new Object[0]);
            }

            if (hasOutput) {
               this.info("\u00a7bOutput Area: \u00a7fSET", new Object[0]);
               this.info("\u00a77  Containers: \u00a7f" + module.getOutputContainerCount(), new Object[0]);
            } else {
               this.info("\u00a7cOutput Area: \u00a7fNOT SET", new Object[0]);
               this.info("\u00a77  Use \u00a7f" + prefix + "setoutput \u00a77to select", new Object[0]);
            }

            if (hasInput && hasOutput) {
               this.info("", new Object[0]);
               this.info("\u00a7aReady to use! \u00a77Enable module to start.", new Object[0]);
            }
         } else {
            this.error("StashMover module not found!", new Object[0]);
         }

         return 1;
      });
   }
}
