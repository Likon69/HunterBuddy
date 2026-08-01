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

   public final Setting<Integer> maxTrackTime = sgGeneral.add(new IntSetting.Builder()
      .name("max-track-time")
      .description("Max ms to wait for the destroy packet before giving up tracking. Safety net for out-of-range rockets.")
      .defaultValue(5000)
      .min(1000)
      .max(30000)
      .sliderRange(1000, 15000)
      .build()
   );

   public final Setting<Integer> cooldownAfterFlush = sgGeneral.add(new IntSetting.Builder()
      .name("cooldown-after-flush")
      .description("Ms to ignore new rockets after a flush. Prevents back-to-back boost extension. 0 = disabled.")
      .defaultValue(0)
      .min(0)
      .max(10000)
      .sliderRange(0, 5000)
      .build()
   );

   public final Setting<Boolean> setbackDetector = sgGeneral.add(new BoolSetting.Builder()
      .name("setback-detector")
      .description("Detect Grim setback (PlayerPositionLookS2CPacket) during boost. Increments setbackCount and flushes immediately. Default ON.")
      .defaultValue(true)
      .build()
   );

   public int setbackCount = 0;

   public double getSpeed() {
      return speedMultiplier.get();
   }

   public int trackedRocketId = -1;
   public long boostStartMs = -1;
   public long trackStartMs = -1;
   public long lastFlushMs = -1;
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
      if (cooldownAfterFlush.get() > 0 && lastFlushMs != -1
         && System.currentTimeMillis() - lastFlushMs < cooldownAfterFlush.get()) {
         return;
      }
      trackedRocketId = entityId;
      boosting = true;
      trackStartMs = System.currentTimeMillis();
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
      trackStartMs = -1;
      lastFlushMs = System.currentTimeMillis();
      trackedRocketId = -1;

      int pongsToFlush = pongQueue.size();
      if (mc.getNetworkHandler() != null) {
         for (Packet<?> p : pongQueue) mc.getNetworkHandler().sendPacket(p);
      }
      pongQueue.clear();

      if (mc.world != null && idToRemove != -1) {
         mc.world.removeEntity(idToRemove, net.minecraft.entity.Entity.RemovalReason.KILLED);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   private void onReceivePacket(PacketEvent.Receive event) {
      if (mc.getNetworkHandler() == null) return;
      if (mc.player == null) return;
      if (!boosting) return;
      if (setbackDetector.get() && event.packet instanceof net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket) {
         setbackCount++;
         flushAndStop();
         return;
      }
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

      if (boosting && boostStartMs == -1 && trackStartMs != -1) {
         long waiting = System.currentTimeMillis() - trackStartMs;
         if (waiting >= maxTrackTime.get()) {
            flushAndStop();
            return;
         }
      }

      String msg;
      if (!boosting) {
         if (lastGainedMs >= 0) {
            String cooldInfo;
            if (cooldownAfterFlush.get() > 0) {
               if (lastFlushMs == -1) {
                  cooldInfo = "coold 0s / " + (cooldownAfterFlush.get() / 1000) + "s";
               } else {
                  long elapsed = Math.min(System.currentTimeMillis() - lastFlushMs, cooldownAfterFlush.get());
                  cooldInfo = "coold " + (elapsed / 1000) + "s / " + (cooldownAfterFlush.get() / 1000) + "s"
                     + (elapsed >= cooldownAfterFlush.get() ? " §aREADY" : "");
               }
            } else {
               cooldInfo = "coold off";
            }
            msg = "§a§l[RB] §r§a+ " + lastGainedMs + "ms §7last gain"
               + "\n§7ready for next rocket | " + cooldInfo;
         } else {
            msg = "§7§l[RB] §r§7idle — fire a rocket to start"
               + "\n§7cooldown: " + (cooldownAfterFlush.get() > 0 ? (cooldownAfterFlush.get() / 1000) + "s" : "off");
         }
      } else if (boostStartMs == -1) {
         msg = "§e§l[RB] §r§etracking #" + trackedRocketId
               + "\n§7destroy packet pas encore arrive | pongs: " + pongQueue.size();
      } else {
         long elapsed = System.currentTimeMillis() - boostStartMs;
         long remaining = boostDuration.get() - elapsed;
         String col = remaining > 200 ? "§a" : "§c";
         String colBold = remaining > 200 ? "§a§l" : "§c§l";
         long percent = (elapsed * 100) / Math.max(1, boostDuration.get());
         String setbackTag = setbackCount > 0 ? " §4§lFLAGGED x" + setbackCount + "§r" : "";
         msg = colBold + "[RB] §r" + col + elapsed + "ms" + "§7/" + boostDuration.get() + "ms (" + percent + "%)"
            + "\n§7+" + remaining + "ms left | pongs: " + pongQueue.size() + setbackTag;
      }

      mc.player.sendMessage(net.minecraft.text.Text.literal(msg), true);
   }
}
