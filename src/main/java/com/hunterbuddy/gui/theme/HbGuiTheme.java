package com.hunterbuddy.gui.theme;

import com.hunterbuddy.gui.theme.widgets.HbCheckbox;
import com.hunterbuddy.gui.theme.widgets.HbButton;
import com.hunterbuddy.gui.theme.widgets.HbConfirmedButton;
import com.hunterbuddy.gui.theme.widgets.HbSeparator;
import com.hunterbuddy.gui.theme.widgets.HbTextBox;
import com.hunterbuddy.gui.theme.widgets.HbLabel;
import com.hunterbuddy.gui.theme.widgets.HbMultiLabel;
import com.hunterbuddy.gui.theme.widgets.HbCategoryActivity;
import com.hunterbuddy.gui.theme.widgets.HbDropdown;
import com.hunterbuddy.gui.theme.widgets.HbFavorite;
import com.hunterbuddy.gui.theme.widgets.HbModuleEntry;
import com.hunterbuddy.gui.theme.screens.HbModulesScreen;
import com.hunterbuddy.gui.theme.widgets.HbSlider;
import com.hunterbuddy.gui.theme.widgets.HbTriangle;
import com.hunterbuddy.gui.theme.widgets.HbWindow;
import com.hunterbuddy.gui.theme.widgets.HbMeteorWindow;
import com.hunterbuddy.modules.mixin.accessors.GuiThemeNameAccessor;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.renderer.packer.GuiTexture;
import meteordevelopment.meteorclient.gui.utils.CharFilter;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.WHorizontalSeparator;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.input.WSlider;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WConfirmedButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WFavorite;
import meteordevelopment.meteorclient.gui.widgets.pressable.WTriangle;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.ModuleListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.nbt.NbtCompound;

import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class HbGuiTheme extends MeteorGuiTheme {
    private final HbStyle style;
    private final HbTypography typography;
    private final HbMetrics metrics = new HbMetrics();
    public final Setting<SettingColor> inputColor;
    public final Setting<SettingColor> riskColor;
    public final Setting<SettingColor> onAccentColor;
    public final Setting<String> fontFamily;
    public final Setting<List<Module>> riskModules;
    public final Setting<List<Module>> separatorBeforeModules;
    private final HbCategoryRegistry categories = new HbCategoryRegistry();
    private final HbColors colors = new HbColors(this);
    private int nativeWidgetDepth;
    private boolean loading;
    private final TextRenderer readableNativeText = new HbReadableText();

    public HbGuiTheme(String name, HbStyle style) {
        ((GuiThemeNameAccessor) (Object) this).hunterbuddy$setName(name);
        this.style = style;
        SettingGroup design = settings.createGroup("HunterBuddy Design");
        inputColor = designColor(design, "input-background-color", "Background of inputs, dropdowns and buttons.");
        riskColor = designColor(design, "risk-color", "Marker color of risky modules.");
        onAccentColor = designColor(design, "accent-text-color", "Text and glyph color drawn on top of the accent color.");
        style.palette().apply(this);
        fontFamily = design.add(new StringSetting.Builder()
            .name("font-family")
            .description("Theme font family. Uses the installed family or Meteor's bundled JetBrains Mono. No font is downloaded.")
            .defaultValue(style.fontFamily()).onChanged(value -> invalidateScreen()).build());
        riskModules = design.add(new ModuleListSetting.Builder().name("risk-modules")
            .description("Modules marked as risky. This is a personal visual classification.").build());
        separatorBeforeModules = design.add(new ModuleListSetting.Builder().name("separator-before-modules")
            .description("Draw an Encre group separator before these modules.")
            .onChanged(value -> invalidateScreen()).build());
        design.add(new EnumSetting.Builder<AccentPreset>().name("accent-preset")
            .description("Apply a mockup color option. The Accent color remains freely editable.")
            .defaultValue(AccentPreset.Default).onChanged(this::applyPreset).build());
        typography = new HbTypography(this);
        settingsFactory = new HbSettingsWidgetFactory(this);
    }

    private static Setting<SettingColor> designColor(SettingGroup group, String name, String description) {
        return group.add(new ColorSetting.Builder().name(name).description(description).defaultValue(new SettingColor()).build());
    }

    @Override
    public GuiTheme fromTag(NbtCompound tag) {
        loading = true;
        try {
            return super.fromTag(tag);
        } finally {
            loading = false;
        }
    }

    public static void register() {
        GuiThemes.add(new HbGuiTheme("HunterBuddy Lanterne", new LanterneStyle()));
        GuiThemes.add(new HbGuiTheme("HunterBuddy Echo", new EchoStyle()));
        GuiThemes.add(new HbGuiTheme("HunterBuddy Encre", new EncreStyle()));
    }

    public HbStyle style() {
        return style;
    }

    public Color accent() {
        return accentColor.get();
    }

    public HbTypography typography() {
        return typography;
    }

    @Override
    public double scale(double value) {
        return super.scale(value);
    }

    public double hbScale(double value) {
        return super.scale(value * metrics.layoutScale());
    }

    public double layoutUnits(double value) {
        return value * metrics.layoutScale();
    }

    public HbColors colors() {
        return colors;
    }

    public boolean useHbWidgets() {
        return nativeWidgetDepth == 0 && mc.currentScreen instanceof HbModulesScreen;
    }

    public void beginNativeWidgets() {
        nativeWidgetDepth++;
    }

    public void endNativeWidgets() {
        nativeWidgetDepth--;
    }

    public double textSize(double size) {
        return size * metrics.textScale();
    }

    @Override
    public TextRenderer textRenderer() {
        return useHbWidgets() ? typography : readableNativeText;
    }

    @Override
    public void beforeRender() {
        super.beforeRender();
        if (metrics.refresh()) invalidateScreen();
        categories.update();
        HbAnim.beginFrame();
    }

    private void invalidateScreen() {
        if (mc.currentScreen instanceof WidgetScreen screen) screen.invalidate();
    }

    private void applyPreset(AccentPreset preset) {
        if (loading) return;
        int[] colors = style.inverted() ? new int[]{0xE8E4DA, 0xF4F1EA, 0xD9D4C5}
            : style.hasCounter() ? new int[]{0x3EDFC6, 0x5BC8FF, 0x7CF0A8}
            : new int[]{0xF2A33A, 0xE8B85A, 0xF07F3A};
        int rgb = colors[preset.ordinal()];
        Color accent = accent();
        accent.set((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, accent.a);
    }

    public enum AccentPreset {
        Default,
        AlternativeOne,
        AlternativeTwo
    }

    @Override
    public WWindow window(WWidget icon, String title) {
        HbCategoryActivity activity = categories.find(title);
        if (useHbWidgets() && (activity != null || title.equals("Search") || title.equals("Favorites"))) {
            return w(new HbWindow(icon, title, activity));
        }
        return w(new HbMeteorWindow(icon, title));
    }

    @Override
    public WHorizontalList horizontalList() {
        WHorizontalList list = super.horizontalList();
        if (useHbWidgets()) list.spacing = layoutUnits(list.spacing);
        return list;
    }

    @Override
    public WVerticalList verticalList() {
        WVerticalList list = super.verticalList();
        if (useHbWidgets()) list.spacing = layoutUnits(list.spacing);
        return list;
    }

    @Override
    public TabScreen modulesScreen() {
        return new HbModulesScreen(this);
    }

    @Override
    public WWidget module(Module module, String title) {
        return useHbWidgets() ? w(new HbModuleEntry(module, title)) : super.module(module, title);
    }

    @Override
    public WFavorite favorite(boolean checked) {
        return useHbWidgets() ? w(new HbFavorite(checked)) : super.favorite(checked);
    }

    @Override
    public WSlider slider(double value, double min, double max) {
        return useHbWidgets() ? w(new HbSlider(value, min, max)) : super.slider(value, min, max);
    }

    @Override
    public WCheckbox checkbox(boolean checked) {
        return useHbWidgets() ? w(new HbCheckbox(checked)) : super.checkbox(checked);
    }

    @Override
    public <T> WDropdown<T> dropdown(T[] values, T value) {
        return useHbWidgets() ? w(new HbDropdown<>(values, value)) : super.dropdown(values, value);
    }

    @Override
    public WTriangle triangle() {
        return useHbWidgets() ? w(new HbTriangle()) : super.triangle();
    }

    @Override
    protected WButton button(String text, GuiTexture texture) {
        return useHbWidgets() ? w(new HbButton(text, texture)) : super.button(text, texture);
    }

    @Override
    protected WConfirmedButton confirmedButton(String text, String confirmText, GuiTexture texture) {
        return useHbWidgets() ? w(new HbConfirmedButton(text, confirmText, texture)) : super.confirmedButton(text, confirmText, texture);
    }

    @Override
    public WTextBox textBox(String text, String placeholder, CharFilter filter, Class<? extends WTextBox.Renderer> renderer) {
        return useHbWidgets() ? w(new HbTextBox(text, placeholder, filter, renderer)) : super.textBox(text, placeholder, filter, renderer);
    }

    @Override
    public WHorizontalSeparator horizontalSeparator(String text) {
        return useHbWidgets() ? w(new HbSeparator(text)) : super.horizontalSeparator(text);
    }

    @Override
    public WLabel label(String text, boolean title, double maxWidth) {
        if (!useHbWidgets()) return super.label(text, title, maxWidth);
        if (maxWidth == 0 && !text.contains("\n")) return w(new HbLabel(text, title));
        return w(new HbMultiLabel(text, title, maxWidth > 0 ? Math.min(maxWidth, 202) : maxWidth));
    }
}
