package com.hunterbuddy.util;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;

public final class GlideClearHold {
   private static final Queue<CommonPingS2CPacket> PINGS = new ConcurrentLinkedQueue<>();
   private static final Queue<EntityTrackerUpdateS2CPacket> CLEARS = new ConcurrentLinkedQueue<>();
   private static volatile Object owner = null;
   private static volatile long heldSince = 0L;

   private GlideClearHold() {
   }

   public static boolean claim(Object candidate) {
      if (owner == candidate) {
         return true;
      }

      if (owner == null || CLEARS.isEmpty()) {
         owner = candidate;
         heldSince = 0L;
         return true;
      }

      return false;
   }

   public static boolean owns(Object candidate) {
      return owner == candidate;
   }

   public static void release(Object candidate) {
      if (owner != candidate) {
         return;
      }

      flush();
      owner = null;
   }

   public static boolean holding() {
      return !CLEARS.isEmpty();
   }

   public static long heldSince() {
      return heldSince;
   }

   public static boolean intake(Object candidate, Receive event) {
      if (owner != candidate || MeteorClient.mc.player == null) {
         return false;
      }

      if (event.packet instanceof CommonPingS2CPacket ping) {
         PINGS.add(ping);
         event.cancel();
         return true;
      }

      if (event.packet instanceof EntityTrackerUpdateS2CPacket tracker
         && tracker.id() == MeteorClient.mc.player.getId()
         && MeteorClient.mc.player.isGliding()) {
         for (DataTracker.SerializedEntry<?> entry : tracker.trackedValues()) {
            if (entry.id() == 0 && entry.value() instanceof Byte flags && (flags & 0x80) == 0) {
               if (CLEARS.isEmpty()) {
                  heldSince = MeteorClient.mc.player.age;
               }

               CLEARS.add(tracker);
               event.cancel();
               return true;
            }
         }
      }

      return false;
   }

   public static void answerPings() {
      ClientPlayNetworkHandler handler = MeteorClient.mc.getNetworkHandler();
      if (handler == null) {
         PINGS.clear();
         return;
      }

      CommonPingS2CPacket ping;
      while ((ping = PINGS.poll()) != null) {
         applyPacket(handler, ping);
      }
   }

   public static void flushAheadOfTeleport() {
      flush();
   }

   public static void flush() {
      ClientPlayNetworkHandler handler = MeteorClient.mc.getNetworkHandler();
      if (handler == null) {
         CLEARS.clear();
         PINGS.clear();
         return;
      }

      EntityTrackerUpdateS2CPacket clear;
      while ((clear = CLEARS.poll()) != null) {
         applyPacket(handler, clear);
      }

      CommonPingS2CPacket ping;
      while ((ping = PINGS.poll()) != null) {
         applyPacket(handler, ping);
      }
   }

   private static <T extends PacketListener> void applyPacket(T listener, Packet<T> packet) {
      if (MeteorClient.mc.isOnThread()) {
         packet.apply(listener);
      } else {
         MeteorClient.mc.getPacketApplyBatcher().add(listener, packet);
      }
   }
}
