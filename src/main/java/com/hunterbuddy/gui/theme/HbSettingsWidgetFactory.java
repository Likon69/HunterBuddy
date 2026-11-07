package com.hunterbuddy.gui.theme;

import com.hunterbuddy.gui.theme.widgets.HbNumericSetting;
import com.hunterbuddy.gui.theme.widgets.HbSettingTable;
import meteordevelopment.meteorclient.gui.DefaultSettingsWidgetFactory;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;

import java.util.Locale;

public class HbSettingsWidgetFactory extends DefaultSettingsWidgetFactory {
    private final DefaultSettingsWidgetFactory nativeFactory;

    public HbSettingsWidgetFactory(HbGuiTheme theme) {
        super(theme);
        nativeFactory = new DefaultSettingsWidgetFactory(theme);
        Factory integer = factories.get(IntSetting.class);
        Factory decimal = factories.get(DoubleSetting.class);
        factories.put(IntSetting.class, (table, setting) -> {
            IntSetting number = (IntSetting) setting;
            if (number.noSlider || number.sliderMin == number.sliderMax) integer.create(table, setting);
            else numeric(table, setting, new HbNumericSetting(number));
        });
        factories.put(DoubleSetting.class, (table, setting) -> {
            DoubleSetting number = (DoubleSetting) setting;
            if (number.noSlider || number.sliderMin == number.sliderMax) decimal.create(table, setting);
            else numeric(table, setting, new HbNumericSetting(number));
        });
    }

    private void numeric(WTable table, Setting<?> setting, HbNumericSetting editor) {
        table.add(editor).right();
        WButton reset = table.add(theme.button(GuiRenderer.RESET)).widget();
        reset.action = () -> {
            setting.reset();
            editor.refresh();
        };
        reset.tooltip = "Reset";
    }

    @Override
    public WWidget create(GuiTheme theme, Settings settings, String filter) {
        if (!((HbGuiTheme) theme).clickGui()) return nativeFactory.create(theme, settings, filter);
        WVerticalList list = theme.verticalList();
        list.spacing = 0;
        for (SettingGroup group : settings.groups) {
            HbSettingTable table = new HbSettingTable();
            table.theme = theme;
            for (Setting<?> setting : group) {
                boolean visible = setting.isVisible();
                setting.lastWasVisible = visible;
                if (!visible || !setting.title.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))) continue;
                WLabel label = table.add(theme.label(setting.title)).widget();
                label.tooltip = setting.description;
                Factory factory = getFactory(setting.getClass());
                if (factory != null) factory.create(table, setting);
                table.row();
            }
            if (table.cells.isEmpty()) continue;
            WContainer container = list;
            if (settings.groups.size() > 1 || !"General".equals(group.name)) {
                var section = list.add(theme.section(group.name, group.sectionExpanded)).expandX().widget();
                section.spacing = 0;
                section.action = () -> group.sectionExpanded = section.isExpanded();
                container = section;
            }
            container.add(table).expandX().padHorizontal(0);
        }
        return list;
    }
}
