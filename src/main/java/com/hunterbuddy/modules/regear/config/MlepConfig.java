package com.hunterbuddy.modules.regear.config;

import com.hunterbuddy.modules.regear.util.EnemyColorManager;
import com.hunterbuddy.modules.regear.util.Utils;
import java.util.List;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public class MlepConfig {
   public static Setting<MlepConfig.TurnSpeedMode> rotationTurnSpeedMode = new Builder<MlepConfig.TurnSpeedMode>().defaultValue(MlepConfig.TurnSpeedMode.Normal).build();
   public static Setting<Double> rotationTurnSpeedCustom = new meteordevelopment.meteorclient.settings.DoubleSetting.Builder().defaultValue(45.0).build();
   public static Setting<Boolean> illegalDisconnectButtonSetting = new meteordevelopment.meteorclient.settings.BoolSetting.Builder().build();
   public static Setting<Boolean> disableMeteorClientTelemetry = new meteordevelopment.meteorclient.settings.BoolSetting.Builder().build();
   public static Setting<Boolean> disableMeteorCapes = new meteordevelopment.meteorclient.settings.BoolSetting.Builder().build();
   public static Setting<Boolean> ignoreOverlayMessages = new meteordevelopment.meteorclient.settings.BoolSetting.Builder().build();
   public static Setting<List<String>> overlayMessageFilter = new meteordevelopment.meteorclient.settings.StringListSetting.Builder().build();
   public static Setting<Utils.IllegalDisconnectMethod> illegalDisconnectMethodSetting = new Builder<Utils.IllegalDisconnectMethod>()
         .defaultValue(Utils.IllegalDisconnectMethod.Chat)
      .build();
   public static Setting<SettingColor> enemyColorSetting = new meteordevelopment.meteorclient.settings.ColorSetting.Builder().build();

   public static double getRotationTurnSpeed() {
      if (rotationTurnSpeedMode == null) {
         return 45.0;
      }

      MlepConfig.TurnSpeedMode mode = (MlepConfig.TurnSpeedMode)rotationTurnSpeedMode.get();
      return mode == MlepConfig.TurnSpeedMode.Custom ? (Double)rotationTurnSpeedCustom.get() : mode.speed;
   }

   public static void initialize() {
      SettingGroup sgMlep = Config.get().settings.createGroup("Mlep");
      illegalDisconnectButtonSetting = sgMlep.add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("illegal-disconnect-button")
                  .description("Adds a button to the main menu that forces the server to kick you when pressed.")
               .defaultValue(false)
            .build()
      );
      illegalDisconnectMethodSetting = sgMlep.add(
         new Builder<Utils.IllegalDisconnectMethod>().name("illegal-disconnect-method").description("The method to use to cause the server to kick you.")
               .defaultValue(Utils.IllegalDisconnectMethod.Chat)
            .build()
      );
      disableMeteorClientTelemetry = sgMlep.add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("disable-meteor-telemetry")
                  .description("Disables sending periodic telemetry pings to meteorclient.com for their online player count api.")
               .defaultValue(true)
            .build()
      );
      disableMeteorCapes = sgMlep.add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("disable-meteor-capes")
                  .description("Disables fetching and rendering Meteor Client custom capes.")
               .defaultValue(false)
            .build()
      );
      enemyColorSetting = sgMlep.add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("enemy-color")
               .description("Color for enemy players in nametags and tablist.")
            .defaultValue(new SettingColor(255, 0, 0, 255)).build()
      );
      EnemyColorManager.setEnemyColorSetting(enemyColorSetting);
      rotationTurnSpeedMode = sgMlep.add(
         new Builder<MlepConfig.TurnSpeedMode>().name("rotation-turn-speed")
                  .description("How fast smooth rotations turn each tick (degrees). Lower = more human-like, higher = snappier.")
               .defaultValue(MlepConfig.TurnSpeedMode.Normal)
            .build()
      );
      rotationTurnSpeedCustom = sgMlep.add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("rotation-turn-speed-custom")
                  .description("Custom per-tick turn cap in degrees (used when turn speed is set to Custom).")
               .defaultValue(45.0)
               .min(1.0)
               .max(180.0)
               .sliderRange(5.0, 180.0)
               .visible(() -> rotationTurnSpeedMode.get() == MlepConfig.TurnSpeedMode.Custom)
            .build()
      );
      ignoreOverlayMessages = sgMlep.add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("ignore-overlay-messages")
                  .description("Overlay messages will be ignored if they match any of the provided filters.")
               .defaultValue(false)
            .build()
      );
      overlayMessageFilter = sgMlep.add(
         new meteordevelopment.meteorclient.settings.StringListSetting.Builder()
                        .name("overlay-message-filter")
                     .description("Overlay messages will be ignored if they match any of the provided filters.")
                  .defaultValue(List.of("2b2t.org")).visible(ignoreOverlayMessages::get)
            .build()
      );
   }

   public enum TurnSpeedMode {
      Slow(25.0),
      Normal(45.0),
      Fast(90.0),
      Instant(180.0),
      Custom(45.0);

      public final double speed;

      TurnSpeedMode(double speed) {
         this.speed = speed;
      }
   }
}
