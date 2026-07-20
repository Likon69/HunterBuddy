package com.hunterbuddy.commands.logistics;

import com.hunterbuddy.modules.logistics.StashMover;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;

public class SetInput extends Command {
   public SetInput() {
      super("setinput", "Start input area selection for StashMover module", new String[0]);
   }

   public void build(LiteralArgumentBuilder<CommandSource> builder) {
      builder.executes(context -> {
         if (mc.player == null) {
            this.error("Player is null!", new Object[0]);
            return 0;
         }

         StashMover module = (StashMover)Modules.get().get(StashMover.class);
         if (module != null) {
            if (module.isSelecting()) {
               module.cancelSelection();
               this.info("Previous selection cancelled", new Object[0]);
            }

            module.startInputSelection();
            this.info("\u00a7aInput area selection started!", new Object[0]);
            this.info("\u00a7eLeft-click the first corner block", new Object[0]);
            this.info("\u00a77Press \u00a7cESC \u00a77to cancel selection", new Object[0]);
         } else {
            this.error("StashMover module not found!", new Object[0]);
         }

         return 1;
      });
   }
}
