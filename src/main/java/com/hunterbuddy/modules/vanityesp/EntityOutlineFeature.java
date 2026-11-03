package com.hunterbuddy.modules.vanityesp;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;

final class EntityOutlineFeature implements VanityFeature {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Setting<Boolean> toggle;
    private final FeatureStyle style;
    private final EntityRule rule;

    EntityOutlineFeature(Setting<Boolean> toggle, FeatureStyle style, EntityRule rule) {
        this.toggle = toggle;
        this.style = style;
        this.rule = rule;
    }

    @Override
    public void render(Render3DEvent event) {
        if (!toggle.get() || !style.drawsShapes() || mc.world == null) return;
        for (Entity entity : mc.world.getEntities()) {
            if (rule.matches().test(entity)) {
                outline(event, entity);
            }
        }
    }

    private void outline(Render3DEvent event, Entity entity) {
        Box box = rule.boxOf().apply(entity);
        Outline.box(event, box, style);
        Outline.tracer(event, rule.tracerTarget().apply(entity), style);
    }
}
