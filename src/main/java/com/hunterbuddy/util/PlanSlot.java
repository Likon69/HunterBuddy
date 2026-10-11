package com.hunterbuddy.util;

import java.util.Objects;

public final class PlanSlot<K, P> {
   private Scope<K> scope;
   private P plan;
   private Ticket<K> pending;

   public synchronized void update(Object player, Object world, K key) {
      Objects.requireNonNull(player);
      Objects.requireNonNull(world);
      Objects.requireNonNull(key);
      if (this.scope != null && this.scope.matches(player, world, key)) return;
      this.scope = new Scope<>(player, world, key);
      this.plan = null;
   }

   public synchronized P plan(Object player, Object world, K key) {
      return this.scope != null && this.scope.matches(player, world, key) ? this.plan : null;
   }

   public synchronized Ticket<K> request() {
      if (this.scope == null || this.plan != null || this.pending != null) return null;
      this.pending = new Ticket<>(this.scope);
      return this.pending;
   }

   public synchronized boolean complete(Ticket<K> ticket, P result) {
      if (ticket == null || ticket != this.pending) return false;
      this.pending = null;
      if (result == null || ticket.scope != this.scope) return false;
      this.plan = result;
      return true;
   }

   public synchronized boolean fail(Ticket<K> ticket) {
      if (ticket == null || ticket != this.pending) return false;
      this.pending = null;
      return true;
   }

   public synchronized void invalidate() {
      this.scope = null;
      this.plan = null;
   }

   private record Scope<K>(Object player, Object world, K key) {
      boolean matches(Object player, Object world, K key) {
         return this.player == player && this.world == world && this.key.equals(key);
      }
   }

   public static final class Ticket<K> {
      private final Scope<K> scope;

      private Ticket(Scope<K> scope) {
         this.scope = scope;
      }

      public K key() {
         return this.scope.key();
      }
   }
}
