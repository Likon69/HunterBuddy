package com.hunterbuddy.pet;

import java.util.Arrays;
import java.util.Random;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import org.joml.Matrix3x2fStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;

public final class PetEffects {
    private final PetSfx sfx;
    private static final int BOTTLES = 6;
    private static final int PARTICLES = 96;
    private final float[] bx = new float[BOTTLES];
    private final float[] by = new float[BOTTLES];
    private final float[] bvx = new float[BOTTLES];
    private final float[] bvy = new float[BOTTLES];
    private final float[] rotation = new float[BOTTLES];
    private final float[] life = new float[BOTTLES];
    private final int[] bounces = new int[BOTTLES];
    private final float[] px = new float[PARTICLES];
    private final float[] py = new float[PARTICLES];
    private final float[] vx = new float[PARTICLES];
    private final float[] vy = new float[PARTICLES];
    private final float[] remaining = new float[PARTICLES];
    private final byte[] kind = new byte[PARTICLES];
    private final Random random = new Random();
    private float smokeTimer;
    private float coughAt;
    private float snoreAt;
    private float drunkAt;
    private boolean smokeSounded;
    private PetState lastState;
    private boolean lastSitDrink;
    private float clock;
    private int nextParticle;
    private static final OrderedText[] WORDS = {
        OrderedText.styledForwardsVisitedString("*BURP*", Style.EMPTY),
        OrderedText.styledForwardsVisitedString("*hic*", Style.EMPTY),
        OrderedText.styledForwardsVisitedString("*pschht*", Style.EMPTY),
        OrderedText.styledForwardsVisitedString("*cough*", Style.EMPTY),
        OrderedText.styledForwardsVisitedString("Zzz", Style.EMPTY),
        OrderedText.styledForwardsVisitedString("CLANG!", Style.EMPTY)
    };

    public PetEffects(PetSfx sfx) {
        this.sfx = sfx;
    }

    public void reset() {
        Arrays.fill(life, 0);
        Arrays.fill(remaining, 0);
        smokeTimer = 0;
        coughAt = snoreAt = drunkAt = 0;
        smokeSounded = false;
        lastState = null;
        lastSitDrink = false;
        clock = 0;
    }

    public void clearBottles() {
        Arrays.fill(life, 0);
    }

    public void throwBottle(float x, float y, int direction, boolean crosshair, int width, int height, float size) {
        for (int i = 0; i < BOTTLES; i++) {
            if (life[i] > 0) continue;
            bx[i] = x;
            by[i] = y;
            float targetX = crosshair ? width * 0.5f : random.nextFloat() * width;
            float targetY = crosshair ? height * 0.5f : height * (0.1f + random.nextFloat() * 0.5f);
            float time = 0.8f + random.nextFloat() * 0.6f;
            bvx[i] = (targetX - x) / time;
            bvy[i] = (targetY - y - 0.5f * size * 5 * time * time) / time;
            rotation[i] = direction;
            life[i] = 8;
            bounces[i] = 0;
            sfx.play(PetSfxAction.THROW);
            return;
        }
    }

    public void word(int index, float x, float y) {
        sfx.play(switch (index) {
            case 0 -> PetSfxAction.BURP;
            case 1 -> PetSfxAction.HIC;
            case 2 -> PetSfxAction.BEER_OPEN;
            case 3 -> PetSfxAction.COUGH;
            case 4 -> PetSfxAction.SNORE;
            case 5 -> PetSfxAction.BOTTLE_SHATTER;
            default -> throw new IllegalArgumentException();
        });
        emit((byte) (index + 2), x, y, (random.nextFloat() - 0.5f) * 8, -18, 2.2f);
    }

    public void update(float dt, PetBrain brain, PetMotion motion, PetResources.Rig rig, float size, float cigarX, float cigarY, float headX, float headY, boolean puppet) {
        clock += dt;
        smokeTimer += dt;
        if (lastSitDrink && !brain.sitDrink && random.nextFloat() < 0.6f) word(0, headX, headY);
        lastSitDrink = brain.sitDrink;
        if (brain.state != lastState) {
            if (lastState == PetState.DRINK && random.nextFloat() < 0.6f) word(0, headX, headY);
            coughAt = brain.state == PetState.SMOKE && random.nextFloat() < 0.4f ? clock + 1.5f + random.nextFloat() * 3 : 0;
            smokeSounded = false;
            lastState = brain.state;
        }
        boolean smoking = brain.state == PetState.SMOKE || brain.state == PetState.CUSTOM && brain.animation != null && brain.animation.smoke();
        if (puppet && smoking && smokeTimer > 0.13f) {
            smokeTimer = 0;
            if (!smokeSounded) {
                sfx.play(PetSfxAction.SMOKE_PUFF);
                smokeSounded = true;
            }
            emit((byte) 0, cigarX, cigarY, (random.nextFloat() - 0.5f) * 6, -size * 0.22f, 2.7f);
        }
        if (coughAt > 0 && clock >= coughAt) {
            coughAt = 0;
            word(3, headX, headY);
        }
        if (brain.grounded && brain.state == PetState.SLEEP) {
            if (snoreAt == 0) snoreAt = clock + 2;
            if (clock >= snoreAt) {
                snoreAt = clock + 5 + random.nextFloat() * 3;
                word(4, headX, headY);
            }
        } else snoreAt = 0;
        if (!brain.resting() && brain.drunkenness > 0.1f) {
            if (drunkAt == 0) drunkAt = clock + 30 + random.nextFloat() * 30;
            if (clock >= drunkAt) {
                drunkAt = clock + 30 + random.nextFloat() * 30;
                word(random.nextBoolean() ? 0 : 1, headX, headY);
            }
        } else drunkAt = 0;
        float radius = Math.max(2, size / rig.height() * (float) Math.hypot(
            Math.max(rig.neckX(), rig.bottleWidth() - rig.neckX()), Math.max(rig.neckY(), rig.bottleHeight() - rig.neckY())));
        for (int i = 0; i < BOTTLES; i++) {
            if (life[i] <= 0) continue;
            life[i] -= dt;
            bx[i] += bvx[i] * dt;
            by[i] += bvy[i] * dt;
            bvy[i] += size * 5 * dt;
            rotation[i] += bvx[i] * dt * 0.05f;
            boolean impact = false;
            if (bx[i] < radius || bx[i] > motion.width - radius) {
                bx[i] = Math.max(radius, Math.min(motion.width - radius, bx[i]));
                bvx[i] *= -0.65f;
                impact = true;
            }
            if (by[i] < radius) { by[i] = radius; bvy[i] = Math.abs(bvy[i]) * 0.6f; impact = true; }
            if (by[i] > motion.height - radius) {
                by[i] = motion.height - radius;
                bvy[i] = -Math.abs(bvy[i]) * 0.52f;
                bvx[i] *= 0.8f;
                impact = true;
            }
            if (impact) {
                sfx.play(PetSfxAction.BOTTLE_BOUNCE);
                bounces[i]++;
            }
            if (bounces[i] >= 3 || life[i] <= 0) {
                life[i] = 0;
                word(5, bx[i], by[i]);
                for (int j = 0; j < 7; j++) emit((byte) 1, bx[i], by[i], (random.nextFloat() - 0.5f) * size,
                    -random.nextFloat() * size, 1.1f);
            }
        }
        for (int i = 0; i < PARTICLES; i++) {
            if (remaining[i] <= 0) continue;
            remaining[i] -= dt;
            px[i] += vx[i] * dt;
            py[i] += vy[i] * dt;
            if (kind[i] == 0) px[i] += (float) Math.sin(clock * 3 + i) * dt * 8;
            if (kind[i] == 1) vy[i] += size * 3 * dt;
        }
    }

    public void render(DrawContext context, PetResources.Rig rig, float size) {
        Matrix3x2fStack matrices = context.getMatrices();
        float scale = size / rig.height();
        for (int i = 0; i < BOTTLES; i++) {
            if (life[i] <= 0) continue;
            matrices.pushMatrix();
            matrices.translate(bx[i], by[i]);
            matrices.rotate(rotation[i]);
            matrices.scale(scale, scale);
            context.drawTexture(RenderPipelines.GUI_TEXTURED, rig.bottle(), -Math.round(rig.neckX()), -Math.round(rig.neckY()),
                0, 0, rig.bottleWidth(), rig.bottleHeight(), rig.bottleWidth(), rig.bottleHeight());
            matrices.popMatrix();
        }
        for (int i = 0; i < PARTICLES; i++) {
            if (remaining[i] <= 0) continue;
            int alpha = Math.min(255, (int) (remaining[i] * 110));
            int x = (int) px[i];
            int y = (int) py[i];
            if (kind[i] == 0) {
                int radius = 1 + (int) ((2.7f - remaining[i]) * 2);
                context.fill(x - radius, y - radius, x + radius, y + radius, (alpha << 24) | 0x999999);
            } else if (kind[i] == 1) context.fill(x, y, x + 2, y + 3, (alpha << 24) | 0x7aab78);
            else context.drawTextWithShadow(MeteorClient.mc.textRenderer,
                WORDS[kind[i] - 2], x, y, (alpha << 24) | 0xffefbb);
        }
    }

    private void emit(byte type, float x, float y, float speedX, float speedY, float seconds) {
        int i = nextParticle;
        nextParticle = (nextParticle + 1) % PARTICLES;
        kind[i] = type;
        px[i] = x;
        py[i] = y;
        vx[i] = speedX;
        vy[i] = speedY;
        remaining[i] = seconds;
    }
}
