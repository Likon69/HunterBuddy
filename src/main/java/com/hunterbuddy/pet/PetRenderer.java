package com.hunterbuddy.pet;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import org.joml.Matrix3x2fStack;

public final class PetRenderer {
    public float anchorX;
    public float anchorY;

    public void anchor(PetResources.Rig rig, PetMotion motion, PetPose pose, float size, int anchor, int owner) {
        float x = rig.anchors()[anchor * 2];
        float y = rig.anchors()[anchor * 2 + 1];
        for (PetResources.Part part : rig.parts()) {
            if (part.index() != owner) continue;
            float sin = (float) Math.sin(pose.angles[owner]);
            float cos = (float) Math.cos(pose.angles[owner]);
            float dx = x - part.pivotX();
            float dy = y - part.pivotY();
            x = part.pivotX() + dx * cos - dy * sin;
            y = part.pivotY() + dx * sin + dy * cos;
            break;
        }
        float dx = x - rig.anchors()[8];
        float dy = y - rig.anchors()[9];
        float sin = (float) Math.sin(pose.lean);
        float cos = (float) Math.cos(pose.lean);
        float scale = size / rig.height();
        anchorX = motion.x + motion.direction * scale * (dx * cos - dy * sin);
        anchorY = motion.y + pose.bob * scale + size * pose.crouch + scale * (dx * sin + dy * cos);
    }

    public void render(DrawContext context, PetResources.Rig rig, PetMotion motion, PetPose pose, PetBrain brain,
                       PetEffects effects, PetDialogue dialogue, float size, float delta, float guiScale) {
        Matrix3x2fStack matrices = context.getMatrices();
        float x = motion.previousX + (motion.x - motion.previousX) * delta;
        float y = motion.previousY + (motion.y - motion.previousY) * delta;
        if (brain.state == PetState.HIDE) {
            int left = Math.round(motion.width * 0.5f - 92 * guiScale);
            int right = Math.round(motion.width * 0.5f + 92 * guiScale);
            int top = Math.max(0, Math.round(motion.height - 24 * guiScale));
            context.enableScissor(0, 0, motion.width, top);
            puppet(context, rig, motion, pose, size, delta, x, y);
            context.disableScissor();
            context.enableScissor(x < motion.width * 0.5f ? 0 : right, top,
                x < motion.width * 0.5f ? Math.max(0, left) : motion.width, motion.height);
            puppet(context, rig, motion, pose, size, delta, x, y);
            context.disableScissor();
        } else puppet(context, rig, motion, pose, size, delta, x, y);
        effects.render(context, rig, size);
        PetResources.Line line = dialogue.current;
        if (line == null) return;
        int bubbleWidth = line.width + 12;
        int bubbleHeight = line.text.length * 10 + 10;
        float textScale = Math.min(1, (motion.width - 8f) / bubbleWidth);
        anchor(rig, motion, pose, size, 3, 0);
        int left = Math.round(Math.max(4, Math.min(motion.width - bubbleWidth * textScale - 4, anchorX - bubbleWidth * textScale / 2)));
        int top = Math.round(Math.max(4, Math.min(y - size, anchorY - size * 0.2f) - bubbleHeight * textScale - 12));
        matrices.pushMatrix();
        matrices.translate(left, top);
        matrices.scale(textScale, textScale);
        context.fill(0, 0, bubbleWidth, bubbleHeight, 0xe81b1c24);
        for (int i = 0; i < line.text.length; i++) context.drawText(MeteorClient.mc.textRenderer, line.text[i], 6, 5 + i * 10, 0xfff8f4e8, false);
        matrices.popMatrix();
    }

    private static void puppet(DrawContext context, PetResources.Rig rig, PetMotion motion, PetPose pose,
                               float size, float delta, float x, float y) {
        Matrix3x2fStack matrices = context.getMatrices();
        float scale = size / rig.height();
        float bob = pose.previousBob + (pose.bob - pose.previousBob) * delta;
        float lean = pose.previousLean + (pose.lean - pose.previousLean) * delta;
        float crouch = pose.previousCrouch + (pose.crouch - pose.previousCrouch) * delta;
        matrices.pushMatrix();
        matrices.translate(x, y + bob * scale + size * crouch);
        matrices.scale(motion.direction * scale, scale);
        matrices.rotate(lean);
        matrices.translate(-rig.anchors()[8], -rig.anchors()[9]);
        for (PetResources.Part part : rig.parts()) {
            matrices.pushMatrix();
            matrices.translate(part.pivotX(), part.pivotY());
            int index = part.index();
            matrices.rotate(pose.previousAngles[index] + (pose.angles[index] - pose.previousAngles[index]) * delta);
            context.drawTexture(RenderPipelines.GUI_TEXTURED, part.texture(), Math.round(part.x() - part.pivotX()),
                Math.round(part.y() - part.pivotY()), 0, 0, part.width(), part.height(), part.width(), part.height());
            matrices.popMatrix();
        }
        matrices.popMatrix();
    }
}
