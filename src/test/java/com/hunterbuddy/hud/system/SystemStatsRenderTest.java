package com.hunterbuddy.hud.system;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Locale;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SystemStatsRenderTest {
    private static final Color TEXT = new Color(236, 243, 252);
    private static final Color RAM = new Color(126, 200, 255);
    private static final Color CPU = new Color(110, 240, 130);
    private static final Color WARN = new Color(255, 200, 80);
    private static final Color CRITICAL = new Color(255, 80, 80);
    private static final Color BACKGROUND = new Color(17, 24, 35, 222);

    @Test
    void allLayoutsKeepTextBarsAndSparklineVerticesInsideTheirZones() throws Throwable {
        SystemStatsRenderLoader loader = new SystemStatsRenderLoader();
        SystemStatsRenderProbe probe = new SystemStatsRenderProbe();
        Class<?> rendererType = loader.loadClass("meteordevelopment.meteorclient.systems.hud.HudRenderer");
        Object renderer = rendererType.getConstructor(SystemStatsRenderProbe.class).newInstance(probe);
        Class<?> painterType = loader.loadClass("com.hunterbuddy.hud.system.SystemStatsPainter");
        Object painter = painterType.getConstructor().newInstance();
        SystemStatsSampler data = SystemStatsSampler.preview();
        for (int i = 0; i < 60; i++) data.record(i % 2 == 0 ? 0 : 8L << 30, 8L << 30, i % 3 == 0 ? -1 : i % 2 * 100);
        Method measure = measure(painterType, rendererType);
        Method draw = draw(painterType, rendererType);
        for (double scale : new double[] {0.5, 1, 3}) {
            for (boolean graph : new boolean[] {false, true}) {
                for (int length : new int[] {2, 10, 60}) {
                    for (int mask = 1; mask <= 3; mask++) {
                        boolean ram = (mask & 1) != 0;
                        boolean cpu = (mask & 2) != 0;
                        int rows = Integer.bitCount(mask);
                        measure.invoke(painter, renderer, data.ramDigits(), scale, graph, rows);
                        double width = (double) painterType.getMethod("width").invoke(painter);
                        double height = (double) painterType.getMethod("height").invoke(painter);
                        probe.left = 14;
                        probe.top = 14;
                        probe.right = probe.left + width;
                        probe.bottom = probe.top + height;
                        probe.graphLeft = probe.left;
                        probe.graphRight = probe.right;
                        probe.graphHeight = Math.max(8, 18 * scale);
                        probe.firstGraph = probe.top + 18 * 0.85 + 8 * scale + Math.max(2, 4 * scale);
                        probe.secondGraph = probe.firstGraph + 18 * 0.85 + 8 * scale + Math.max(2, 4 * scale)
                            + probe.graphHeight + 10 * scale;
                        probe.checking = true;
                        probe.triangles = 0;
                        bind(draw, painter, renderer, data, 14.0, 14.0, ram, cpu, graph, length,
                            70.0, 85.0, 50.0, 75.0, TEXT, RAM, CPU, WARN, CRITICAL).invokeExact();
                        assertTrue(probe.triangles <= rows * 60);
                    }
                }
            }
        }
    }

    @Test
    void valueChangesDoNotResizeTheCardAndMissingCpuDoesNotDrawASparkline() throws Throwable {
        SystemStatsRenderLoader loader = new SystemStatsRenderLoader();
        SystemStatsRenderProbe probe = new SystemStatsRenderProbe();
        Class<?> rendererType = loader.loadClass("meteordevelopment.meteorclient.systems.hud.HudRenderer");
        Object renderer = rendererType.getConstructor(SystemStatsRenderProbe.class).newInstance(probe);
        Class<?> painterType = loader.loadClass("com.hunterbuddy.hud.system.SystemStatsPainter");
        Object painter = painterType.getConstructor().newInstance();
        SystemStatsSampler data = new SystemStatsSampler();
        Method measure = measure(painterType, rendererType);
        measure.invoke(painter, renderer, 2, 1.0, true, 2);
        double width = (double) painterType.getMethod("width").invoke(painter);
        for (int value : new int[] {0, 9, 50, 99, 100}) {
            data.record((8L << 30) * value / 100, 8L << 30, -1);
            measure.invoke(painter, renderer, data.ramDigits(), 1.0, true, 2);
            assertEquals(width, (double) painterType.getMethod("width").invoke(painter));
        }
        bind(draw(painterType, rendererType), painter, renderer, data, 14.0, 14.0, false, true, true, 60,
            70.0, 85.0, 50.0, 75.0, TEXT, RAM, CPU, WARN, CRITICAL).invokeExact();
        assertEquals(0, probe.triangles);
    }

    @Test
    void measuresRenderCpuAndAllocationWithACommandSink() throws Throwable {
        SystemStatsRenderProbe probe = new SystemStatsRenderProbe();
        RenderLoop after = current(probe);
        for (int i = 0; i < 10_000; i++) after.render();
        ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!threads.isThreadAllocatedMemoryEnabled()) threads.setThreadAllocatedMemoryEnabled(true);
        Path baseline = Path.of("build/benchmarks/SystemStatsHud-before.class");
        RenderLoop before = Files.isRegularFile(baseline) ? baseline(baseline, probe) : null;
        if (before != null) for (int i = 0; i < 10_000; i++) before.render();
        double[] oldTimes = new double[9];
        double[] newTimes = new double[9];
        double[] oldBytes = new double[9];
        double[] newBytes = new double[9];
        long oldCommands = 0;
        long newCommands = 0;
        for (int round = 0; round < 9; round++) {
            if (before != null) {
                Measurement measurement = measure(before, probe, threads);
                oldTimes[round] = measurement.nanos;
                oldBytes[round] = measurement.bytes;
                oldCommands = measurement.commands;
            }
            Measurement measurement = measure(after, probe, threads);
            newTimes[round] = measurement.nanos;
            newBytes[round] = measurement.bytes;
            newCommands = measurement.commands;
        }
        Arrays.sort(oldTimes);
        Arrays.sort(newTimes);
        Arrays.sort(oldBytes);
        Arrays.sort(newBytes);
        assertEquals(0, newBytes[4], "New painter and glass frame must not allocate per render");
        assertTrue(probe.checksum > 0);
        String report = String.format(Locale.ROOT,
            "SystemStats CPU command-submission benchmark%nJava %s, %s %s, %d processors%n"
                + "Identical 60-sample traces, RAM and CPU, graphs enabled; new Glass card, scale 1; old defaults.%n"
                + "10000 warmup frames, 9 batches of 2000 frames, median batch mean.%n"
                + "Deterministic text metrics and command sink; excludes actual font drawing, Minecraft, GPU and blur.%n"
                + "Baseline available: %s%nBefore: %.3f us/frame, %.1f bytes/frame, %d commands/frame%n"
                + "After: %.3f us/frame, %.1f bytes/frame, %d commands/frame%n",
            System.getProperty("java.version"), System.getProperty("os.name"), System.getProperty("os.arch"),
            Runtime.getRuntime().availableProcessors(), before != null, oldTimes[4] / 1000, oldBytes[4], oldCommands,
            newTimes[4] / 1000, newBytes[4], newCommands);
        System.out.print(report);
        Path output = Path.of("build/reports/systemstats/benchmark.txt");
        Files.createDirectories(output.getParent());
        Files.writeString(output, report);
    }

    private static Measurement measure(RenderLoop loop, SystemStatsRenderProbe probe, ThreadMXBean threads) throws Throwable {
        long id = Thread.currentThread().threadId();
        long commands = probe.commands;
        long allocated = threads.getThreadAllocatedBytes(id);
        long start = System.nanoTime();
        for (int i = 0; i < 2000; i++) loop.render();
        long elapsed = System.nanoTime() - start;
        long bytes = threads.getThreadAllocatedBytes(id) - allocated;
        return new Measurement(elapsed / 2000.0, bytes / 2000.0, (probe.commands - commands) / 2000);
    }

    private static RenderLoop current(SystemStatsRenderProbe probe) throws Throwable {
        SystemStatsRenderLoader loader = new SystemStatsRenderLoader();
        Class<?> rendererType = loader.loadClass("meteordevelopment.meteorclient.systems.hud.HudRenderer");
        Object renderer = rendererType.getConstructor(SystemStatsRenderProbe.class).newInstance(probe);
        Class<?> painterType = loader.loadClass("com.hunterbuddy.hud.system.SystemStatsPainter");
        Object painter = painterType.getConstructor().newInstance();
        SystemStatsSampler data = benchmarkData();
        MethodHandle measure = bind(measure(painterType, rendererType), painter, renderer, data.ramDigits(), 1.0, true, 2);
        measure.invokeExact();
        double width = (double) painterType.getMethod("width").invoke(painter);
        double height = (double) painterType.getMethod("height").invoke(painter);
        Class<?> glassType = loader.loadClass("com.hunterbuddy.hud.glass.GlassPainter");
        Object glass = glassType.getConstructor().newInstance();
        MethodHandle card = bind(glassType.getMethod("card", rendererType, double.class, double.class,
            double.class, double.class, double.class, double.class, boolean.class, Color.class),
            glass, renderer, 0.0, 0.0, width + 28, height + 28, 1.0, 1.0, true, BACKGROUND);
        MethodHandle accent = bind(glassType.getMethod("accentBar", rendererType, double.class, double.class,
            double.class, double.class, double.class, Color.class), glass, renderer, 0.0, 0.0, height + 28, 1.0, 1.0, RAM);
        MethodHandle draw = bind(draw(painterType, rendererType), painter, renderer, data, 14.0, 14.0, true, true, true, 60,
            70.0, 85.0, 50.0, 75.0, TEXT, RAM, CPU, WARN, CRITICAL);
        return new RenderLoop(measure, card, accent, draw);
    }

    private static RenderLoop baseline(Path path, SystemStatsRenderProbe probe) throws Throwable {
        SystemStatsRenderLoader loader = new SystemStatsRenderLoader(path);
        Class<?> rendererType = loader.loadClass("meteordevelopment.meteorclient.systems.hud.HudRenderer");
        Object renderer = rendererType.getConstructor(SystemStatsRenderProbe.class).newInstance(probe);
        Class<?> type = loader.loadClass("com.hunterbuddy.hud.SystemStatsHud");
        Object hud = type.getConstructor().newInstance();
        for (var field : type.getDeclaredFields()) {
            field.setAccessible(true);
            if (field.getType() != SystemStatsRenderProbe.Value.class) continue;
            Object value = switch (field.getName()) {
                case "titleText" -> "System";
                case "textScale" -> 1.0;
                case "graphWidth" -> 160;
                case "graphHeight" -> 50;
                case "showTitle", "textShadow", "showGrid", "fillGraph", "showValueOnGraph", "showPeakOnGraph", "showThresholdLines" -> true;
                case "showAverageOnGraph" -> false;
                case "warnRamPct" -> 70.0;
                case "dangerRamPct" -> 85.0;
                case "warnCpuPct" -> 50.0;
                case "dangerCpuPct" -> 75.0;
                default -> field.getName().endsWith("Color") ? new SettingColor(236, 243, 252, 255) : 60;
            };
            field.set(hud, new SystemStatsRenderProbe.Value(value));
        }
        SystemStatsSampler data = benchmarkData();
        for (String history : new String[] {"ramHistory", "cpuHistory"}) {
            ArrayDeque<Double> values = new ArrayDeque<>();
            for (int age = 59; age >= 0; age--) values.add(data.history(history.equals("ramHistory"), age));
            set(type, hud, history, values);
        }
        set(type, hud, "currentRamUsedBytes", Math.round((8L << 30) * data.ramUsage() / 100));
        set(type, hud, "maxRamBytes", 8L << 30);
        set(type, hud, "currentRamPct", data.ramUsage());
        set(type, hud, "smoothedRamPct", data.ramUsage());
        set(type, hud, "currentCpuPct", data.cpuUsage());
        set(type, hud, "peakRamPct", 50.0);
        set(type, hud, "peakCpuPct", 50.0);
        return new RenderLoop(bind(type.getMethod("render", rendererType), hud, renderer));
    }

    private static SystemStatsSampler benchmarkData() {
        SystemStatsSampler data = new SystemStatsSampler();
        for (int i = 0; i < 60; i++) {
            double percent = 30 + 20 * Math.sin(i * 0.2);
            data.record(Math.round((8L << 30) * percent / 100), 8L << 30, percent);
        }
        return data;
    }
    private static void set(Class<?> type, Object instance, String name, Object value) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static Method measure(Class<?> painter, Class<?> renderer) throws Exception {
        return painter.getMethod("measure", renderer, int.class, double.class, boolean.class, int.class);
    }

    private static Method draw(Class<?> painter, Class<?> renderer) throws Exception {
        return painter.getMethod("draw", renderer, SystemStatsSampler.class, double.class, double.class,
            boolean.class, boolean.class, boolean.class, int.class, double.class, double.class, double.class, double.class,
            Color.class, Color.class, Color.class, Color.class, Color.class);
    }

    private static MethodHandle bind(Method method, Object instance, Object... arguments) throws Exception {
        return MethodHandles.insertArguments(MethodHandles.lookup().unreflect(method).bindTo(instance), 0, arguments);
    }

    private record Measurement(double nanos, double bytes, long commands) { }

    private record RenderLoop(MethodHandle... steps) {
        void render() throws Throwable {
            for (MethodHandle step : steps) step.invokeExact();
        }
    }
}
