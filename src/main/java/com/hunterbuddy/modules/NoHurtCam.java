package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;

public class NoHurtCam extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> disableHurtCam = this.sgGeneral
      .add(
         new Builder().name("Disable Hurt Cam").description("Disables the camera shake/tilt when taking damage.")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> disableRedOverlay = this.sgGeneral
      .add(
         new Builder().name("Disable Red Overlay").description("Disables the red overlay when taking damage.")
               .defaultValue(false)
            .build()
      );

   public NoHurtCam() {
      super(HunterBuddyAddon.UTILITY_CATEGORY, "NoHurtCam", "Removes the hurt camera tilt and shake effect when taking damage.");
   }

   public boolean shouldDisableHurtCam() {
      return this.isActive() && (Boolean)this.disableHurtCam.get();
   }

   public boolean shouldDisableRedOverlay() {
      return this.isActive() && (Boolean)this.disableRedOverlay.get();
   }
}
