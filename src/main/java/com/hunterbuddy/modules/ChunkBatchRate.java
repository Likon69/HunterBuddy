package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

/**
 * The server paces chunk delivery from a rate the client itself reports, in
 * ServerboundChunkBatchReceivedPacket. Vanilla derives that number from how long the
 * client took to process the last batch (ChunkBatchSizeCalculator), starts at 9 and the
 * server clamps it to [0.01, 64] in PlayerChunkSender#onChunkBatchReceivedByClient.
 * Reporting a higher figure makes the server send chunks faster.
 *
 * This is a throughput packet, not a movement packet, so it never reaches the anticheat's
 * prediction engine.
 */
public class ChunkBatchRate extends Module {
    private static final int LOG_INTERVAL_TICKS = 20 * 5;

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    public final Setting<Double> chunksPerTick = this.sgGeneral.add(new DoubleSetting.Builder()
        .name("chunks-per-tick")
        .description("Débit annoncé au serveur. Le vanilla démarre à 9, le serveur plafonne à 64.")
        .defaultValue(32.0)
        .min(1.0)
        .sliderRange(1.0, 64.0)
        .build()
    );

    public final Setting<Boolean> onlyWhenGliding = this.sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-gliding")
        .description("Ne forcer le débit qu'en vol elytra. Au sol, le client annonce sa vraie mesure.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> debug = this.sgGeneral.add(new BoolSetting.Builder()
        .name("debug")
        .description("Affiche dans le chat la mesure vanilla et la valeur réellement envoyée.")
        .defaultValue(false)
        .build()
    );

    private float lastVanillaEstimate;
    private float lastSentValue;
    private int logCooldown;

    public ChunkBatchRate() {
        super(HunterBuddyAddon.UTILITY_CATEGORY, "chunk-batch-rate",
            "Annonce au serveur un débit de chunks plus élevé pour qu'il les envoie plus vite. Paquet de débit, sans rapport avec le mouvement.");
    }

    @Override
    public void onActivate() {
        this.lastVanillaEstimate = 0.0f;
        this.lastSentValue = 0.0f;
        this.logCooldown = 0;
    }

    /**
     * Called from the mixin with the rate vanilla just computed.
     * Returns the rate to report instead, or a negative number to keep the vanilla one.
     */
    public float resolveDesiredChunksPerTick(float vanillaEstimate) {
        this.lastVanillaEstimate = vanillaEstimate;

        if (this.onlyWhenGliding.get() && (this.mc.player == null || !this.mc.player.isGliding())) {
            this.lastSentValue = vanillaEstimate;
            return -1.0f;
        }

        this.lastSentValue = this.chunksPerTick.get().floatValue();
        return this.lastSentValue;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!this.debug.get()) return;

        if (this.logCooldown > 0) {
            this.logCooldown--;
            return;
        }

        this.logCooldown = LOG_INTERVAL_TICKS;
        info("mesure vanilla " + round(this.lastVanillaEstimate)
            + " chunk/tick -> annonce " + round(this.lastSentValue), new Object[0]);
    }

    private static double round(float value) {
        return Math.round(value * 100.0f) / 100.0;
    }
}
