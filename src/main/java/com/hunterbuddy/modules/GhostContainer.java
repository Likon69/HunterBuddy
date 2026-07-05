package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;

public class GhostContainer extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   public final Setting<String> buttonText = this.sgGeneral
      .add(
         new Builder().name("button-text").description("Label shown on the ghost-close button.").defaultValue("Ghost Close")
            .build()
      );
   public final Setting<Integer> buttonWidth = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("button-width")
                  .description("Width of the ghost-close button.")
               .defaultValue(80)
            .min(20)
            .sliderRange(20, 160)
            .build()
      );
   public final Setting<Integer> offsetX = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("offset-x")
                  .description("Horizontal offset from the container's top-left corner.")
               .defaultValue(0)
            .sliderRange(-200, 200)
            .build()
      );
   public final Setting<Integer> offsetY = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("offset-y")
                  .description("Vertical offset from the container's top-left corner.")
               .defaultValue(-22)
            .sliderRange(-200, 200)
            .build()
      );
   public final Setting<Boolean> interceptClosePacket = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("intercept-close-packet")
                  .description(
                     "Cancel every CloseHandledScreenC2SPacket while active, so ESC/E also ghost-close. Catch-all even when other code calls closeHandledScreen()."
                  ).defaultValue(false)
            .build()
      );
   public final Setting<Boolean> notify = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("notify")
                  .description("Print a chat message when a container is left open server-side.")
               .defaultValue(true)
            .build()
      );

   public GhostContainer() {
      super(
         HunterBuddyAddon.HUNTER_BUDDY_CATEGORY,
         "ghost-container",
         "Adds a button to container GUIs that exits without sending the close packet, leaving the container open server-side (Paper/Folia desync)."
      );
   }

   public boolean canGhost() {
      if (this.mc.player == null || this.mc.world == null) {
         return false;
      }

      if (!(this.mc.currentScreen instanceof HandledScreen)) {
         return false;
      }

      ScreenHandler handler = this.mc.player.currentScreenHandler;
      return handler != null && !(handler instanceof PlayerScreenHandler);
   }

   public void ghostClose() {
      if (this.canGhost()) {
         int syncId = this.mc.player.currentScreenHandler.syncId;
         this.mc.setScreen(null);
         if ((Boolean)this.notify.get()) {
            this.info("Container left open server-side (syncId (highlight)%d(default)).", new Object[]{syncId});
         }
      }
   }

   @EventHandler
   private void onSend(Send event) {
      if ((Boolean)this.interceptClosePacket.get()) {
         if (event.packet instanceof CloseHandledScreenC2SPacket) {
            if (this.mc.player != null && !(this.mc.player.currentScreenHandler instanceof PlayerScreenHandler)) {
               event.cancel();
            }
         }
      }
   }
}
