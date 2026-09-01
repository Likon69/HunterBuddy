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
               MixinGuiMapWaypointFollower.this.createWaypointFromRightClick(waypointFollower, false);
            }
         });
         options.add(new RightClickOption("Add Route Mark", options.size(), this) {
            public void onAction(Screen screen) {
               MixinGuiMapWaypointFollower.this.createWaypointFromRightClick(waypointFollower, true);
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
                  // Meteor fires no keybind with a screen open, so the fill is
                  // triggered from here, where the map is certain to be open.
                  if (code != -1 && code == waypointFollower.getSeedRouteKeyCode()) {
                     waypointFollower.seedRouteFromMap();
                     cir.setReturnValue(true);
                     return;
                  }

                  boolean routeMark = code == waypointFollower.getAddRouteKeyCode();
                  if (routeMark || code == waypointFollower.getAddWaypointKeyCode()) {
                     this.rightClickX = this.mouseBlockPosX;
                     this.rightClickY = this.mouseBlockPosY;
                     this.rightClickZ = this.mouseBlockPosZ;
                     this.rightClickCoordinateScale = this.mouseBlockCoordinateScale;
                     this.createWaypointFromRightClick(waypointFollower, routeMark);
                     cir.setReturnValue(true);
                  }
               }
            }
         }
      }
   }

   private void createWaypointFromRightClick(WaypointFollower waypointFollower, boolean routeMark) {
      try {
         this.createWaypoint(this.rightClickX, this.rightClickZ, waypointFollower, routeMark);
      } catch (Exception e) {
         if (waypointFollower.shouldShowChatMessages()) {
            waypointFollower.error("Error creating waypoint: " + e.getMessage(), new Object[0]);
         }
      }
   }

   private void createWaypoint(int x, int z, WaypointFollower waypointFollower, boolean routeMark) {
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
         MinimapWorld targetMinimapWorld;
         Dimension storageDim;

         if (viewedDimension == World.END) {
            targetMinimapWorld = minimapSession.getWorldManager().getCurrentWorld();
            storageDim = Dimension.End;
         } else {
            // Always the overworld waypoint world, in overworld coordinates,
            // whatever the map's dimension dropdown says. The follower reads
            // that world and that frame and nothing else; and this world is
            // asked for by name rather than searched in the dropdown's own
            // container, because with "Nether" selected that container holds
            // no overworld -- the search used to fail and the point, already
            // multiplied by eight, landed in the Nether world eight times too far.
            // Resolved, never created: XaeroPlus's own lookup registers a new
            // world when the selected container has no overworld, and that is
            // where the second "Overworld" in the list came from.
            targetMinimapWorld = WaypointFollower.resolveOverworldWaypointWorld();

            if (targetMinimapWorld == null) {
               MinimapWorldRootContainer rootContainer = minimapSession.getWorldManager().getCurrentRootContainer();

               for (MinimapWorld world : rootContainer.getWorlds()) {
                  try {
                     if ("overworld".equals(world.getDimId().getValue().getPath())) {
                        targetMinimapWorld = world;
                        break;
                     }
                  } catch (Exception ignored) {
                  }
               }
            }

            if (viewedDimension == World.NETHER) {
               finalX = x * 8;
               finalZ = z * 8;
            }

            storageDim = Dimension.Overworld;
         }

         if (targetMinimapWorld == null) {
            waypointFollower.error("Could not find the overworld waypoint world; nothing placed. Select Overworld in the map's waypoint dimension and try again.", new Object[0]);
            return;
         }

         int y = this.getNormalizedY(storageDim);
         // A route mark is a control point for seed-route, not a destination:
         // its own prefix, its own numbering from one, and it is never handed
         // to the follower's tracking below.
         String waypointName = routeMark
            ? waypointFollower.getRoutePrefix() + waypointFollower.getNextRouteNumber()
            : waypointFollower.getWaypointPrefix() + waypointFollower.getWaypointCount();
         Waypoint huntWaypoint = new Waypoint(finalX, y, finalZ, waypointName,
            routeMark ? "RT" : "HW",
            routeMark ? WaypointColor.YELLOW : WaypointColor.AQUA, WaypointPurpose.NORMAL, false);
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
         if (!routeMark && waypointFollower.isActive()) {
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
