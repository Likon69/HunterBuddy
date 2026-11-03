package com.hunterbuddy.modules.stash;

import com.hunterbuddy.modules.StashFinder.StashChunk;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.MeteorToast;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;

import java.util.function.Supplier;

public final class StashNotifier {
    private static final String CHAT_PREFIX = "StashFinder";
    private static final long DING_GAP_MILLIS = 150L;
    private static final float[] DING_PITCHES = {1.0F, 1.2F, 1.4F};

    private final StashGeneralSettings general;
    private final StashPrivacySettings privacy;
    private final Supplier<String> title;

    public StashNotifier(StashGeneralSettings general, StashPrivacySettings privacy, Supplier<String> title) {
        this.general = general;
        this.privacy = privacy;
        this.title = title;
    }

    public void notify(StashChunk chunk) {
        switch (general.notificationMode.get()) {
            case Chat -> chat(chunk);
            case Toast -> toast();
            case Sound -> ding();
            case Both -> {
                chat(chunk);
                toast();
            }
            case All -> {
                chat(chunk);
                toast();
                ding();
            }
            case Silent -> { }
        }
    }

    private void chat(StashChunk chunk) {
        if (privacy.hidesCoordinatesInChat()) {
            ChatUtils.infoPrefix(CHAT_PREFIX, "Found stash! Use secure menu to view coordinates.");
        } else {
            ChatUtils.infoPrefix(CHAT_PREFIX, "Found stash at [" + chunk.x + ", " + chunk.z + "]");
        }
    }

    private void toast() {
        MeteorClient.mc.getToastManager().add(
            new MeteorToast.Builder(title.get()).icon(Items.CHEST).text("Stash Found!").build());
    }

    private void ding() {
        MeteorExecutor.execute(() -> {
            try {
                for (float pitch : DING_PITCHES) {
                    play(pitch);
                    Thread.sleep(DING_GAP_MILLIS);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private static void play(float pitch) {
        MeteorClient.mc.execute(() -> MeteorClient.mc.getSoundManager()
            .play(PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, pitch)));
    }
}
