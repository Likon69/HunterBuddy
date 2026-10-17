package com.hunterbuddy.modules.messenger;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public final class MessengerSettings {
    public enum Sound {
        None,
        Pling,
        Bell,
        Orb
    }

    public enum Glass {
        Off,
        Tint
    }

    public enum ListSide {
        Auto,
        Left,
        Right,
        Above,
        Below
    }

    private final SettingGroup sgGeneral;
    private final SettingGroup sgIgnore;
    private final SettingGroup sgDetection;
    private final SettingGroup sgNotification;
    private final SettingGroup sgWindows;
    private final SettingGroup sgColors;
    private final SettingGroup sgStyle;

    public final Setting<String> replyCommand;
    public final Setting<Integer> sendInterval;
    public final Setting<Integer> historySize;
    public final Setting<Boolean> keepHistory;
    public final Setting<Boolean> autoOpen;
    public final Setting<Boolean> markReadOnOpen;
    public final Setting<Boolean> hideFromMainChat;
    public final Setting<Boolean> friendsOnly;
    public final Setting<Boolean> streamMode;
    public final Setting<Boolean> debugRaw;
    public final Setting<Keybind> openLast;
    public final Setting<Boolean> bubbleAdded;

    public final Setting<List<String>> ignoredNames;
    public final Setting<Boolean> hideIgnoredFromMainChat;

    public final Setting<Boolean> useVanillaKeys;
    public final Setting<String> incomingPattern;
    public final Setting<String> outgoingPattern;

    public final Setting<Sound> sound;
    public final Setting<Double> soundVolume;
    public final Setting<Boolean> chatNotice;

    public final Setting<Integer> windowWidth;
    public final Setting<Integer> windowLines;
    public final Setting<Double> textScale;
    public final Setting<Boolean> textShadow;
    public final Setting<Double> lineSpacing;
    public final Setting<Double> cornerRadius;
    public final Setting<Boolean> timestamps;
    public final Setting<Boolean> lockPositions;
    public final Setting<Boolean> animations;
    public final Setting<ListSide> listSide;

    public final Setting<Glass> glass;
    public final Setting<Boolean> sheen;
    public final Setting<Boolean> peerColors;
    public final Setting<Integer> bandWidth;
    public final Setting<SettingColor> selectionColor;
    public final Setting<Boolean> copyWithNames;

    public final Setting<SettingColor> backgroundColor;
    public final Setting<SettingColor> headerColor;
    public final Setting<SettingColor> incomingColor;
    public final Setting<SettingColor> outgoingColor;
    public final Setting<SettingColor> textColor;
    public final Setting<SettingColor> dimColor;
    public final Setting<SettingColor> accentColor;
    public final Setting<SettingColor> dangerColor;

    public String command() {
        return replyCommand.get().trim().replaceFirst("^/+", "");
    }

    private static final long PATTERN_WARN_INTERVAL_MS = 10_000L;
    private static long lastPatternWarn;

    private static void checkPattern(String regex) {
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            long now = System.currentTimeMillis();
            if (now - lastPatternWarn < PATTERN_WARN_INTERVAL_MS) return;
            lastPatternWarn = now;
            ChatUtils.warningPrefix("Messenger", "An invalid pattern is ignored: %s", regex);
        }
    }

    public MessengerSettings(Settings settings, Runnable onOpenLast) {
        sgGeneral = settings.getDefaultGroup();
        sgIgnore = settings.createGroup("Ignore");
        sgDetection = settings.createGroup("Detection");
        sgNotification = settings.createGroup("Notification");
        sgWindows = settings.createGroup("Windows");
        sgColors = settings.createGroup("Colors");
        sgStyle = settings.createGroup("Style");

        replyCommand = sgGeneral.add(new StringSetting.Builder()
            .name("reply-command").description("Command used to send a private message. A leading slash is removed.")
            .defaultValue("msg").build());

        sendInterval = sgGeneral.add(new IntSetting.Builder()
            .name("send-interval").description("Minimum time between two sent messages, in milliseconds. Extra messages wait in a queue.")
            .defaultValue(1500).min(500).max(10000).sliderRange(500, 5000).build());

        historySize = sgGeneral.add(new IntSetting.Builder()
            .name("history-size").description("Messages kept per conversation.")
            .defaultValue(200).min(10).max(2000).sliderRange(20, 500).build());

        keepHistory = sgGeneral.add(new BoolSetting.Builder()
            .name("keep-history").description("Save conversations to disk and bring them back next session. Turned off, the saved history is erased.")
            .defaultValue(true).build());

        autoOpen = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-open").description("Open the conversation window when a message arrives while the chat is open.")
            .defaultValue(false).build());

        markReadOnOpen = sgGeneral.add(new BoolSetting.Builder()
            .name("mark-read-on-open").description("A conversation counts as read while its window is showing. Off, it counts as read once you click the window.")
            .defaultValue(true).build());

        hideFromMainChat = sgGeneral.add(new BoolSetting.Builder()
            .name("hide-from-main-chat").description("Remove private messages from the main chat.")
            .defaultValue(false).build());

        friendsOnly = sgGeneral.add(new BoolSetting.Builder()
            .name("friends-only").description("Only collect messages from Meteor friends.")
            .defaultValue(false).build());

        streamMode = sgGeneral.add(new BoolSetting.Builder()
            .name("stream-mode").description("Never show message contents: windows and notices show names and counts only.")
            .defaultValue(false).build());

        debugRaw = sgGeneral.add(new BoolSetting.Builder()
            .name("debug-raw").description("Writes every received server chat line, private messages included, to the game log to learn the private message format. Turn it off after use.")
            .defaultValue(false).build());

        openLast = sgGeneral.add(new KeybindSetting.Builder()
            .name("open-last").description("Open the chat with the most recent conversation.")
            .defaultValue(Keybind.none()).action(onOpenLast).build());

        bubbleAdded = sgGeneral.add(new BoolSetting.Builder()
            .name("bubble-added").description("Internal: the button was already added to the HUD once.")
            .defaultValue(false).visible(() -> false).build());

        ignoredNames = sgIgnore.add(new StringListSetting.Builder()
            .name("ignored-names").description("Players whose private messages are never added or counted. Adding a name also deletes its conversation. Remove a name here to stop ignoring it.")
            .defaultValue(new ArrayList<>()).build());

        hideIgnoredFromMainChat = sgIgnore.add(new BoolSetting.Builder()
            .name("hide-ignored-from-main-chat").description("Also hides private messages received from ignored players in the main chat.")
            .defaultValue(true).build());

        useVanillaKeys = sgDetection.add(new BoolSetting.Builder()
            .name("use-vanilla-keys").description("Recognise messages by the vanilla translation keys first.")
            .defaultValue(true).build());

        incomingPattern = sgDetection.add(new StringSetting.Builder()
            .name("incoming-pattern").description("Fallback regex for received messages. Group 1 is the name, group 2 the text. An invalid pattern is ignored.")
            .defaultValue("^([A-Za-z0-9_]{1,16}) whispers(?: to you)?: (.+)$").onChanged(MessengerSettings::checkPattern).build());

        outgoingPattern = sgDetection.add(new StringSetting.Builder()
            .name("outgoing-pattern").description("Fallback regex for the echo of sent messages. Group 1 is the name, group 2 the text. An invalid pattern is ignored.")
            .defaultValue("^(?:to|You whisper to) ([A-Za-z0-9_]{1,16}): (.+)$").onChanged(MessengerSettings::checkPattern).build());

        sound = sgNotification.add(new EnumSetting.Builder<Sound>()
            .name("sound").description("Sound played when a message arrives.")
            .defaultValue(Sound.Pling).build());

        soundVolume = sgNotification.add(new DoubleSetting.Builder()
            .name("sound-volume").description("Volume of the notification sound.")
            .defaultValue(0.6).min(0.0).max(1.0).sliderRange(0.0, 1.0)
            .visible(() -> sound.get() != Sound.None).build());

        chatNotice = sgNotification.add(new BoolSetting.Builder()
            .name("chat-notice").description("Print a line in the chat for every message received.")
            .defaultValue(false).build());

        windowWidth = sgWindows.add(new IntSetting.Builder()
            .name("window-width").description("Width of a conversation window in pixels.")
            .defaultValue(380).min(220).max(900).sliderRange(260, 600).build());

        windowLines = sgWindows.add(new IntSetting.Builder()
            .name("window-lines").description("Height of the message area, in lines.")
            .defaultValue(10).min(3).max(40).sliderRange(5, 20).build());

        textScale = sgWindows.add(new DoubleSetting.Builder()
            .name("text-scale").description("Scale of the text.")
            .defaultValue(0.8).min(0.4).max(2.0).sliderRange(0.5, 1.5).build());

        textShadow = sgWindows.add(new BoolSetting.Builder()
            .name("text-shadow").description("Render a shadow behind text.")
            .defaultValue(true).build());

        lineSpacing = sgWindows.add(new DoubleSetting.Builder()
            .name("line-spacing").description("Extra space between lines, in pixels.")
            .defaultValue(3.0).min(0.0).max(12.0).sliderRange(0.0, 8.0).build());

        cornerRadius = sgWindows.add(new DoubleSetting.Builder()
            .name("corner-radius").description("Roundness of panels and bubbles, in pixels.")
            .defaultValue(8.0).min(0.0).max(16.0).sliderRange(0.0, 12.0).build());

        timestamps = sgWindows.add(new BoolSetting.Builder()
            .name("timestamps").description("Show the time under every message.")
            .defaultValue(false).build());

        lockPositions = sgWindows.add(new BoolSetting.Builder()
            .name("lock-positions").description("Stop windows from being dragged.")
            .defaultValue(false).build());

        animations = sgWindows.add(new BoolSetting.Builder()
            .name("animations").description("Fade windows in and pulse counters on news.")
            .defaultValue(true).build());

        listSide = sgWindows.add(new EnumSetting.Builder<ListSide>()
            .name("list-side").description("Where the contact list opens relative to the button.")
            .defaultValue(ListSide.Auto).build());

        backgroundColor = sgColors.add(new ColorSetting.Builder()
            .name("background-color").description("Window background. The alpha is the transparency.")
            .defaultValue(new SettingColor(18, 22, 30, 150)).build());

        headerColor = sgColors.add(new ColorSetting.Builder()
            .name("header-color").description("Window header.")
            .defaultValue(new SettingColor(30, 38, 52, 170)).build());

        incomingColor = sgColors.add(new ColorSetting.Builder()
            .name("incoming-color").description("Bubbles of received messages.")
            .defaultValue(new SettingColor(44, 54, 72, 170)).build());

        outgoingColor = sgColors.add(new ColorSetting.Builder()
            .name("outgoing-color").description("Bubbles of sent messages.")
            .defaultValue(new SettingColor(40, 110, 170, 200)).build());

        textColor = sgColors.add(new ColorSetting.Builder()
            .name("text-color").description("Main text.")
            .defaultValue(new SettingColor(235, 240, 248, 255)).build());

        dimColor = sgColors.add(new ColorSetting.Builder()
            .name("dim-color").description("Secondary text, times and hints.")
            .defaultValue(new SettingColor(140, 152, 170, 255)).build());

        accentColor = sgColors.add(new ColorSetting.Builder()
            .name("accent-color").description("Unread badges and highlights.")
            .defaultValue(new SettingColor(255, 170, 60, 255)).build());

        dangerColor = sgColors.add(new ColorSetting.Builder()
            .name("danger-color").description("Remove, clear and ignore buttons on hover.")
            .defaultValue(new SettingColor(230, 80, 80, 255)).build());

        glass = sgStyle.add(new EnumSetting.Builder<Glass>()
            .name("glass").description("Off keeps flat panels. Tint makes panels translucent with a light reflection and edges.")
            .defaultValue(Glass.Tint).build());

        sheen = sgStyle.add(new BoolSetting.Builder()
            .name("sheen").description("Light reflection on the top of panels and thin light and dark edges.")
            .defaultValue(true).visible(() -> glass.get() == Glass.Tint).build());

        peerColors = sgStyle.add(new BoolSetting.Builder()
            .name("peer-colors").description("Give every correspondent a color: side band and header tint in windows, dot in the contact list.")
            .defaultValue(true).build());

        bandWidth = sgStyle.add(new IntSetting.Builder()
            .name("band-width").description("Width of the colored side band, in pixels.")
            .defaultValue(3).min(0).max(8).sliderRange(0, 8).visible(peerColors::get).build());

        selectionColor = sgStyle.add(new ColorSetting.Builder()
            .name("selection-color").description("Highlight behind selected text.")
            .defaultValue(new SettingColor(255, 170, 60, 80)).build());

        copyWithNames = sgStyle.add(new BoolSetting.Builder()
            .name("copy-with-names").description("Put the sender name before every message when copying more than one message.")
            .defaultValue(false).build());
    }
}
