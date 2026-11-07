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
        image.update(poses.current, rig.height(), size, poses.age(delta), motion.direction, blend, x, y);
        eyeX += image.eyesX * blend * (1 - mix);
        eyeY += image.eyesY * blend * (1 - mix);
        if (poses.alternate != null && mix > 0) {
            image.update(poses.alternate, rig.height(), size, poses.age(delta), motion.direction, blend, x, y);
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
        float squash = fullPose.previousSquash + (fullPose.squash - fullPose.previousSquash) * delta;
        boolean showImage = fullPose.current != null && blend >= squash && (blend > 0 || squash == 0);
        if (!showImage && squash > 0) {
            matrices.pushMatrix();
            squash(matrices, x, squash);
            puppet(context, rig, motion, 1);
            matrices.popMatrix();
        }
        float age = fullPose.age(delta);
        float mix = fullPose.previousMix + (fullPose.mix - fullPose.previousMix) * delta;
        if (showImage) whole(context, rig, fullPose.current, age, motion.direction, size, blend, 1 - mix, x, y);
        if (showImage && fullPose.alternate != null && mix > 0) whole(context, rig, fullPose.alternate, age, motion.direction, size, blend, mix, x, y);
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
        squash(matrices, x, breathing);
        matrices.mul(image.matrix);
        if (direction < 0) matrices.scale(-1, 1);
        PetImages.draw(context, pose.texture(), 0, 0, pose.width(), pose.height(), direction, ((int) (alpha * 255) << 24) | 0xffffff);
        matrices.popMatrix();
    }

    private static void squash(Matrix3x2fStack matrices, float x, float width) {
        matrices.translate(x, 0);
        matrices.scale(Math.max(0.02f, width), 1);
        matrices.translate(-x, 0);
    }

    private void puppet(DrawContext context, PetResources.Rig rig, PetMotion motion, float alpha) {
        if (!motion.hiding) {
            parts(context, rig, motion.direction, alpha);
            return;
        }
        context.enableScissor(0, 0, motion.width, Math.max(0, Math.round(motion.hideTop)));
        parts(context, rig, motion.direction, alpha);
        context.disableScissor();
    }

    private void parts(DrawContext context, PetResources.Rig rig, int direction, float alpha) {
        for (PetResources.Part part : rig.parts()) part(context, part, direction, alpha);
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
