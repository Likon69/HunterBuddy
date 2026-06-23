package org.linytech.trader.modules.tradingsystem;

import baritone.api.BaritoneAPI;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.linytech.trader.modules.tradingsystem.stages.*;
import org.linytech.trader.modules.tradingsystem.utilites.RenderUtils;
import org.linytech.trader.modules.tradingsystem.utilites.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@SuppressWarnings({"unused", "BooleanMethodIsAlwaysInverted"})
public class ExperienceTraderModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPosition = settings.createGroup("Position");
    private final SettingGroup sgTimings = settings.createGroup("Timings");
    private final SettingGroup sgCleaners = settings.createGroup("Cleaners");

    public final List<Integer> blocked = new ArrayList<>();

    private @Nullable VillagerEntity target;

    public @Nullable VillagerEntity getTarget() {
        return target;
    }

    public void setTarget(@Nullable VillagerEntity target) {
        this.target = target;
    }

    private boolean isTimeToClear = false;
    public boolean isTimeToClear() {
        return this.isTimeToClear;
    }
    public void setTimeToClear(boolean timeToClear) {
        this.isTimeToClear = timeToClear;
    }

    public ExperienceTraderModule(Category category) {
        super(category, "Villager Trading", "I'll take it for a hundred!");
        init();
    }

    public final Setting<Integer> maxPrice = sgGeneral.add(
        new IntSetting.Builder()
            .name("experience-max-price")
            .description("The maximum for which the mod will buy experience.")
            .min(1)
            .max(64)
            .sliderMin(1)
            .sliderMax(64)
            .defaultValue(1)
            .build()
    );

    public final Setting<Integer> searchingRadius = sgGeneral.add(
        new IntSetting.Builder()
            .name("villagers-searching-radius")
            .description("At what distance to look for the inhabitants around the player.")
            .min(32)
            .max(128)
            .sliderMin(32)
            .sliderMax(128)
            .defaultValue(64)
            .build()
    );

    public final Setting<Double> baritoneStopDistance = sgGeneral.add(
        new DoubleSetting.Builder()
            .name("stop-distance-for-baritone")
            .description("Minimum distance to target(chests and inhabitants).")
            .min(0.5)
            .max(5)
            .sliderMin(0.5)
            .sliderMax(5)
            .defaultValue(0.75)
            .build()
    );

    public final Setting<Integer> minEmeraldsCount = sgGeneral.add(
        new IntSetting.Builder()
            .name("min-emeralds-count")
            .description("Minimum number of emeralds to replenish your inventory.")
            .min(0)
            .max(128)
            .sliderMin(0)
            .sliderMax(128)
            .defaultValue(25)
            .build()
    );

    public final Setting<Boolean> doRenderHandling = sgGeneral.add(
        new BoolSetting.Builder()
            .name("Highlight accepted villagers")
            .defaultValue(false)
            .build()
    );

    public final Setting<Integer> maxServerTimeout = sgTimings.add(
        new IntSetting.Builder()
            .name("max-server-time-out")
            .description("Maximum time to wait for a response from the server in seconds (e.g. opening an inventory or a trade).")
            .min(1)
            .max(10)
            .sliderMin(1)
            .sliderMax(10)
            .defaultValue(1)
            .build()
    );

    public final Setting<Integer> stealAndDumpDelay = sgTimings.add(
        new IntSetting.Builder()
            .name("steal-and-dump-delay")
            .description("Minimal delay for stealing/dumping emeralds/experience bottles in chest (!!!MS!!!)")
            .min(1)
            .max(5000)
            .sliderMin(1)
            .sliderMax(5000)
            .defaultValue(100)
            .build()
    );

    public final Setting<BlockPos> forwardingBackPosition = sgPosition.add(
        new BlockPosSetting.Builder()
            .name("forwarding-position")
            .description("AI want to go home.")
            .defaultValue(BlockPos.ORIGIN)
            .build()
    );

    public final Setting<BlockPos> emeraldChestPosition = sgPosition.add(
        new BlockPosSetting.Builder()
            .name("emeralds-chest-position")
            .description("The position of the chest where the AI will replenish the inventory with emeralds.")
            .defaultValue(BlockPos.ORIGIN)
            .build()
    );

    public final Setting<BlockPos> experienceChestPosition = sgPosition.add(
        new BlockPosSetting.Builder()
            .name("experience-chest-position")
            .description("The position of the chest where the AI will drop experience bottles.")
            .defaultValue(BlockPos.ORIGIN)
            .build()
    );

    public final Setting<BlockPos> lookAtOffset = sgPosition.add(
        new BlockPosSetting.Builder()
            .name("look-at-offset")
            .description("Additional offset to lockAt to villagers")
            .defaultValue(BlockPos.ORIGIN)
            .build()
    );

    private final Setting<Boolean> clearAllVillagersData = sgCleaners.add(
        new BoolSetting.Builder()
            .name("clear-villagers-data")
            .description("Clear the data on passed residents from the past cycle. Triggers when clicked.")
            .defaultValue(false)
            .onChanged(val -> {
                try {
                    blocked.clear();
                    info("Данные о пройденных жителях очищена!");
                } catch (Throwable ignored) {
                    error("Ошибка при сбрасывании данных о жителях!");
                }
            })
            .build()
    );

    private final Setting<Boolean> clearAllData = sgCleaners.add(
        new BoolSetting.Builder()
            .name("clear-mod-cache")
            .description("Clear mod cache data (Useful when the mod is hung, so as not to restart the client). Triggers when clicked.")
            .defaultValue(false)
            .onChanged(val -> {
                try {
                    resetData();
                    info("Все данные очищены!");
                } catch (Throwable ignored) {
                    error("Ошибка при сбрасывании кеша!");
                }
            })
            .build()
    );

    private AutoEat autoEat;

    public boolean doOutput() {
        return super.chatFeedback;
    }

    private final List<Stage> stages = new ArrayList<>();

    public void init() {
        stages.add(new EmptyStage(this));
        stages.add(new ValidateStage(this));
        stages.add(new EmeraldCheckStage(this));
        stages.add(new SprintToChest(this, SprintToChest.NeededChest.EXPERIENCE)); //a pass if you don't need it
        stages.add(new StealOrDumpStage(this, StealOrDumpStage.Type.DUMP)); //a pass if you don't need it
        stages.add(new SprintToChest(this, SprintToChest.NeededChest.EMERALDS)); //a pass if you don't need it
        stages.add(new StealOrDumpStage(this, StealOrDumpStage.Type.STEAL)); //a pass if you don't need it
        stages.add(new CraftStage(this)); //a pass if you don't need it
        stages.add(new SprintStage(this));
        stages.add(new OpenTradeMenu(this));
        stages.add(new BuyingStage(this));

        //perform current data
        resetData();
    }

    @SuppressWarnings("unchecked")
    public <C> C getStage(Class<C> clazz) {
        Optional<?> stage = stages.parallelStream()
            .filter(stage0 -> stage0.getClass().equals(clazz))
            .findAny();

        if (stage.isEmpty())
            throw new RuntimeException("Stage %s not found!".formatted(clazz));

        return (C) stage.get();
    }

    @Override
    public void onActivate() {
        current_index = 1;

        this.autoEat = Modules.get().get(AutoEat.class);
    }

    @Override
    public void onDeactivate() {
        this.stages.get(0).reset();
        Stage.getGoalProcess().setGoalAndPath(null);
        current_index = 0;
    }

    private int waitTicks = 0;
    public void addWaitTicks(int count) {
        this.waitTicks += count;
    }

    private int current_index = 0;
    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (this.autoEat != null) {
            if (this.autoEat.eating) return;
        }

        if (this.waitTicks > 0) {
            this.waitTicks--;
            return;
        }

        try {
            if (this.stages.get(current_index).work()) {
                this.stages.get(current_index).reset();
                current_index += 1;
            }

            if (this.stages.size() <= current_index)
                resetData();
        } catch (Throwable err) {
            error("Ошибка при выполнении одной из стадий: " + err.getMessage());
            err.printStackTrace(System.err);
            super.toggle();
        }
    }

    @EventHandler
    public void on3DRender(Render3DEvent event) {
        if (this.doRenderHandling.get()) {
            RenderUtils.renderBoundingBox(
                event,
                Objects.requireNonNull(mc.world),
                this.blocked
            );
        }
    }

    public void nextRound(String reason) {
        if (doOutput()) {
            warning("Житель пропущен по причине: " + reason);
        }

        resetData();
    }

    public void resetData() {
        try {
            current_index = 1;

            if (getTarget() != null) {
                this.blocked.add(Objects.requireNonNull(getTarget()).getId());
                setTarget(null);
            }

            setTimeToClear(false);

            if (MinecraftClient.getInstance().currentScreen != null)
                MinecraftClient.getInstance().currentScreen.close();
        } catch (Throwable ignored) {}

        stages.parallelStream().forEach(stage -> {
            try {
                stage.reset();
            } catch (Throwable ignored) {}
        });
    }

    @EventHandler
    public void onRender(Render2DEvent event) {
        if (doOutput()) {
            TextRenderer.get().render(
                this.stages.get(current_index).toShortString(),
                10, 10,
                Color.WHITE
            );

            if (MinecraftClient.getInstance().currentScreen != null)
                TextRenderer.get().render(
                    MinecraftClient.getInstance().currentScreen.getClass().getSimpleName(),
                    10, 30,
                    Color.WHITE
                );
        }
    }
}
