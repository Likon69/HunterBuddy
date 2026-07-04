package com.hunterbuddy.modules.regear.util;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

/**
 * Reference parity: mlep.util.EnemyColorManager. Holds a single shared
 * {@code Setting<SettingColor>} used as the "enemy color" across modules.
 */
public class EnemyColorManager {
    private static Setting<SettingColor> enemyColorSetting;

    public static void setEnemyColorSetting(Setting<SettingColor> setting) {
        enemyColorSetting = setting;
    }

    public static Setting<SettingColor> getEnemyColorSetting() {
        return enemyColorSetting;
    }
}
