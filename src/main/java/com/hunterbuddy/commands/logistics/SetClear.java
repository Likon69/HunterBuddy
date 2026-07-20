package com.hunterbuddy.commands.logistics;

import com.hunterbuddy.modules.logistics.StashMover;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;

public class SetClear extends Command {
   public SetClear() {
      super("setclear", "Clear all StashMover area selections", new String[0]);
   }

   public void build(LiteralArgumentBuilder<CommandSource> builder) {
      builder.executes(context -> {
         StashMover module = (StashMover)Modules.get().get(StashMover.class);
         if (module != null) {
            module.clearAreas();
            this.info("\u00a7cAll StashMover areas have been cleared", new Object[0]);
            String prefix = (String)Config.get().prefix.get();
            this.info("\u00a77Use \u00a7f" + prefix + "setinput \u00a77and \u00a7f" + prefix + "setoutput \u00a77to select new areas", new Object[0]);
         } else {
            this.error("StashMover module not found!", new Object[0]);
         }

         return 1;
      });
   }
}
