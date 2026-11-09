package com.hunterbuddy.modules.aura;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;

public final class AuraVitals {
    private static AuraVitals active;
    private final AuraVitalsSettings settings;
    private final AuraTrack[] tracks = new AuraTrack[32];
    private final AuraTrack[] selected = new AuraTrack[16];
    private final LivingEntity[] nearest = new LivingEntity[16];
    private final double[] distances = new double[16];
    private final AuraDamage[] damage = new AuraDamage[48];
    private final AuraVitalsPainter painter;
    private ClientWorld world;
    private LivingEntity current;
    private int selectedCount;
    private int serial;

    public AuraVitals(AuraVitalsSettings settings) {
        this.settings = settings;
        painter = new AuraVitalsPainter(settings);
        for (int i = 0; i < tracks.length; i++) tracks[i] = new AuraTrack();
        for (int i = 0; i < damage.length; i++) damage[i] = new AuraDamage();
    }

    public void activate() { clear(); active = this; }

    public void deactivate() { if (active == this) active = null; clear(); world = null; }

    public static void tracked(int id) {
        if (active != null && active.settings.enabled()) active.observe(id);
    }

    public static void critical(int id) {
        if (active == null || !active.settings.enabled()) return;
        AuraTrack track = active.find(id);
        if (track == null) return;
        long now = now();
        track.critical = now;
    }

    public void hit(Entity entity) {
        if (!settings.enabled() || !(entity instanceof LivingEntity living) || !inRange(living)) return;
        if (!ensureWorld()) return;
        AuraTrack track = admit(living, now());
        current = living;
        track.attempt = now();
        track.health.attempted();
    }

    public void tick(Entity target) {
        if (!ensureWorld() || !settings.enabled()) { clear(); return; }
        long now = now();
        current = target instanceof LivingEntity living && inRange(living) ? living : null;
        selectedCount = 0;
        java.util.Arrays.fill(selected, null);
        for (int i = 0; i < tracks.length; i++) {
            AuraTrack track = tracks[i];
            if (track.entity == null) continue;
            if (now - Math.max(track.seen, Math.max(track.hit, track.attempt)) > settings.duration.get() * 1000 + 1000) {
                discard(track); continue;
            }
            if (track.entity.getMaxHealth() != track.health.maximum) observe(track.entity.getId());
        }
        if (settings.target.get() && current != null) select(admit(current, now), now);
        if (settings.recent.get()) {
            for (int pass = selectedCount; pass < settings.maximum.get(); pass++) {
                AuraTrack newest = null;
                for (int i = 0; i < tracks.length; i++) {
                    AuraTrack track = tracks[i];
                    if (track.entity == null || track.hit == 0 || now - track.hit >= settings.duration.get() * 1000
                        || !track.health.trusted(track.player(), now) || !inRange(track.entity)) continue;
                    if (!track.entity.isAlive() && !hasNumber(track, now)) continue;
                    boolean already = false;
                    for (int j = 0; j < selectedCount; j++) if (selected[j] == track) already = true;
                    if (!already && (newest == null || track.hit > newest.hit)) newest = track;
                }
                if (newest == null) break;
                select(newest, now);
            }
        }
        if (settings.nearby.get() && selectedCount < settings.maximum.get()) nearby(now);
        for (int i = 0; i < selectedCount; i++) {
            AuraTrack track = selected[i];
            if (now - track.refreshed >= 1000) {
                track.refreshed = now;
                String name = track.entity.getName().getString();
                if (!name.equals(track.name)) { track.name = name; track.measuredSize = 0; }
            }
            painter.measure(track);
        }
        for (int i = 0; i < damage.length; i++) {
            if (damage[i].owner != null && now - damage[i].born >= settings.damageDuration.get() * 1000) damage[i].owner = null;
        }
    }

    private void nearby(long now) {
        int count = 0;
        int limit = settings.maximum.get();
        java.util.Arrays.fill(nearest, null);
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof MobEntity living) || !living.isAlive() || living.isInvisible() || !inRange(living)) continue;
            double distance = living.squaredDistanceTo(MeteorClient.mc.player);
            int at = Math.min(count, limit);
            while (at > 0 && distance < distances[at - 1]) at--;
            if (at >= limit) continue;
            for (int i = Math.min(count, limit - 1); i > at; i--) {
                nearest[i] = nearest[i - 1]; distances[i] = distances[i - 1];
            }
            nearest[at] = living; distances[at] = distance;
            count = Math.min(limit, count + 1);
        }
        for (int i = 0; i < count && selectedCount < limit; i++) select(admit(nearest[i], now), now);
    }

    private boolean hasNumber(AuraTrack track, long now) {
        if (!settings.damage.get()) return false;
        for (int i = 0; i < damage.length; i++) if (damage[i].owner == track && now - damage[i].born < settings.damageDuration.get() * 1000) return true;
        return false;
    }

    private void select(AuraTrack track, long now) {
        if (track.entity == current || settings.nearby.get() && track.entity instanceof MobEntity) track.seen = now;
        if (!track.health.trusted(track.player(), now)) return;
        for (int i = 0; i < selectedCount; i++) if (selected[i] == track) return;
        if (selectedCount >= settings.maximum.get()) return;
        selected[selectedCount++] = track;
    }

    private AuraTrack admit(LivingEntity entity, long now) {
        AuraTrack found = find(entity.getId());
        if (found != null) {
            if (found.entity != entity) { discard(found); found.reset(entity, now); }
            return found;
        }
        AuraTrack oldest = null;
        for (int i = 0; i < tracks.length; i++) {
            AuraTrack track = tracks[i];
            if (track.entity == null) { oldest = track; break; }
            boolean protectedTrack = false;
            for (int j = 0; j < selectedCount; j++) if (selected[j] == track) protectedTrack = true;
            if (!protectedTrack && (oldest == null || track.seen < oldest.seen)) oldest = track;
        }
        discard(oldest);
        oldest.reset(entity, now);
        return oldest;
    }

    private AuraTrack find(int id) {
        for (int i = 0; i < tracks.length; i++) if (tracks[i].entity != null && tracks[i].entity.getId() == id) return tracks[i];
        return null;
    }

    private void observe(int id) {
        if (!ensureWorld()) return;
        AuraTrack track = find(id);
        if (track == null || world.getEntityById(id) != track.entity) return;
        long now = now();
        float before = track.health.health;
        float absorption = track.health.absorption;
        float max = track.health.maximum;
        float lost = track.health.observe(track.entity.getHealth(), track.entity.getMaxHealth(), track.entity.getAbsorptionAmount(), track.player(), now);
        if (before != track.health.health || absorption != track.health.absorption || max != track.health.maximum) {
            track.labels();
            painter.measure(track);
        }
        if (track.attempt > 0 && now - track.attempt <= 750 && (lost > 0 || absorption > track.health.absorption)) track.hit = now;
        if (lost <= 0) return;
        boolean critical = track.critical > 0 && now - track.critical <= 350;
        track.critical = 0;
        if (!settings.damage.get() || !useful(track, now)) return;
        AuraDamage slot = null;
        AuraDamage oldest = damage[0];
        int owned = 0;
        for (int i = 0; i < damage.length; i++) {
            AuraDamage number = damage[i];
            if (number.owner == null) slot = number;
            if (number.owner == track) owned++;
            if (number.born < oldest.born) oldest = number;
        }
        if (owned >= 3) {
            slot = null;
            for (int i = 0; i < damage.length; i++) if (damage[i].owner == track && (slot == null || damage[i].born < slot.born)) slot = damage[i];
        }
        if (slot == null) slot = oldest;
        slot.set(track, lost, now, serial++ % 3 - 1, critical);
        slot.width = MeteorClient.mc.textRenderer.getWidth(slot.text);
    }

    private boolean useful(AuraTrack track, long now) {
        return settings.target.get() && track.entity == current || settings.recent.get() && track.hit > 0
            && now - track.hit < settings.duration.get() * 1000 || settings.nearby.get() && track.entity instanceof MobEntity;
    }

    boolean persistent(AuraTrack track) { return settings.target.get() && track.entity == current || settings.nearby.get() && track.entity instanceof MobEntity; }

    public void render(Render2DEvent event) {
        if (!settings.enabled() || MeteorClient.mc.player == null || MeteorClient.mc.world != world || MeteorClient.mc.options.hudHidden) return;
        painter.render(event, this, selected, selectedCount, damage, now());
    }

    private boolean inRange(LivingEntity entity) {
        return MeteorClient.mc.player != null && entity != MeteorClient.mc.player && entity.squaredDistanceTo(MeteorClient.mc.player) <= settings.distance.get() * settings.distance.get();
    }

    private boolean ensureWorld() {
        ClientWorld next = MeteorClient.mc.world;
        if (next != world) { clear(); world = next; }
        return next != null && MeteorClient.mc.player != null;
    }

    private void discard(AuraTrack track) {
        for (int i = 0; i < damage.length; i++) if (damage[i].owner == track) damage[i].owner = null;
        track.entity = null;
    }

    private void clear() {
        selectedCount = 0;
        current = null;
        for (int i = 0; i < tracks.length; i++) tracks[i].entity = null;
        java.util.Arrays.fill(selected, null);
        for (int i = 0; i < damage.length; i++) damage[i].owner = null;
        java.util.Arrays.fill(nearest, null);
    }

    private static long now() { return System.nanoTime() / 1_000_000; }
}
