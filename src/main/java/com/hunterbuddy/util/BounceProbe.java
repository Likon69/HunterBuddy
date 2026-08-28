package com.hunterbuddy.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Locale;

import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.MeteorClient;

/**
 * A recorder for one question: what leaves and arrives around a bounce that ends in a setback.
 *
 * <p>It writes and nothing else. No packet is held, delayed, added or dropped by anything in
 * here, because the thing being measured is an order of packets a few ticks wide and any hand
 * on it would be measuring itself.
 *
 * <p>One line per event, into {@code bounce-probe.log} in the game folder, truncated every time
 * the module is switched on so a run stands on its own. Each line carries the tick count since
 * that switch-on and the milliseconds with it, which is what lets a landing be lined up against
 * the metadata that answers it a round trip later.
 */
public final class BounceProbe {
    private static final BounceProbe INSTANCE = new BounceProbe();

    private PrintWriter writer;
    private long startedAt;
    private int tick;

    private BounceProbe() {
    }

    public static BounceProbe get() {
        return INSTANCE;
    }

    /** Opens the file, replacing whatever the last run left in it. */
    public void start() {
        stop();

        try {
            File file = new File(MeteorClient.mc.runDirectory, "bounce-probe.log");
            writer = new PrintWriter(new BufferedWriter(new FileWriter(file, false)));
            startedAt = System.currentTimeMillis();
            tick = 0;
            writer.println("# bounce probe, one line per event, times in ms from this line");
            writer.flush();
        } catch (Exception e) {
            writer = null;
            HunterBuddyAddon.LOG.error("BounceProbe: could not open the probe log", e);
        }
    }

    public void stop() {
        if (writer == null) return;

        try {
            writer.flush();
            writer.close();
        } catch (Exception e) {
            HunterBuddyAddon.LOG.error("BounceProbe: could not close the probe log", e);
        }

        writer = null;
    }

    public boolean isRecording() {
        return writer != null;
    }

    /** Counts the tick the rest of a tick's lines are stamped with. */
    public void nextTick() {
        tick++;
    }

    /**
     * One event.
     *
     * <p>Flushed on every line rather than buffered: the run ends with a setback and often a
     * disconnect behind it, and the lines that matter are the last ones written.
     */
    public void line(String tag, String fields) {
        if (writer == null) return;

        try {
            writer.println(String.format(Locale.ROOT, "t=%d +%d %s %s", tick, System.currentTimeMillis() - startedAt, tag, fields));
            writer.flush();
        } catch (Exception e) {
            HunterBuddyAddon.LOG.error("BounceProbe: could not write to the probe log", e);
            stop();
        }
    }
}
