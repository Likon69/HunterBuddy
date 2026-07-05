package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.modules.WaypointFollower;
import java.util.ArrayList;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.world.Dimension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.world.container.MinimapWorldRootContainer;
import xaero.map.WorldMapSession;
import xaero.map.gui.GuiMap;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.dropdown.rightclick.RightClickOption;
import xaero.map.mods.SupportMods;
import xaero.map.world.MapWorld;
import xaeroplus.settings.Settings;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

@Mixin(value = GuiMap.class, remap = false)
public abstract class MixinGuiMapWaypointFollower implements IRightClickableElement {
   @Shadow
   private int rightClickX;
   @Shadow
   private int rightClickY;
   @Shadow
   private int rightClickZ;
   @Shadow
   private double rightClickCoordinateScale;
   @Shadow
   private int mouseBlockPosX;
   @Shadow
   private int mouseBlockPosY;
   @Shadow
   private int mouseBlockPosZ;
   @Shadow
   private double mouseBlockCoordinateScale;

   @Inject(method = "getRightClickOptions", at = @At("RETURN"), remap = false)
   public void getRightClickOptionsInject(CallbackInfoReturnable<ArrayList<RightClickOption>> cir) {
      ArrayList<RightClickOption> options = (ArrayList<RightClickOption>)cir.getReturnValue();
      final WaypointFollower waypointFollower = (WaypointFollower)Modules.get().get(WaypointFollower.class);
      if (waypointFollower != null) {
         options.add(new RightClickOption("Add Hunt Waypoint", options.size(), this) {
            public void onAction(Screen screen) {
               MixinGuiMapWaypointFollower.this.createWaypointFromRightClick(waypointFollower);
            }
         });
      }
   }

   @Inject(method = "onInputPress", at = @At("HEAD"), cancellable = true, remap = false)
   public void onKeyPress(@Coerce Object type, int code, CallbackInfoReturnable<Boolean> cir) {
      if ("KEYSYM".equals(type.toString())) {
         Screen screen = (Screen)(Object)this;
         Element focused = screen.getFocused();
         if (!(focused instanceof TextFieldWidget)) {
            if (focused == null || !focused.getClass().getName().contains("TextField")) {
               WaypointFollower waypointFollower = (WaypointFollower)Modules.get().get(WaypointFollower.class);
               if (waypointFollower != null) {
                  if (code == waypointFollower.getAddWaypointKeyCode()) {
                     this.rightClickX = this.mouseBlockPosX;
                     this.rightClickY = this.mouseBlockPosY;
                     this.rightClickZ = this.mouseBlockPosZ;
                     this.rightClickCoordinateScale = this.mouseBlockCoordinateScale;
                     this.createWaypointFromRightClick(waypointFollower);
                     cir.setReturnValue(true);
                  }
               }
            }
         }
      }
   }

   private void createWaypointFromRightClick(WaypointFollower waypointFollower) {
      try {
         this.createWaypoint(this.rightClickX, this.rightClickZ, waypointFollower);
      } catch (Exception e) {
         if (waypointFollower.shouldShowChatMessages()) {
            waypointFollower.error("Error creating waypoint: " + e.getMessage(), new Object[0]);
         }
      }
   }

   private void createWaypoint(int x, int z, WaypointFollower waypointFollower) {
      try {
         MinimapSession minimapSession = (MinimapSession)BuiltInHudModules.MINIMAP.getCurrentSession();
         if (minimapSession == null) {
            if (waypointFollower.shouldShowChatMessages()) {
               waypointFollower.error("MinimapSession is null", new Object[0]);
            }

            return;
         }

         RegistryKey<World> viewedDimension = this.getViewedDimension();
         int finalX = x;
         int finalZ = z;
         MinimapWorld targetMinimapWorld = minimapSession.getWorldManager().getCurrentWorld();
         boolean preferOverworldWaypoints = false;

         try {
            preferOverworldWaypoints = Settings.REGISTRY.owAutoWaypointDimension.get();
         } catch (Exception var16) {
         }

         if (preferOverworldWaypoints && viewedDimension == World.NETHER) {
            finalX = x * 8;
            finalZ = z * 8;
            MinimapWorldRootContainer rootContainer = minimapSession.getWorldManager().getCurrentRootContainer();

            for (MinimapWorld world : rootContainer.getWorlds()) {
               try {
                  String dimPath = world.getDimId().getValue().getPath();
                  if ("overworld".equals(dimPath)) {
                     targetMinimapWorld = world;
                     break;
                  }
               } catch (Exception var18) {
               }
            }
         }

         if (targetMinimapWorld == null) {
            if (waypointFollower.shouldShowChatMessages()) {
               waypointFollower.error("MinimapWorld is null", new Object[0]);
            }

            return;
         }

         Dimension storageDim = preferOverworldWaypoints && viewedDimension == World.NETHER
            ? Dimension.Overworld
            : this.getDimensionFromRegistryKey(viewedDimension);
         int y = this.getNormalizedY(storageDim);
         String waypointName = waypointFollower.getWaypointPrefix() + waypointFollower.getWaypointCount();
         Waypoint huntWaypoint = new Waypoint(finalX, y, finalZ, waypointName, "HW", WaypointColor.AQUA, WaypointPurpose.NORMAL, false);
         WaypointSet currentSet = targetMinimapWorld.getCurrentWaypointSet();
         if (currentSet == null) {
            if (waypointFollower.shouldShowChatMessages()) {
               waypointFollower.error("Current waypoint set is null", new Object[0]);
            }

            return;
         }

         currentSet.add(huntWaypoint);

         try {
            minimapSession.getWorldManagerIO().saveWorld(targetMinimapWorld);
         } catch (Exception saveEx) {
            if (waypointFollower.shouldShowChatMessages()) {
               waypointFollower.error("Failed to save waypoint: " + saveEx.getMessage(), new Object[0]);
            }
         }

         SupportMods.xaeroMinimap.requestWaypointsRefresh();
         if (waypointFollower.isActive()) {
            waypointFollower.addWaypointToTrack(finalX, y, finalZ);
         }

         if (waypointFollower.shouldShowChatMessages()) {
            waypointFollower.info("Created waypoint '" + waypointName + "' at " + finalX + ", " + finalZ, new Object[0]);
         }
      } catch (Exception e) {
         if (waypointFollower.shouldShowChatMessages()) {
            waypointFollower.error("Error creating waypoint: " + e.getMessage(), new Object[0]);
         }
      }
   }

   private RegistryKey<World> getViewedDimension() {
      try {
         WorldMapSession session = WorldMapSession.getCurrentSession();
         if (session != null) {
            MapWorld mapWorld = session.getMapProcessor().getMapWorld();
            if (mapWorld != null) {
               RegistryKey<World> dimId = mapWorld.getCurrentDimensionId();
               if (dimId != null) {
                  String dimPath = dimId.getValue().getPath();
                  if ("the_nether".equals(dimPath)) {
                     return World.NETHER;
                  }

                  if ("the_end".equals(dimPath)) {
                     return World.END;
                  }
               }
            }
         }
      } catch (Exception var5) {
      }

      return World.OVERWORLD;
   }

   private Dimension getDimensionFromRegistryKey(RegistryKey<World> key) {
      if (key == World.NETHER) {
         return Dimension.Nether;
      } else {
         return key == World.END ? Dimension.End : Dimension.Overworld;
      }
   }

   private int getNormalizedY(Dimension dimension) {
      switch (dimension) {
         case Nether:
            return 120;
         case End:
            return 70;
         case Overworld:
         default:
            return 320;
      }
   }
}
