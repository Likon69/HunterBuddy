package com.hunterbuddy.modules.mine;

import com.hunterbuddy.modules.HMine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;
import org.joml.Vector3d;

public class MineRender {
    public record Look(HMine.RenderStyle style, ShapeMode shape, SettingColor mine, SettingColor done, SettingColor queue,
                       boolean outline, boolean chain, boolean numbers, boolean queueOn, double pulseRate, int fadeMs) {
    }

    private static final class Fade {
        private boolean state;
        private long instant;
        private final int duration;

        private Fade(boolean state, int duration) {
            this.state = state;
            this.duration = duration;
            this.instant = System.currentTimeMillis();
        }

        private void set(boolean value) {
            if (this.state != value) {
                this.state = value;
                this.instant = System.currentTimeMillis();
            }
        }

        private float factor() {
            if (this.state) {
                return 1.0F;
            }

            float elapsed = (float) (System.currentTimeMillis() - this.instant);
            return 1.0F - Math.min(1.0F, elapsed / (float) this.duration);
        }
    }

    private final MinecraftClient mc = MeteorClient.mc;
    private final Map<HMine.MiningData, Fade> fades = new HashMap<>();

    public void clear() {
        this.fades.clear();
    }

    public void draw3D(Render3DEvent e, World world, List<HMine.MiningData> active, ToDoubleFunction<HMine.MiningData> fullAt,
                       List<BlockPos> waiting, Look look) {
        if (look.queueOn() && !waiting.isEmpty()) {
            this.drawWaiting(e, world, active, waiting, look);
        }

        for (HMine.MiningData d : active) {
            if (!this.cleared(world, d.pos()) && !this.fades.containsKey(d)) {
                this.fades.put(d, new Fade(true, look.fadeMs()));
            }
        }

        for (Map.Entry<HMine.MiningData, Fade> entry : this.fades.entrySet()) {
            HMine.MiningData k = entry.getKey();
            entry.getValue().set(active.contains(k) && !this.cleared(world, k.pos()));
        }

        List<HMine.MiningData> expired = new ArrayList<>();
        for (Map.Entry<HMine.MiningData, Fade> entry : this.fades.entrySet()) {
            Fade fade = entry.getValue();
            float factor = fade.factor();
            this.drawBlock(e, world, entry.getKey(), factor, fade.instant, fullAt, look);
            if (factor <= 0.0F) {
                expired.add(entry.getKey());
            }
        }

        for (HMine.MiningData k : expired) {
            this.fades.remove(k);
        }
    }

    public void draw2D(Render2DEvent e, List<BlockPos> waiting, Look look) {
        for (int i = 0; i < waiting.size(); i++) {
            BlockPos pos = waiting.get(i);
            Vector3d screen = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.85, pos.getZ() + 0.5);
            if (!NametagUtils.to2D(screen, 1.0)) {
                continue;
            }

            String text = String.valueOf(i + 1);
            int width = this.mc.textRenderer.getWidth(text);
            NametagUtils.begin(screen, e.drawContext);
            e.drawContext.fill(-width / 2 - 2, -1, width / 2 + 2, 9, new Color(0, 0, 0, 150).getPacked());
            e.drawContext.drawText(this.mc.textRenderer, text, -width / 2, 0,
                new Color(look.queue().r, look.queue().g, look.queue().b, 255).getPacked(), true);
            NametagUtils.end(e.drawContext);
        }
    }

    private void drawWaiting(Render3DEvent e, World world, List<HMine.MiningData> active, List<BlockPos> waiting, Look look) {
        Vec3d previous = null;
        for (HMine.MiningData d : active) {
            if (!this.cleared(world, d.pos())) {
                previous = d.pos().toCenterPos();
                break;
            }
        }

        for (int i = 0; i < waiting.size(); i++) {
            Vec3d center = waiting.get(i).toCenterPos();
            int rank = Math.min(i, 4);
            double half = (0.62 - 0.06 * rank) / 2.0;
            double glow = 1.0 - 0.13 * rank;

            if (look.chain() && previous != null) {
                e.renderer.line(previous.x, previous.y, previous.z, center.x, center.y, center.z,
                    shade(look.queue(), (int) Math.round(90 * glow)), shade(look.queue(), (int) Math.round(220 * glow)));
            }

            Box box = new Box(center.x - half, center.y - half, center.z - half, center.x + half, center.y + half, center.z + half);
            this.box(e, box, look.queue(), (int) Math.round(90 * glow), (int) Math.round(255 * glow), look.shape());
            previous = center;
        }
    }

    private void drawBlock(Render3DEvent e, World world, HMine.MiningData k, float factor, long since,
                           ToDoubleFunction<HMine.MiningData> fullAt, Look look) {
        if (factor <= 0.0F) {
            return;
        }

        BlockPos pos = k.pos();
        BlockState state = world.getBlockState(pos);
        boolean cleared = this.cleared(world, pos);
        float damage = k.progress();
        float lastDamage = k.previousProgress();
        float total = (float) fullAt.applyAsDouble(k);

        VoxelShape shape = state.getOutlineShape(world, pos);
        Box local = (shape.isEmpty() ? VoxelShapes.fullCube() : shape).getBoundingBox();
        Box full = local.offset(pos);
        boolean finished = cleared || damage >= 0.95F;
        SettingColor base = finished ? look.done() : look.mine();

        if (look.outline()) {
            this.box(e, full, base, 0, (int) Math.round(55 * factor), ShapeMode.Lines);
        }

        double scale = cleared ? 1.0 : MathHelper.clamp((damage + (damage - lastDamage) * e.tickDelta) / total, 0.0F, 1.0F);
        double width = full.maxX - full.minX;
        double height = full.maxY - full.minY;
        double depth = full.maxZ - full.minZ;

        if (look.style() == HMine.RenderStyle.CLASSIC) {
            double cx = (full.minX + full.maxX) / 2.0;
            double cy = (full.minY + full.maxY) / 2.0;
            double cz = (full.minZ + full.maxZ) / 2.0;
            double hx = width / 2.0 * scale;
            double hy = height / 2.0 * scale;
            double hz = depth / 2.0 * scale;
            Box grown = new Box(cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz);
            this.box(e, grown, base, (int) Math.round(40 * factor), (int) Math.round(100 * factor), look.shape());
            return;
        }

        boolean pulse = look.style() == HMine.RenderStyle.PULSE;
        double beat = pulse ? this.breath(since, look) : 0.0;
        double level = full.minY + height * scale;

        Box fill = new Box(full.minX, full.minY, full.minZ, full.maxX, level, full.maxZ);
        double lift = pulse ? 0.7 * beat : 0.0;
        int alpha = (int) Math.round((pulse ? 30 + 110 * beat : 60) * factor);
        Color sides = new Color(
            base.r + (int) Math.round((255 - base.r) * lift),
            base.g + (int) Math.round((255 - base.g) * lift),
            base.b + (int) Math.round((255 - base.b) * lift),
            MathHelper.clamp(alpha * base.a / 255, 0, 255));
        e.renderer.box(fill, sides, new Color(0, 0, 0, 0), ShapeMode.Sides, 0);

        Box line = new Box(full.minX, level, full.minZ, full.maxX, level, full.maxZ);
        this.box(e, line, base, 0, (int) Math.round(230 * factor), ShapeMode.Lines);
    }

    private double breath(long since, Look look) {
        double seconds = (System.currentTimeMillis() - since) / 1000.0;
        return 0.5 - 0.5 * MathHelper.cos((float) (seconds * look.pulseRate() * 2.0 * Math.PI));
    }

    private boolean cleared(World world, BlockPos pos) {
        BlockState s = world.getBlockState(pos);
        return s.isAir() || s.getBlock() instanceof FluidBlock;
    }

    private void box(Render3DEvent e, Box b, Color c, int sidesAlpha, int linesAlpha, ShapeMode mode) {
        e.renderer.box(b, shade(c, sidesAlpha), shade(c, linesAlpha), mode, 0);
    }

    private static Color shade(Color c, int a) {
        return new Color(c.r, c.g, c.b, MathHelper.clamp(a * c.a / 255, 0, 255));
    }
}
