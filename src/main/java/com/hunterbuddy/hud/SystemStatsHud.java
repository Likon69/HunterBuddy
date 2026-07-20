package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayDeque;
import java.util.Deque;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

public class SystemStatsHud extends HudElement {
   public static final HudElementInfo<SystemStatsHud> INFO = new HudElementInfo(
      HunterBuddyAddon.HUD_GROUP, "SystemStats", "RAM and CPU usage with line graphs.", SystemStatsHud::new
   );

   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgGraph = this.settings.createGroup("Graph");
   private final SettingGroup sgColors = this.settings.createGroup("Colors");

   private final Setting<String> titleText = sgGeneral.add(
      new meteordevelopment.meteorclient.settings.StringSetting.Builder()
         .name("title-text").description("Title text shown above the metrics.")
         .defaultValue("System").build()
   );

   private final Setting<Boolean> showTitle = sgGeneral.add(new Builder()
      .name("show-title").description("Display the HUD title.").defaultValue(true).build());

   private final Setting<Integer> sampleInterval = sgGeneral.add(
      new meteordevelopment.meteorclient.settings.IntSetting.Builder()
         .name("sample-interval")
         .description("Ticks between samples (20 ticks = 1 sec).")
         .defaultValue(20).min(5).max(200).sliderRange(5, 100)
         .build()
   );

   private final Setting<Double> textScale = sgGeneral.add(
      new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
         .name("text-scale").description("Scale of the text.")
         .defaultValue(1.0).min(0.5).max(2.0).sliderRange(0.5, 2.0)
         .build()
   );

   private final Setting<Boolean> textShadow = sgGeneral.add(new Builder()
      .name("text-shadow").description("Render shadow behind text.").defaultValue(true).build());

   private final Setting<Integer> graphPoints = sgGraph.add(
      new meteordevelopment.meteorclient.settings.IntSetting.Builder()
         .name("graph-points").description("Number of data points to keep.")
         .defaultValue(60).min(20).max(120).sliderRange(20, 120)
         .build()
   );

   private final Setting<Integer> graphWidth = sgGraph.add(
      new meteordevelopment.meteorclient.settings.IntSetting.Builder()
         .name("width").description("Graph width.")
         .defaultValue(160).min(80).max(300).sliderRange(80, 250)
         .build()
   );

   private final Setting<Integer> graphHeight = sgGraph.add(
      new meteordevelopment.meteorclient.settings.IntSetting.Builder()
         .name("height").description("Graph height.")
         .defaultValue(50).min(30).max(120).sliderRange(30, 100)
         .build()
   );

   private final Setting<Boolean> showGrid = sgGraph.add(new Builder()
      .name("show-grid").description("Show grid lines.").defaultValue(true).build());

   private final Setting<Boolean> fillGraph = sgGraph.add(new Builder()
      .name("fill-graph").description("Fill below the line.").defaultValue(true).build());

   private final Setting<Boolean> showValueOnGraph = sgGraph.add(new Builder()
      .name("show-value-on-graph").description("Display current value at top-right of each graph.")
      .defaultValue(true).build());

   private final Setting<Boolean> showPeakOnGraph = sgGraph.add(new Builder()
      .name("show-peak").description("Show peak value seen since session start.")
      .defaultValue(true).build());

   private final Setting<Boolean> showAverageOnGraph = sgGraph.add(new Builder()
      .name("show-average").description("Show average value over the history buffer.")
      .defaultValue(false).build());

   private final Setting<Boolean> showThresholdLines = sgGraph.add(new Builder()
      .name("show-threshold-lines").description("Draw horizontal lines at warn/danger thresholds.")
      .defaultValue(true).build());

   private final Setting<Double> ramSmoothing = sgGraph.add(
      new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
         .name("ram-smoothing")
         .description("Smooths the RAM graph to hide normal JVM garbage-collection sawtooths. 0 = raw samples.")
         .defaultValue(0.75).min(0.0).max(0.95).sliderRange(0.0, 0.9)
         .build()
   );

   private final Setting<SettingColor> titleColor = sgColors.add(
      new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
         .name("title-color").description("Title text color.")
         .defaultValue(new SettingColor(255, 255, 255, 255)).build()
   );

   private final Setting<SettingColor> ramTextColor = sgColors.add(
      new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
         .name("ram-text-color").description("RAM text color (normal).")
         .defaultValue(new SettingColor(180, 220, 255, 255)).build()
   );

   private final Setting<SettingColor> ramGraphColor = sgColors.add(
      new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
         .name("ram-graph-color").description("RAM graph line color.")
         .defaultValue(new SettingColor(100, 180, 255, 255)).build()
   );

   private final Setting<SettingColor> cpuTextColor = sgColors.add(
      new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
         .name("cpu-text-color").description("CPU text color (normal).")
         .defaultValue(new SettingColor(255, 200, 150, 255)).build()
   );

   private final Setting<SettingColor> cpuGraphColor = sgColors.add(
      new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
         .name("cpu-graph-color").description("CPU graph line color.")
         .defaultValue(new SettingColor(255, 140, 80, 255)).build()
   );

   private final Setting<SettingColor> graphBgColor = sgColors.add(
      new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
         .name("graph-background-color").description("Graph background color.")
         .defaultValue(new SettingColor(15, 15, 20, 180)).build()
   );

   private final Setting<SettingColor> graphGridColor = sgColors.add(
      new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
         .name("graph-grid-color").description("Graph grid line color.")
         .defaultValue(new SettingColor(60, 60, 70, 100)).build()
   );

   private final Setting<SettingColor> graphBorderColor = sgColors.add(
      new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
         .name("graph-border-color").description("Graph border color.")
         .defaultValue(new SettingColor(80, 80, 90, 150)).build()
   );

   private final Setting<SettingColor> graphWarnLineColor = sgColors.add(
      new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
         .name("warn-line-color").description("Color of the warn threshold line.")
         .defaultValue(new SettingColor(255, 200, 80, 120)).build()
   );

   private final Setting<SettingColor> graphDangerLineColor = sgColors.add(
      new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
         .name("danger-line-color").description("Color of the danger threshold line.")
         .defaultValue(new SettingColor(255, 80, 80, 150)).build()
   );

   private final Setting<Double> warnRamPct = sgColors.add(
      new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
         .name("ram-warn-threshold").description("RAM % for warning color.")
         .defaultValue(70.0).min(50.0).max(95.0).sliderRange(50.0, 95.0).build()
   );

   private final Setting<Double> dangerRamPct = sgColors.add(
      new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
         .name("ram-danger-threshold").description("RAM % for danger color.")
         .defaultValue(85.0).min(60.0).max(99.0).sliderRange(60.0, 99.0).build()
   );

   private final Setting<Double> warnCpuPct = sgColors.add(
      new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
         .name("cpu-warn-threshold").description("CPU % for warning color.")
         .defaultValue(50.0).min(20.0).max(90.0).sliderRange(20.0, 90.0).build()
   );

   private final Setting<Double> dangerCpuPct = sgColors.add(
      new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
         .name("cpu-danger-threshold").description("CPU % for danger color.")
         .defaultValue(75.0).min(40.0).max(99.0).sliderRange(40.0, 99.0).build()
   );

   private final Deque<Double> ramHistory = new ArrayDeque<>();
   private final Deque<Double> cpuHistory = new ArrayDeque<>();

   private double currentRamPct = 0.0;
   private double smoothedRamPct = 0.0;
   private long currentRamUsedBytes = 0;
   private long maxRamBytes = 0;
   private double currentCpuPct = 0.0;
   private double peakRamPct = 0.0;
   private double peakCpuPct = 0.0;
   private int tickCounter = 0;
   private boolean cpuSupported = true;
   private boolean ramGraphInitialized = false;

   public SystemStatsHud() {
      super(INFO);
      MeteorClient.EVENT_BUS.subscribe(this);
   }

   @EventHandler
   private void onTick(Post event) {
      this.sampleCurrent();
      this.tickCounter++;
      if (this.tickCounter >= this.sampleInterval.get()) {
         this.tickCounter = 0;
         this.pushSample();
      }
   }

   private void sampleCurrent() {
      Runtime rt = Runtime.getRuntime();
      this.currentRamUsedBytes = rt.totalMemory() - rt.freeMemory();
      this.maxRamBytes = rt.maxMemory();
      this.currentRamPct = this.maxRamBytes > 0 ? (this.currentRamUsedBytes * 100.0 / this.maxRamBytes) : 0.0;
      this.updateSmoothedRam();

      double cpu = this.sampleCpu();
      if (cpu < 0) {
         // CPU sample not available this tick (baseline not yet established, or unsupported JVM)
         // Keep previous value, but mark as unsupported after enough failed attempts
         if (!this.cpuSupported) {
            this.currentCpuPct = -1.0; // sentinel for "no data"
         }
      } else {
         this.cpuSupported = true;
         this.currentCpuPct = cpu;
      }

      if (this.smoothedRamPct > this.peakRamPct) this.peakRamPct = this.smoothedRamPct;
      if (this.currentCpuPct > this.peakCpuPct) this.peakCpuPct = this.currentCpuPct;
   }

   private void updateSmoothedRam() {
      if (!this.ramGraphInitialized) {
         this.smoothedRamPct = this.currentRamPct;
         this.ramGraphInitialized = true;
         return;
      }

      double smoothing = this.ramSmoothing.get();
      this.smoothedRamPct = smoothing == 0.0
         ? this.currentRamPct
         : this.smoothedRamPct * smoothing + this.currentRamPct * (1.0 - smoothing);
   }

   private double sampleCpu() {
      try {
         OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
         double load = osBean.getProcessCpuLoad();
         if (load < 0 || Double.isNaN(load)) return -1.0;
         return Math.min(100.0, load * 100.0);
      } catch (Throwable t) {
         this.cpuSupported = false;
         return -1.0;
      }
   }

   private void pushSample() {
      int max = this.graphPoints.get();
      if (this.currentRamPct >= 0) this.ramHistory.addLast(this.smoothedRamPct);
      if (this.currentCpuPct >= 0) this.cpuHistory.addLast(this.currentCpuPct);
      while (this.ramHistory.size() > max) this.ramHistory.pollFirst();
      while (this.cpuHistory.size() > max) this.cpuHistory.pollFirst();
   }

   private double computeAverage(Deque<Double> data) {
      if (data.isEmpty()) return 0.0;
      double sum = 0.0;
      for (Double v : data) sum += v;
      return sum / data.size();
   }

   private String formatBytes(long bytes) {
      if (bytes < 1024L * 1024L * 1024L) {
         return String.format("%d MB", bytes / (1024L * 1024L));
      }
      return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
   }

   @Override
   public void render(HudRenderer renderer) {
      boolean shadow = this.textShadow.get();
      double scale = this.textScale.get();
      double textHeight = renderer.textHeight(shadow, scale);
      double spacing = 3.0;

      double curY = this.y;
      double maxWidth = 0.0;
      double gw = this.graphWidth.get();

      if (this.showTitle.get()) {
         String title = this.titleText.get();
         double tw = renderer.textWidth(title, shadow, scale);
         renderer.text(title, this.x, curY, this.titleColor.get(), shadow, scale);
         curY += textHeight + spacing;
         maxWidth = Math.max(maxWidth, tw);
      }

      // === RAM ===
      double ramLabelW = renderer.textWidth("RAM", shadow, scale);
      renderer.text("RAM", this.x, curY, new SettingColor(140, 140, 140, 255), shadow, scale);

      String ramValueText;
      if (this.maxRamBytes <= 0) {
         ramValueText = " --";
      } else {
         ramValueText = String.format(" %s / %s (%.0f%%)",
            this.formatBytes(this.currentRamUsedBytes),
            this.formatBytes(this.maxRamBytes),
            this.currentRamPct);
      }
      SettingColor ramTextCol = this.currentRamPct >= this.dangerRamPct.get()
         ? new SettingColor(255, 80, 80, 255)
         : (this.currentRamPct >= this.warnRamPct.get()
            ? new SettingColor(255, 200, 80, 255)
            : this.ramTextColor.get());
      double ramValueW = renderer.textWidth(ramValueText, shadow, scale);
      renderer.text(ramValueText, this.x + ramLabelW, curY, ramTextCol, shadow, scale);
      curY += textHeight + 1.0;
      maxWidth = Math.max(maxWidth, ramLabelW + ramValueW);

      // RAM graph
      curY = this.renderGraph(renderer, this.x, curY, gw, this.ramHistory, this.ramGraphColor.get(),
         this.fillGraph.get(), this.showGrid.get(), this.showValueOnGraph.get(),
         this.showPeakOnGraph.get(), this.showAverageOnGraph.get(),
         this.showThresholdLines.get(), this.warnRamPct.get(), this.dangerRamPct.get(),
         this.peakRamPct, this.computeAverage(this.ramHistory),
         this.currentRamPct >= 0 ? String.format("%.0f%%", this.smoothedRamPct) : "--", shadow, scale);
      curY += spacing;
      maxWidth = Math.max(maxWidth, gw);

      // === CPU ===
      renderer.text("CPU", this.x, curY, new SettingColor(140, 140, 140, 255), shadow, scale);
      String cpuValueText;
      if (this.currentCpuPct < 0) {
         cpuValueText = " -- (unsupported)";
      } else {
         cpuValueText = String.format(" %.1f%%", this.currentCpuPct);
      }
      SettingColor cpuTextCol;
      if (this.currentCpuPct < 0) {
         cpuTextCol = new SettingColor(128, 128, 128, 255);
      } else if (this.currentCpuPct >= this.dangerCpuPct.get()) {
         cpuTextCol = new SettingColor(255, 80, 80, 255);
      } else if (this.currentCpuPct >= this.warnCpuPct.get()) {
         cpuTextCol = new SettingColor(255, 200, 80, 255);
      } else {
         cpuTextCol = this.cpuTextColor.get();
      }
      double cpuValueW = renderer.textWidth(cpuValueText, shadow, scale);
      renderer.text(cpuValueText, this.x + ramLabelW, curY, cpuTextCol, shadow, scale);
      curY += textHeight + 1.0;
      maxWidth = Math.max(maxWidth, ramLabelW + cpuValueW);

      // CPU graph
      curY = this.renderGraph(renderer, this.x, curY, gw, this.cpuHistory, this.cpuGraphColor.get(),
         this.fillGraph.get(), this.showGrid.get(), this.showValueOnGraph.get(),
         this.showPeakOnGraph.get(), this.showAverageOnGraph.get(),
         this.showThresholdLines.get(), this.warnCpuPct.get(), this.dangerCpuPct.get(),
         Math.max(0, this.peakCpuPct), this.computeAverage(this.cpuHistory),
         this.currentCpuPct >= 0 ? String.format("%.1f%%", this.currentCpuPct) : "--", shadow, scale);

      this.setSize(Math.max(maxWidth, 80.0), curY - this.y);
   }

   private double renderGraph(HudRenderer renderer, double gx, double gy, double w,
                              Deque<Double> data, SettingColor lineColor,
                              boolean fill, boolean grid, boolean showValue,
                              boolean showPeak, boolean showAverage,
                              boolean showThresholds, double warnPct, double dangerPct,
                              double peakValue, double averageValue,
                              String currentText, boolean shadow, double scale) {
      double gh = this.graphHeight.get();

      // Background
      renderer.quad(gx, gy, w, gh, this.graphBgColor.get());

      // Grid (horizontal lines at 25/50/75%)
      if (grid) {
         for (int i = 1; i <= 3; i++) {
            double gridY = gy + gh - gh * i / 4.0;
            renderer.quad(gx, gridY, w, 1.0, this.graphGridColor.get());
         }
      }

      // Threshold lines (warn / danger)
      if (showThresholds) {
         double warnY = gy + gh - Math.min(100.0, warnPct) / 100.0 * gh;
         double dangerY = gy + gh - Math.min(100.0, dangerPct) / 100.0 * gh;
         renderer.quad(gx, warnY, w, 1.0, this.graphWarnLineColor.get());
         renderer.quad(gx, dangerY, w, 1.5, this.graphDangerLineColor.get());
      }

      // Border
      renderer.quad(gx, gy, w, 1.0, this.graphBorderColor.get());
      renderer.quad(gx, gy + gh - 1.0, w, 1.0, this.graphBorderColor.get());
      renderer.quad(gx, gy, 1.0, gh, this.graphBorderColor.get());
      renderer.quad(gx + w - 1.0, gy, 1.0, gh, this.graphBorderColor.get());

      // Cache fill color (one allocation per graph instead of one per pixel segment)
      SettingColor fillCol = null;
      if (fill) {
         fillCol = new SettingColor(lineColor.r, lineColor.g, lineColor.b, 50);
      }

      // Plot line + fill — iterate deque directly, no array allocation
      int n = data.size();
      if (n >= 2) {
         Double prev = null;
         int idx = 0;
         for (Double curr : data) {
            if (prev != null) {
               double v1 = Math.max(0.0, Math.min(100.0, prev));
               double v2 = Math.max(0.0, Math.min(100.0, curr));
               double x1 = gx + (double) idx / (n - 1) * w;
               double x2 = gx + (double) (idx + 1) / (n - 1) * w;
               double y1 = gy + gh - v1 / 100.0 * gh;
               double y2 = gy + gh - v2 / 100.0 * gh;

               if (fill) {
                  int steps = Math.max(1, (int) (x2 - x1));
                  for (int s = 0; s <= steps; s++) {
                     double t = (double) s / steps;
                     double px = x1 + (x2 - x1) * t;
                     double py = y1 + (y2 - y1) * t;
                     double ph = gy + gh - py;
                     renderer.quad(px, py, 1.0, ph, fillCol);
                  }
               }
               this.drawLine(renderer, x1, y1, x2, y2, lineColor);
            }
            prev = curr;
            idx++;
         }
      }

      // Peak marker (small horizontal tick at peak Y position)
      if (showPeak && peakValue > 0) {
         double peakY = gy + gh - Math.min(100.0, peakValue) / 100.0 * gh;
         SettingColor peakCol = new SettingColor(255, 255, 100, 200);
         renderer.quad(gx, peakY - 0.5, w, 1.5, peakCol);
      }

      // Current value label at top-right
      if (showValue) {
         double vw = renderer.textWidth(currentText, shadow, scale);
         renderer.text(currentText, gx + w - vw - 2.0, gy + 1.0, lineColor, shadow, scale);
      }

      // Average label at top-left
      if (showAverage) {
         String avgStr = String.format("avg %.0f%%", averageValue);
         renderer.text(avgStr, gx + 2.0, gy + 1.0, new SettingColor(200, 200, 200, 200), shadow, scale);
      }

      return gy + gh;
   }

   private void drawLine(HudRenderer renderer, double x1, double y1, double x2, double y2, SettingColor color) {
      double dx = x2 - x1;
      double dy = y2 - y1;
      double len = Math.sqrt(dx * dx + dy * dy);
      if (len == 0.0) return;

      int steps = (int) Math.ceil(len);
      for (int i = 0; i <= steps; i++) {
         double t = (double) i / steps;
         double px = x1 + dx * t;
         double py = y1 + dy * t;
         renderer.quad(px - 0.5, py - 0.5, 1.5, 1.5, color);
      }
   }
}
