package com.hunterbuddy.commands;

import com.hunterbuddy.modules.ChunkRadar;
import com.hunterbuddy.util.TrailStore;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Reads and prunes the trails the radar remembers.
 *
 * <p>Nothing here prints a coordinate. The whole point of hiding them is that a
 * line pasted into a chat window is a line someone else can fly to, and a listing
 * is exactly the shape that gets pasted. A heading, a length and an age are
 * enough to know which entry you mean.
 */
public class Trails extends Command {
    public Trails() {
        super("trails", "List, forget or clear the trails chunk-radar remembers.", new String[0]);
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> list());

        builder.then(literal("list").executes(context -> list()));

        builder.then(literal("clear").executes(context -> {
            ChunkRadar radar = ChunkRadar.get();
            if (radar == null) return failed();

            List<TrailStore.Trail> all = List.copyOf(radar.trails().all());
            for (TrailStore.Trail trail : all) radar.forgetTrail(trail);

            this.info("Forgot %d trail(s).", new Object[]{all.size()});
            return 1;
        }));

        builder.then(literal("forget").then(argument("index", IntegerArgumentType.integer(1))
            .executes(context -> {
                ChunkRadar radar = ChunkRadar.get();
                if (radar == null) return failed();

                List<TrailStore.Trail> all = List.copyOf(radar.trails().all());
                int index = IntegerArgumentType.getInteger(context, "index");

                if (index > all.size()) {
                    this.warning("There is no trail %d; the list has %d.", new Object[]{index, all.size()});
                    return 1;
                }

                TrailStore.Trail trail = all.get(index - 1);
                radar.forgetTrail(trail);
                this.info("Forgot trail %d, heading %.0f.", new Object[]{index, trail.heading});
                return 1;
            })));
    }

    private int list() {
        ChunkRadar radar = ChunkRadar.get();
        if (radar == null) return failed();

        List<TrailStore.Trail> all = radar.trails().all();
        if (all.isEmpty()) {
            this.info("No trails remembered.", new Object[0]);
            return 1;
        }

        this.info("%d trail(s) remembered:", new Object[]{all.size()});

        for (int i = 0; i < all.size(); i++) {
            TrailStore.Trail trail = all.get(i);

            // A trail that turned a corner is stored as a chain of legs, so the
            // listing says which leg follows which rather than showing two
            // unrelated trails that happen to touch.
            String leg = "";
            if (trail.previous != null) {
                int before = indexOf(all, trail.previous);
                leg = before > 0 ? ", leg after " + before : ", later leg";
            }

            this.info("%d. heading %.0f, %.0f chunks long, %d hits, seen %s ago%s", new Object[]{
                i + 1, trail.heading, trail.length(), trail.hits, ago(trail.lastSeen), leg
            });
        }

        return 1;
    }

    private static int indexOf(List<TrailStore.Trail> all, String id) {
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(id)) return i + 1;
        }

        return 0;
    }

    private int failed() {
        this.warning("chunk-radar is not loaded.", new Object[0]);
        return 1;
    }

    private static String ago(long at) {
        if (at <= 0L) return "never";

        Duration since = Duration.ofMillis(System.currentTimeMillis() - at);
        long days = since.toDays();
        if (days > 0L) return days + "d";

        long hours = since.toHours();
        if (hours > 0L) return hours + "h";

        return String.format(Locale.ROOT, "%dm", Math.max(1L, since.toMinutes()));
    }
}
