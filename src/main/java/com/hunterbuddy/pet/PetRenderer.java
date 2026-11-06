package com.hunterbuddy.pet;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;

public final class PetRenderer {
    public float anchorX;
    public float anchorY;

    private final PetSkeleton skeleton = new PetSkeleton();
    private final PetImageTransform image = new PetImageTransform();

    public void prepare(PetResources.Rig rig, PetMotion motion, PetPose pose, float size, float delta) {
        skeleton.update(rig, motion, pose, size, delta);
    }

    public void anchor(PetResources.Rig rig, int anchor) {
        skeleton.anchor(rig, anchor);
        anchorX = skeleton.anchorX;
        anchorY = skeleton.anchorY;
    }

    public void eyes(PetResources.Rig rig, PetMotion motion, PetFullPose poses, float size, float delta) {
        anchor(rig, 3);
        float blend = poses.previousBlend + (poses.blend - poses.previousBlend) * delta;
        if (poses.current == null || blend == 0) return;
        float x = motion.previousX + (motion.x - motion.previousX) * delta;
        float y = motion.previousY + (motion.y - motion.previousY) * delta;
        float mix = poses.previousMix + (poses.mix - poses.previousMix) * delta;
        float eyeX = anchorX * (1 - blend);
        float eyeY = anchorY * (1 - blend);
        image.update(poses.current, rig.height(), size, poses.age, motion.direction, blend, x, y);
        eyeX += image.eyesX * blend * (1 - mix);
        eyeY += image.eyesY * blend * (1 - mix);
        if (poses.alternate != null && mix > 0) {
            image.update(poses.alternate, rig.height(), size, poses.age, motion.direction, blend, x, y);
            eyeX += image.eyesX * blend * mix;
            eyeY += image.eyesY * blend * mix;
        }
        anchorX = eyeX;
        anchorY = eyeY;
    }

    public void render(DrawContext context, PetResources.Rig rig, PetMotion motion, PetPose pose,
                       PetEffects effects, PetDialogue dialogue, PetFullPose fullPose, float size, float delta) {
        Matrix3x2fStack matrices = context.getMatrices();
        float x = motion.previousX + (motion.x - motion.previousX) * delta;
        float y = motion.previousY + (motion.y - motion.previousY) * delta;
        float blend = fullPose.previousBlend + (fullPose.blend - fullPose.previousBlend) * delta;
        prepare(rig, motion, pose, size, delta);
        if (blend < 1) puppet(context, rig, motion.direction, 1 - blend);
        float mix = fullPose.previousMix + (fullPose.mix - fullPose.previousMix) * delta;
        if (fullPose.current != null && blend > 0) whole(context, rig, fullPose.current, fullPose.age, motion.direction, size, blend, blend * (1 - mix), x, y);
        if (fullPose.alternate != null && mix > 0 && blend > 0) whole(context, rig, fullPose.alternate, fullPose.age, motion.direction, size, blend, blend * mix, x, y);
        effects.render(context, rig, size);
        PetResources.Line line = dialogue.current;
        if (line == null) return;
        int bubbleWidth = line.width + 12;
        int bubbleHeight = line.text.length * 10 + 10;
        float textScale = Math.min(1, (motion.width - 8f) / bubbleWidth);
        eyes(rig, motion, fullPose, size, delta);
        int left = Math.round(Math.max(4, Math.min(motion.width - bubbleWidth * textScale - 4, anchorX - bubbleWidth * textScale / 2)));
        int top = Math.round(Math.max(4, anchorY - bubbleHeight * textScale - 12));
        matrices.pushMatrix();
        matrices.translate(left, top);
        matrices.scale(textScale, textScale);
        context.fill(0, 0, bubbleWidth, bubbleHeight, 0xe81b1c24);
        for (int i = 0; i < line.text.length; i++) context.drawText(MeteorClient.mc.textRenderer, line.text[i], 6, 5 + i * 10, 0xfff8f4e8, false);
        matrices.popMatrix();
    }

    private void whole(DrawContext context, PetResources.Rig rig, PetResources.FullPose pose, float age, int direction,
                       float size, float breathing, float alpha, float x, float y) {
        Matrix3x2fStack matrices = context.getMatrices();
        image.update(pose, rig.height(), size, age, direction, breathing, x, y);
        matrices.pushMatrix();
        matrices.mul(image.matrix);
        if (direction < 0) matrices.scale(-1, 1);
        PetImages.draw(context, pose.texture(), 0, 0, pose.width(), pose.height(), direction, ((int) (alpha * 255) << 24) | 0xffffff);
        float sparkle = Math.max(0, (float) Math.sin(age * 4));
        if (pose.name().equals("moon") && sparkle > 0.65f) {
            int sx = direction * Math.round(pose.shineX());
            int sy = Math.round(pose.shineY());
            int radius = Math.round(5 + sparkle * 9);
            int color = ((int) (alpha * 255) << 24) | 0xffffdf;
            context.fill(sx - radius, sy - 2, sx + radius, sy + 2, color);
            context.fill(sx - 2, sy - radius, sx + 2, sy + radius, color);
        }
        matrices.popMatrix();
    }

    private void puppet(DrawContext context, PetResources.Rig rig, int direction, float alpha) {
        for (int layer = 0; layer < 6; layer++) {
            for (PetResources.Part part : rig.parts()) {
                if (part.index() == PetSkeleton.layer(layer)) part(context, part, direction, alpha);
            }
        }
    }

    private void part(DrawContext context, PetResources.Part part, int direction, float alpha) {
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.mul(skeleton.matrices[part.index()]);
        if (direction < 0) matrices.scale(-1, 1);
        PetImages.draw(context, part.texture(), Math.round(part.x()), Math.round(part.y()), part.width(), part.height(),
            direction, ((int) (alpha * 255) << 24) | 0xffffff);
        matrices.popMatrix();
    }
}
