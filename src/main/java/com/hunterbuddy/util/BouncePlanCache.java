package com.hunterbuddy.util;

import java.util.Objects;

public final class BouncePlanCache<K, P> {
   private long generation;
   private Object player;
   private Object world;
   private K key;
   private P plan;
   private BouncePlanCache.Request<K> inFlight;

   public synchronized boolean update(Object player, Object world, K key) {
      Objects.requireNonNull(player); Objects.requireNonNull(world); Objects.requireNonNull(key);
      if (this.player == player && this.world == world && key.equals(this.key)) return false;
      this.generation++;
      this.player = player; this.world = world; this.key = key; this.plan = null;
      return true;
   }

   public synchronized void publish(Object player, Object world, K key, P plan) {
      Objects.requireNonNull(plan);
      this.update(player, world, key);
      this.plan = plan;
   }

   public synchronized Request<K> request() {
      if (this.key != null && this.plan == null && this.inFlight == null) {
         this.inFlight = new Request<>(this.generation, this.player, this.world, this.key);
         return this.inFlight;
      }
      return null;
   }

   public synchronized boolean complete(Request<K> request, P result) {
      if (request != null && request == this.inFlight) {
         this.inFlight = null;
         if (result != null && request.generation == this.generation
             && request.player == this.player && request.world == this.world
             && Objects.equals(request.key, this.key)) {
            this.plan = result;
            return true;
         }
         return false;
      }
      return false;
   }

   public synchronized boolean fail(Request<K> request) {
      if (request != null && request == this.inFlight) { this.inFlight = null; return true; }
      return false;
   }

   public synchronized void invalidate() {
      this.generation++; this.player = null; this.world = null; this.key = null; this.plan = null;
   }

   public synchronized P plan(Object player, Object world, K key) {
      return this.key != null && this.player == player && this.world == world && this.key.equals(key) ? this.plan : null;
   }

   public synchronized boolean isInFlight() { return this.inFlight != null; }

   public static final class Request<K> {
      private final long generation;
      private final Object player;
      private final Object world;
      private final K key;
      private Request(long generation, Object player, Object world, K key) {
         this.generation = generation; this.player = player; this.world = world; this.key = key;
      }
      public K key() { return this.key; }
   }
}
