package com.hunterbuddy.modules.regear.arealoader;

import java.io.File;
import java.io.IOException;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.MinecraftClient;

final class RecoverySaveWriter {
    private RecoverySaveWriter() {
    }

    static boolean write(AreaLoader area, String modeName, Object data) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) {
            ChatUtils.error("Join a world before saving the recovery");
            return false;
        }
        File file = AreaSaveFiles.fileFor(area, modeName, AreaSaveFiles.suffixOf(mc.world.getRegistryKey()));
        try {
            AreaSaveFiles.writeJson(file, data);
            return true;
        } catch (IOException e) {
            ChatUtils.error("Could not write %s: %s", file.getAbsolutePath(), e.getMessage());
            return false;
        }
    }
}
