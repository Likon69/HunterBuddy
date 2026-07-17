package com.hunterbuddy.modules.chesttracker;
import net.minecraft.client.MinecraftClient;


public class ChestTrackerDataManager {
   private static ChestTrackerDataV2 sharedData = null;
   private static int activeModuleCount = 0;
   private static String currentServerIdentifier = null;

   public static synchronized ChestTrackerDataV2 onModuleActivate() {
      activeModuleCount++;
      return sharedData;
   }

   public static synchronized void onModuleDeactivate() {
      activeModuleCount--;
      if (activeModuleCount <= 0) {
         if (sharedData != null) {
            sharedData.saveData();
            sharedData = null;
         }

         activeModuleCount = 0;
         currentServerIdentifier = null;
      }
   }

   public static synchronized ChestTrackerDataV2 getData() {
      String serverNow = getCurrentServerIdentifier();
      if (sharedData != null && currentServerIdentifier != null && !currentServerIdentifier.equals(serverNow)) {
         sharedData.saveData();
         sharedData.reinitializeForNewServer();
         sharedData.loadData();
         currentServerIdentifier = serverNow;
      }

      if (sharedData == null) {
         sharedData = new ChestTrackerDataV2();
         sharedData.loadData();
         currentServerIdentifier = serverNow;
      }

      return sharedData;
   }

   private static String getCurrentServerIdentifier() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc == null) {
         return "unknown";
      } else if (mc.getCurrentServerEntry() != null) {
         return mc.getCurrentServerEntry().address;
      } else {
         return mc.isInSingleplayer() && mc.getServer() != null ? "singleplayer_" + mc.getServer().getSaveProperties().getLevelName() : "unknown";
      }
   }

   public static synchronized boolean isActive() {
      return activeModuleCount > 0;
   }

   public static synchronized void saveData() {
      if (sharedData != null) {
         sharedData.saveData();
      }
   }

   public static synchronized void reloadData() {
      if (sharedData != null) {
         sharedData.reinitializeForNewServer();
         sharedData.loadData();
         currentServerIdentifier = getCurrentServerIdentifier();
      }
   }

   public static synchronized void onWorldJoin() {
      String serverNow = getCurrentServerIdentifier();
      if (sharedData != null) {
         sharedData.saveData();
         sharedData.reinitializeForNewServer();
         sharedData.loadData();
         currentServerIdentifier = serverNow;
      } else if (activeModuleCount > 0) {
         sharedData = new ChestTrackerDataV2();
         sharedData.loadData();
         currentServerIdentifier = serverNow;
      }
   }
}
