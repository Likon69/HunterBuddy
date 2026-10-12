package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import java.util.List;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.SoundEventListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

public class DisconnectSound extends Module {
    private static final SoundEvent DEFAULT_SOUND = SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();

    private final SettingGroup sgMain = settings.getDefaultGroup();

    private final Setting<List<SoundEvent>> sounds = sgMain.add(new SoundEventListSetting.Builder()
        .name("sound")
        .description("What to play when you get disconnected; only the first entry is used.")
        .defaultValue(List.of(DEFAULT_SOUND))
        .build()
    );

    private final Setting<Double> pitch = sgMain.add(new DoubleSetting.Builder()
        .name("sound-pitch")
        .description("How high it plays; 1 is the normal pitch.")
        .defaultValue(0.5)
        .min(0.0)
        .sliderRange(0.5, 2.0)
        .build()
    );

    private final Setting<Double> volume = sgMain.add(new DoubleSetting.Builder()
        .name("sound-volume")
        .description("How loud it plays, from silent at 0 to full at 1.")
        .defaultValue(1.0)
        .min(0.0)
        .sliderRange(0.0, 1.0)
        .build()
    );

    public DisconnectSound() {
        super(HunterBuddyAddon.UTILITY_CATEGORY, "kick-alarm", "Plays a sound when you get disconnected from a server: kicked, timed out or the connection failed.", "disconnect-sound");
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (event.screen instanceof DisconnectedScreen) play();
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WButton button = theme.button("Test sound");
        button.action = this::play;
        return button;
    }

    private void play() {
        List<SoundEvent> list = sounds.get();
        if (list == null || list.isEmpty()) return;
        mc.getSoundManager().play(PositionedSoundInstance.master(list.get(0), pitch.get().floatValue(), volume.get().floatValue()));
    }
}
