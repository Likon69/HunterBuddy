package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;

public class EntityList extends HudElement {
   public static final HudElementInfo<EntityList> INFO = new HudElementInfo(HunterBuddyAddon.HUD_GROUP, "EntityList", "Displays nearby entities in a list.", EntityList::new);
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> showTitle = this.sgGeneral
      .add(new Builder().name("show-title").description("Display the HUD title.").defaultValue(true).build());
   private final Setting<Boolean> showItems = this.sgGeneral
      .add(new Builder().name("show-items").description("Show dropped items.").defaultValue(true).build());
   private final Setting<Boolean> showMobs = this.sgGeneral
      .add(new Builder().name("show-mobs").description("Show mobs.").defaultValue(true).build());
   private final Setting<Boolean> showPlayers = this.sgGeneral
      .add(new Builder().name("show-players").description("Show players.").defaultValue(true).build());
   private final Setting<Boolean> showProjectiles = this.sgGeneral
      .add(
         new Builder().name("show-projectiles").description("Show thrown projectiles (ender pearls, arrows, etc).")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> showRockets = this.sgGeneral
      .add(new Builder().name("show-rockets").description("Show firework rockets.").defaultValue(false).build());
   private final Setting<Double> maxDistance = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("max-distance")
               .description("Maximum distance to show entities.")
            .defaultValue(100.0)
            .min(0.0)
            .sliderRange(0.0, 500.0)
            .build()
      );
   private final Setting<Boolean> sortByDistance = this.sgGeneral
      .add(new Builder().name("sort-by-distance").description("Sort entities by distance.").defaultValue(true).build());
   private final Setting<Boolean> showDistance = this.sgGeneral
      .add(new Builder().name("show-distance").description("Show distance to entities.").defaultValue(true).build());
   private final Setting<Boolean> includeYLevel = this.sgGeneral
      .add(
         new Builder().name("include-y-level").description("Include Y level in distance calculation (3D distance).")
               .defaultValue(false)
            .build()
      );
   private final Setting<Double> textScale = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("text-scale")
               .description("Scale of the text.")
            .defaultValue(1.0)
            .min(0.1)
            .sliderRange(0.1, 3.0)
            .build()
      );
   private final Setting<Boolean> textShadow = this.sgGeneral
      .add(new Builder().name("text-shadow").description("Render shadow behind the text.").defaultValue(true).build());
   private final Setting<SettingColor> playerColor = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("player-color")
               .description("Color for player entities.")
            .defaultValue(new SettingColor(0, 255, 0, 255)).build()
      );
   private final Setting<SettingColor> mobColor = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("mob-color")
               .description("Color for mob entities.")
            .defaultValue(new SettingColor(255, 0, 0, 255)).build()
      );
   private final Setting<SettingColor> itemColor = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("item-color")
               .description("Color for item entities.")
            .defaultValue(new SettingColor(255, 255, 255, 255)).build()
      );
   private final Setting<SettingColor> projectileColor = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("projectile-color")
               .description("Color for projectile entities.")
            .defaultValue(new SettingColor(150, 100, 255, 255)).build()
      );
   private final Setting<SettingColor> rocketColor = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("rocket-color")
               .description("Color for firework rockets.")
            .defaultValue(new SettingColor(255, 165, 0, 255)).build()
      );

   public EntityList() {
      super(INFO);
   }

   public void render(HudRenderer renderer) {
      if (MeteorClient.mc.world != null && MeteorClient.mc.player != null) {
         Map<String, EntityList.Aggregated> map = new HashMap<>();

         for (Entity entity : MeteorClient.mc.world.getEntities()) {
            if (entity != MeteorClient.mc.player) {
               double dx = entity.getX() - MeteorClient.mc.player.getX();
               double dz = entity.getZ() - MeteorClient.mc.player.getZ();
               double distance;
               if ((Boolean)this.includeYLevel.get()) {
                  double dy = entity.getY() - MeteorClient.mc.player.getY();
                  distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
               } else {
                  distance = Math.sqrt(dx * dx + dz * dz);
               }

               if (!(distance > (Double)this.maxDistance.get())) {
                  boolean isRocket = entity instanceof FireworkRocketEntity;
                  if (!isRocket || (Boolean)this.showRockets.get()) {
                     boolean isItem = entity instanceof ItemEntity && (Boolean)this.showItems.get();
                     boolean isMob = entity instanceof MobEntity && (Boolean)this.showMobs.get();
                     boolean isPlayer = entity instanceof PlayerEntity && (Boolean)this.showPlayers.get();
                     boolean isProjectile = !isRocket && (entity instanceof ProjectileEntity || entity instanceof EnderPearlEntity) && (Boolean)this.showProjectiles.get();
                     if (isItem || isMob || isPlayer || isProjectile || isRocket) {
                        String name = this.getEntityName(entity);
                        SettingColor color = this.getEntityColor(entity);
                        EntityList.Aggregated agg = map.get(name);
                        if (agg == null) {
                           agg = new EntityList.Aggregated();
                           agg.name = name;
                           agg.color = color;
                           agg.minDist = distance;
                           if (isItem) {
                              agg.count = ((ItemEntity)entity).getStack().getCount();
                           } else {
                              agg.count = 1;
                           }

                           map.put(name, agg);
                        } else {
                           agg.minDist = Math.min(agg.minDist, distance);
                           if (isItem) {
                              agg.count = agg.count + ((ItemEntity)entity).getStack().getCount();
                           } else {
                              agg.count++;
                           }
                        }
                     }
                  }
               }
            }
         }

         List<EntityList.Aggregated> aggregatedList = new ArrayList<>(map.values());
         if ((Boolean)this.sortByDistance.get()) {
            aggregatedList.sort(Comparator.comparingDouble(a -> a.minDist));
         }

         double curX = this.x;
         double curY = this.y;
         double maxWidth = 0.0;
         double height = 0.0;
         double textHeight = renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get());
         double spacing = 2.0;
         if ((Boolean)this.showTitle.get()) {
            String title = "Entity List";
            double titleWidth = renderer.textWidth(title, (Boolean)this.textShadow.get(), (Double)this.textScale.get());
            renderer.text(title, curX, curY, (Color)this.playerColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
            curY += textHeight + spacing;
            height += textHeight + spacing;
            maxWidth = Math.max(maxWidth, titleWidth);
         }

         for (EntityList.Aggregated agg : aggregatedList) {
            String text = agg.name;
            if (agg.count > 1) {
               text = text + " x" + agg.count;
            }

            if ((Boolean)this.showDistance.get()) {
               text = text + " (" + (int)agg.minDist + "m)";
            }

            double textWidth = renderer.textWidth(text, (Boolean)this.textShadow.get(), (Double)this.textScale.get());
            renderer.text(text, curX, curY, agg.color, (Boolean)this.textShadow.get(), (Double)this.textScale.get());
            curY += textHeight + spacing;
            height += textHeight + spacing;
            maxWidth = Math.max(maxWidth, textWidth);
         }

         this.setSize(maxWidth, height > spacing ? height - spacing : 0.0);
      } else {
         if (this.isInEditor()) {
            renderer.text("Entity List", this.x, this.y, (Color)this.playerColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
            this.setSize(
               renderer.textWidth("Entity List", (Boolean)this.textShadow.get(), (Double)this.textScale.get()),
               renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get())
            );
         }
      }
   }

   private String getEntityName(Entity entity) {
      if (entity instanceof ItemEntity item) {
         return item.getStack().getName().getString();
      } else if (entity instanceof PlayerEntity player) {
         return player.getName().getString();
      } else if (entity instanceof FireworkRocketEntity) {
         return "Firework Rocket";
      } else if (entity instanceof EnderPearlEntity) {
         return "Ender Pearl";
      } else if (entity instanceof TridentEntity) {
         return "Trident";
      } else if (entity instanceof ProjectileEntity) {
         String className = entity.getClass().getSimpleName();
         return className.replace("Entity", "").replaceAll("([A-Z])", " $1").trim();
      } else {
         return entity.getType().getName().getString();
      }
   }

   private SettingColor getEntityColor(Entity entity) {
      if (entity instanceof ItemEntity) {
         return (SettingColor)this.itemColor.get();
      } else if (entity instanceof MobEntity) {
         return (SettingColor)this.mobColor.get();
      } else if (entity instanceof PlayerEntity) {
         return (SettingColor)this.playerColor.get();
      } else if (entity instanceof FireworkRocketEntity) {
         return (SettingColor)this.rocketColor.get();
      } else {
         return !(entity instanceof ProjectileEntity) && !(entity instanceof EnderPearlEntity)
            ? new SettingColor(255, 255, 255, 255)
            : (SettingColor)this.projectileColor.get();
      }
   }

   private static class Aggregated {
      String name;
      int count;
      double minDist;
      SettingColor color;
   }
}
