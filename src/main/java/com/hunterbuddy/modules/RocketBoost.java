package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;

import java.util.ArrayList;
import java.util.List;

public class RocketBoost extends Module {
   private final SettingGroup sgGeneral = settings.getDefaultGroup();

   public final Setting<Integer> boostDuration = sgGeneral.add(new IntSetting.Builder()
      .name("boost-duration")
      .description("Maximum ms the boost can be extended. Test up to the server's Grim max-ping-firework-boost limit.")
      .defaultValue(800)
      .min(100)
      .max(10000)
      .sliderRange(100, 10000)
      .build()
   );

   public final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
      .name("debug")
      .description("Show boost state in the action bar.")
      .defaultValue(false)
      .build()
   );

   public final Setting<Double> speedMultiplier = sgGeneral.add(new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
      .name("speed-multiplier")
      .description("Firework target speed multiplier (vanilla 1.5). Stay under 2.0 to stay inside Grim's ±1.7 block/tick uncertainty box.")
      .defaultValue(1.5)
      .min(1.0)
      .max(2.0)
      .sliderRange(1.5, 2.0)
      .build()
   );

   public double getSpeed() {
      return speedMultiplier.get();
   }

   public int trackedRocketId = -1;
   public long boostStartMs = -1;
   public boolean boosting = false;
   public long lastGainedMs = -1;
   public final List<Packet<?>> pongQueue = new ArrayList<>();

   public RocketBoost() {
      super(HunterBuddyAddon.LAB_CATEGORY, "rocket-boost", "Approach A: cancel destroy + queue pongs (Grim-aware, 800ms default).");
   }

   @Override
   public void onDeactivate() {
      flushAndStop();
      lastGainedMs = -1;
   }

   public void setTrackedRocket(int entityId) {
      if (trackedRocketId == entityId) return;
      if (trackedRocketId != -1) flushAndStop();
      trackedRocketId = entityId;
      boosting = true;
   }

   public boolean isBoosting() {
      return boosting;
   }

   public void flushAndStop() {
      int idToRemove = trackedRocketId;
      if (boostStartMs != -1) {
         lastGainedMs = System.currentTimeMillis() - boostStartMs;
      }
      boosting = false;
      boostStartMs = -1;
      trackedRocketId = -1;

      int pongsToFlush = pongQueue.size();
      if (mc.getNetworkHandler() != null) {
         for (Packet<?> p : pongQueue) mc.getNetworkHandler().sendPacket(p);
      }
      pongQueue.clear();

      if (mc.world != null && idToRemove != -1) {
         mc.world.removeEntity(idToRemove, net.minecraft.entity.Entity.RemovalReason.KILLED);
      }
      if (debug.get()) {
         info("flushAndStop — idToRemove=" + idToRemove + " pongs flush=" + pongsToFlush + " gained=" + lastGainedMs + "ms");
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   private void onReceivePacket(PacketEvent.Receive event) {
      if (mc.getNetworkHandler() == null) return;
      if (mc.player == null) return;
      if (!boosting) return;
      if (!(event.packet instanceof EntitiesDestroyS2CPacket packet)) return;

      IntList remaining = new IntArrayList();
      boolean found = false;
      for (int id : packet.getEntityIds()) {
         if (id == trackedRocketId) {
            event.cancel();
            boostStartMs = System.currentTimeMillis();
            found = true;
         } else {
            remaining.add(id);
         }
      }
      if (found && !remaining.isEmpty()) {
         mc.getNetworkHandler().onEntitiesDestroy(new EntitiesDestroyS2CPacket(remaining));
      }
      if (found && debug.get()) {
         info("DESTROY intercepté — boost démarre, boostStartMs=" + boostStartMs);
      }
   }

   @EventHandler
   private void onSendPacket(PacketEvent.Send event) {
      if (mc.player == null || !mc.player.isGliding()) return;
      if (!boosting || boostStartMs == -1) return;
      if (!(event.packet instanceof CommonPongC2SPacket packet)) return;

      long elapsed = System.currentTimeMillis() - boostStartMs;
      if (elapsed < boostDuration.get()) {
         event.cancel();
         pongQueue.add(event.packet);
      } else {
         flushAndStop();
      }
   }

   @EventHandler
   private void onTickDebug(meteordevelopment.meteorclient.events.world.TickEvent.Post event) {
      if (!debug.get() || mc.player == null) return;

      String lastInfo = lastGainedMs >= 0 ? "\n§a[RB] ✓ +" + lastGainedMs + "ms gained" : "";

      String msg;
      if (!boosting) {
         if (lastGainedMs >= 0) {
            msg = "§a[RB] ✓ +" + lastGainedMs + "ms gained\n§7waiting for next rocket";
         } else {
            msg = "§7[RB] IDLE";
         }
      } else if (boostStartMs == -1) {
         msg = "§e[RB] TRACKING #" + trackedRocketId + " | pongs:" + pongQueue.size() + lastInfo;
      } else {
         long elapsed = System.currentTimeMillis() - boostStartMs;
         long remaining = boostDuration.get() - elapsed;
         String col = remaining > 300 ? "§a" : "§c";
         msg = "§b[RB] EXTENDING " + elapsed + "ms/" + boostDuration.get() + "ms " + col + "(+" + remaining + "ms)" + " §7pongs:" + pongQueue.size() + lastInfo;
      }

      mc.player.sendMessage(net.minecraft.text.Text.literal(msg), true);
   }
}
