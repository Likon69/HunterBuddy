package com.hunterbuddy.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.io.File;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;

/**
 * Wipes AreaLoader's saved progress from disk.
 *
 * <p>The module's own "Clear All" button only reaches the profile currently
 * named in its {@code save-name} setting: the files live under
 * {@code meteor-client/arealoader/<save-name>/}. A profile left over from an
 * earlier name therefore survives every click, and gets picked up again the day
 * that name comes back — which is how a stale spiral angle reappears long after
 * everything looked cleared.
 *
 * <p>This deletes every profile, so there is nothing left anywhere to reload.
 */
public class AreaLoaderReset extends Command {
    public AreaLoaderReset() {
        super("arealoaderreset", "Delete every AreaLoader saved profile, not just the selected one.", new String[0]);
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            File baseDir = new File(MeteorClient.FOLDER, "arealoader");

            if (!baseDir.exists()) {
                this.info("No AreaLoader data on disk — nothing to reset.", new Object[0]);
                return 1;
            }

            int profiles = 0;
            int files = 0;

            File[] children = baseDir.listFiles();
            if (children != null) {
                for (File profile : children) {
                    if (!profile.isDirectory()) continue;

                    File[] saves = profile.listFiles();
                    if (saves != null) {
                        for (File save : saves) {
                            if (save.isFile() && save.delete()) files++;
                        }
                    }

                    profile.delete();
                    profiles++;
                }
            }

            if (files == 0) {
                this.info("Found %d profile folder(s) but no save files to delete.", new Object[]{profiles});
            } else {
                this.info("Deleted %d save file(s) across %d profile(s).", new Object[]{files, profiles});
            }

            this.info("Disable and re-enable AreaLoader to start fresh from your position.", new Object[0]);
            return 1;
        });
    }
}
