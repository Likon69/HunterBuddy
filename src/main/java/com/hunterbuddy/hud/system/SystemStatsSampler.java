package com.hunterbuddy.hud.system;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;

public final class SystemStatsSampler {
    public static final int CAPACITY = 60;
    public static final long INTERVAL_NANOS = 500_000_000L;
    private static final long GIB = 1L << 30;
    private final Source source;
    private final double[] ram = new double[CAPACITY];
    private final double[] cpu = new double[CAPACITY];
    private final StringBuilder buffer = new StringBuilder(64);
    private int head;
    private int count;
    private long lastSample;
    private boolean sampled;
    private long usedTenths = -1;
    private long maxTenths = -1;
    private long ramPercent = -1;
    private long cpuTenths = -2;
    private double ramUsage = -1;
    private double cpuUsage = -1;
    private int ramDigits = 2;
    private String ramText = "--";
    private String cpuText = "--";

    public interface Source {
        long usedMemory();
        long maxMemory();
        double processCpu();
    }

    public SystemStatsSampler() {
        this(new PlatformSource());
    }

    public SystemStatsSampler(Source source) {
        this.source = source;
    }

    public boolean refresh(long now) {
        if (sampled && now - lastSample < INTERVAL_NANOS) return false;
        sampled = true;
        lastSample = now;
        record(source.usedMemory(), source.maxMemory(), source.processCpu());
        return true;
    }

    void record(long used, long maximum, double processCpu) {
        used = Math.clamp(used, 0L, Math.max(0L, maximum));
        ramUsage = maximum > 0 ? used * 100.0 / maximum : -1;
        cpuUsage = Double.isFinite(processCpu) && processCpu >= 0 ? Math.min(100, processCpu) : -1;
        ram[head] = ramUsage;
        cpu[head] = cpuUsage;
        head = (head + 1) % CAPACITY;
        count = Math.min(CAPACITY, count + 1);
        long nextUsed = Math.round(used * (10.0 / GIB));
        long nextMax = Math.round(Math.max(0L, maximum) * (10.0 / GIB));
        long nextRam = Math.round(ramUsage);
        if (nextUsed != usedTenths || nextMax != maxTenths || nextRam != ramPercent) {
            usedTenths = nextUsed;
            maxTenths = nextMax;
            ramPercent = nextRam;
            ramDigits = 2;
            for (long value = (nextMax + 9) / 10; value >= 100; value /= 10) ramDigits++;
            buffer.setLength(0);
            if (maximum > 0) {
                decimal(nextUsed);
                buffer.append(" / ");
                decimal(nextMax);
                buffer.append(" GiB (").append(nextRam).append("%)");
                ramText = buffer.toString();
            } else ramText = "--";
        }
        long nextCpu = cpuUsage >= 0 ? Math.round(cpuUsage * 10) : -1;
        if (nextCpu != cpuTenths) {
            cpuTenths = nextCpu;
            buffer.setLength(0);
            if (nextCpu >= 0) {
                decimal(nextCpu);
                buffer.append('%');
                cpuText = buffer.toString();
            } else cpuText = "--";
        }
    }

    private void decimal(long tenths) {
        buffer.append(tenths / 10).append('.').append(tenths % 10);
    }

    public int count() { return count; }
    public int ramDigits() { return ramDigits; }
    public String ramText() { return ramText; }
    public String cpuText() { return cpuText; }
    public double ramUsage() { return ramUsage; }
    public double cpuUsage() { return cpuUsage; }

    public double history(boolean memory, int age) {
        if (age < 0 || age >= count) return -1;
        return (memory ? ram : cpu)[(head - 1 - age + CAPACITY) % CAPACITY];
    }

    public static SystemStatsSampler preview() {
        SystemStatsSampler preview = new SystemStatsSampler();
        for (int i = 0; i < CAPACITY; i++) {
            preview.record((long) ((2.4 + 0.3 * Math.sin(i * 0.18)) * GIB), 8 * GIB,
                18 + 10 * Math.sin(i * 0.25));
        }
        return preview;
    }

    private static final class PlatformSource implements Source {
        private final Runtime runtime = Runtime.getRuntime();
        private final OperatingSystemMXBean operatingSystem;

        private PlatformSource() {
            var bean = ManagementFactory.getOperatingSystemMXBean();
            operatingSystem = bean instanceof OperatingSystemMXBean supported ? supported : null;
        }

        @Override
        public long usedMemory() { return runtime.totalMemory() - runtime.freeMemory(); }

        @Override
        public long maxMemory() { return runtime.maxMemory(); }

        @Override
        public double processCpu() {
            if (operatingSystem == null) return -1;
            try {
                double load = operatingSystem.getProcessCpuLoad();
                return load >= 0 ? load * 100 : -1;
            } catch (UnsupportedOperationException | SecurityException ignored) {
                return -1;
            }
        }
    }
}
