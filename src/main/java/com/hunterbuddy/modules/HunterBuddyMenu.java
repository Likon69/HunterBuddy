package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.chatdock.ChatDock;
import com.hunterbuddy.chatdock.DockMigration;
import com.hunterbuddy.chatdock.DockPreferences;
import com.hunterbuddy.chatdock.HunterBuddyScreen;
import meteordevelopment.meteorclient.events.meteor.CustomFontChangedEvent;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.meteor.MouseClickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.nbt.NbtCompound;

public final class HunterBuddyMenu extends Module implements DockPreferences {
    private final SettingGroup menu = settings.createGroup("Menu");
    private final Setting<Keybind> menuKey = menu.add(new KeybindSetting.Builder().name("menu-key")
        .description("Open or close the HunterBuddy menu without opening chat.").defaultValue(Keybind.none()).build());
    private final Setting<Boolean> showInChat = menu.add(new BoolSetting.Builder().name("show-in-chat")
        .description("Show the menu button while chat is open.").defaultValue(true).build());
    private final Setting<Double> dockX = menu.add(new DoubleSetting.Builder().name("dock-x")
        .description("Relative menu position; negative uses automatic placement.").defaultValue(-1).min(-1).max(1).visible(() -> false).build());
    private final Setting<Double> dockY = menu.add(new DoubleSetting.Builder().name("dock-y")
        .description("Relative menu position; negative uses automatic placement.").defaultValue(-1).min(-1).max(1).visible(() -> false).build());
    private final Setting<Double> scale = menu.add(new DoubleSetting.Builder().name("scale")
        .description("Menu button, text and spacing scale.").defaultValue(1).min(0.6).max(1.8).sliderRange(0.7, 1.5).build());
    private final Setting<SettingColor> background = menu.add(new ColorSetting.Builder().name("background")
        .description("Menu glass background and opacity.").defaultValue(new SettingColor(17, 24, 35, 195)).build());
    private final Setting<SettingColor> accent = menu.add(new ColorSetting.Builder().name("accent")
        .description("Menu hover and selection color.").defaultValue(new SettingColor(106, 231, 193)).build());

    public HunterBuddyMenu() {
        super(HunterBuddyAddon.UTILITY_CATEGORY, "hunterbuddy-menu", "Central glass menu for HunterBuddy tools.");
        runInMainMenu = true;
        ChatDock.INSTANCE.configure(this, settings);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ChatDock.INSTANCE.shutdown());
    }

    @Override
    public void onActivate() { ChatDock.INSTANCE.enabled(true); }

    @Override
    public void onDeactivate() { ChatDock.INSTANCE.enabled(false); }

    @EventHandler
    private void onMenuKey(KeyEvent event) {
        if (event.action != KeyAction.Press || !menuKey.get().matches(event.input) || !canToggleMenu()) return;
        ChatDock.INSTANCE.toggleScreen();
        event.cancel();
    }

    @EventHandler
    private void onMenuButton(MouseClickEvent event) {
        if (event.action != KeyAction.Press || !menuKey.get().matches(event.input) || !canToggleMenu()) return;
        ChatDock.INSTANCE.toggleScreen();
        event.cancel();
    }

    private boolean canToggleMenu() {
        return mc.player != null && (mc.currentScreen == null || mc.currentScreen instanceof HunterBuddyScreen);
    }

    @EventHandler
    private void onFontChanged(CustomFontChangedEvent event) { ChatDock.INSTANCE.fontChanged(); }

    @Override
    public Module fromTag(NbtCompound tag) {
        return super.fromTag("log".equals(tag.getString("name", "")) ? DockMigration.convert(tag) : tag);
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        var controls = theme.verticalList();
        WButton reset = controls.add(theme.button("Reset position")).expandX().widget();
        reset.action = ChatDock.INSTANCE::resetPosition;
        ChatDock.INSTANCE.widgets(theme, controls);
        return controls;
    }

    @Override
    public double dockX() { return dockX.get(); }

    @Override
    public double dockY() { return dockY.get(); }

    @Override
    public void dockPosition(double x, double y) {
        dockX.set(x);
        dockY.set(y);
    }

    @Override
    public double dockScale() { return scale.get(); }

    @Override
    public boolean dockInChat() { return showInChat.get(); }

    @Override
    public SettingColor dockAccent() { return accent.get(); }

    @Override
    public SettingColor dockBackground() { return background.get(); }
}
