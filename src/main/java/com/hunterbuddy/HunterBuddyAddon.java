package com.hunterbuddy;


import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;

import com.hunterbuddy.modules.AnvilRename;
import com.hunterbuddy.modules.AutoFarm;
import com.hunterbuddy.modules.AutoPortal;
import com.hunterbuddy.modules.CaveAirESP;
import com.hunterbuddy.modules.ControlFly;
import com.hunterbuddy.modules.ElytraAutoFly;
import com.hunterbuddy.modules.ElytraRecast;
import com.hunterbuddy.modules.FlowESP;
import com.hunterbuddy.modules.ItemsSucker;
import com.hunterbuddy.modules.SandMineAddon;
import com.hunterbuddy.modules.SearchArea;
import com.hunterbuddy.modules.SignRender;
import com.hunterbuddy.modules.ShulkerColor;
import com.hunterbuddy.modules.VanityESP;
import com.hunterbuddy.modules.tradingsystem.ExperienceTraderModule;
import com.hunterbuddy.modules.tradingsystem.ExperienceTraderStarterModule;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HunterBuddyAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category HUNTER_BUDDY_CATEGORY = new Category("HunterBuddy");

    @Override
    public void onInitialize() {
        LOG.info("Initializing HunterBuddy Addon");

        // HunterBuddy modules
        Modules.get().add(new SandMineAddon(HUNTER_BUDDY_CATEGORY));
        Modules.get().add(new ShulkerColor(HUNTER_BUDDY_CATEGORY));
        Modules.get().add(new ItemsSucker(HUNTER_BUDDY_CATEGORY));
        Modules.get().add(new AutoFarm(HUNTER_BUDDY_CATEGORY));
        Modules.get().add(new AnvilRename(HUNTER_BUDDY_CATEGORY));
        Modules.get().add(new ElytraAutoFly());
        Modules.get().add(new ElytraRecast());
        Modules.get().add(new ControlFly());
        Modules.get().add(new AutoPortal());
        Modules.get().add(new SignRender());
        Modules.get().add(new SearchArea());
        Modules.get().add(new FlowESP());
        Modules.get().add(new CaveAirESP());
        Modules.get().add(new VanityESP());
        ExperienceTraderModule module = new ExperienceTraderModule(HUNTER_BUDDY_CATEGORY);
        Modules.get().add(module);
        Modules.get().add(new ExperienceTraderStarterModule(HUNTER_BUDDY_CATEGORY, module));

        // ------------------------------------------------------------------
        // Meteor 0.5.8 / Yarn 1.21.1 workaround:
        //
        // Meteor 0.5.8 (both -SNAPSHOT and release) is compiled against Yarn
        // 1.21.4 mappings. On Yarn 1.21.1 the static field
        //   meteordevelopment.meteorclient.systems.modules.Categories.Movement
        // initialises with a remapped Item reference that doesn't resolve
        // (NoSuchFieldError or similar). As a result ElytraFly and ElytraBoost
        // from Meteor fail to register in Modules.REGISTRY.
        //
        // Meteor's FireworkRocketEntityMixin calls
        //   Modules.get().get(ElytraBoost.class).isFirework(entity)
        // every firework tick. With ElytraBoost unregistered, that returns
        // null and NPEs the entire game the moment any firework rocket exists
        // in the world (player- or server-launched).
        //
        // Fix: forcibly instantiate the two Meteor modules against a fallback
        // Movement category and inject them into Meteor's internal maps.
        // ------------------------------------------------------------------
        ensureMeteorMovementModulesRegistered(ElytraFly.class);
        ensureMeteorMovementModulesRegistered(meteordevelopment.meteorclient.systems.modules.movement.ElytraBoost.class);
    }

    /**
     * If Meteor's ElytraFly or ElytraBoost failed to auto-register (because of
     * the Yarn 1.21.1 vs 1.21.4 mismatch above), instantiate them against the
     * fallback Movement category and inject them into Meteor's internal module
     * registry so {@code Modules.get(klass)} returns a non-null instance.
     */
    private void ensureMeteorMovementModulesRegistered(Class<? extends Module> moduleClass) {
        try {
            Module existing = Modules.get().get(moduleClass);
            if (existing != null) return;

            Category category = getOrCreateMovementCategory();
            if (category == null) {
                LOG.warn("Skipping {} registration: no Movement category available.", moduleClass.getSimpleName());
                return;
            }

            Module instance = moduleClass.getDeclaredConstructor().newInstance();

            // The Meteor Module constructor already calls Modules.get().add(this),
            // but some init paths skip that on Yarn 1.21.1, so we re-inject
            // into the internal maps to be safe.
            Field moduleInstancesField = Modules.class.getDeclaredField("moduleInstances");
            moduleInstancesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Class<? extends Module>, Module> moduleInstances =
                (Map<Class<? extends Module>, Module>) moduleInstancesField.get(Modules.get());
            moduleInstances.put(moduleClass, instance);

            Field groupsField = Modules.class.getDeclaredField("groups");
            groupsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Category, List<Module>> groups =
                (Map<Category, List<Module>>) groupsField.get(Modules.get());
            groups.computeIfAbsent(category, k -> new ArrayList<>()).add(instance);

            Field modulesField = Modules.class.getDeclaredField("modules");
            modulesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Module> modulesList = (List<Module>) modulesField.get(Modules.get());
            modulesList.add(instance);

            LOG.info("HunterBuddy: pre-registered Meteor's {} against fallback Movement category.",
                moduleClass.getSimpleName());
        } catch (Throwable t) {
            LOG.warn("Could not pre-register Meteor {}: {}", moduleClass.getSimpleName(), t.getMessage());
        }
    }

    /**
     * Returns Meteor's {@code Categories.Movement} instance, building a fallback
     * (and patching the static field) if it failed to initialise on Yarn 1.21.1.
     */
    private Category getOrCreateMovementCategory() {
        try {
            Class<?> categoriesClass = Class.forName("meteordevelopment.meteorclient.systems.modules.Categories");
            Field movementField = categoriesClass.getDeclaredField("Movement");
            movementField.setAccessible(true);
            Object existing = movementField.get(null);
            if (existing != null) return (Category) existing;

            // Fallback: build a Movement category with the first static ItemStack
            // we can find on the Items class. The intermediary name "class_1802"
            // is stable across Yarn 1.21.x; we don't depend on the Yarn field names.
            Class<?> itemsClass = Class.forName("net.minecraft.class_1802");
            Class<?> categoryClass = Class.forName("meteordevelopment.meteorclient.systems.modules.Category");

            Object iconStack = null;
            String[] candidateMethods = {"method_7854", "getDefaultStack"};
            outer:
            for (String methodName : candidateMethods) {
                for (Field f : itemsClass.getDeclaredFields()) {
                    if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    Object item = f.get(null);
                    if (item == null) continue;
                    try {
                        Method m = item.getClass().getMethod(methodName);
                        Object stack = m.invoke(item);
                        if (stack != null) {
                            iconStack = stack;
                            break outer;
                        }
                    } catch (Throwable ignored) {}
                }
            }
            if (iconStack == null) {
                LOG.warn("Could not find any Item defaultStack method — Movement category remains null.");
                return null;
            }

            Category fallback = (Category) categoryClass
                .getConstructor(String.class, iconStack.getClass())
                .newInstance("Movement", iconStack);
            movementField.set(null, fallback);

            // Push into Modules.CATEGORIES so other code can find it.
            try {
                Field categoriesListField = Modules.class.getDeclaredField("CATEGORIES");
                categoriesListField.setAccessible(true);
                @SuppressWarnings("unchecked")
                List<Category> categories = (List<Category>) categoriesListField.get(null);
                if (!categories.contains(fallback)) categories.add(fallback);
            } catch (Throwable ignored) {}

            LOG.info("HunterBuddy: patched Categories.Movement (was null on Yarn 1.21.1) with a fallback category.");
            return fallback;
        } catch (Throwable t) {
            LOG.warn("Failed to obtain/create Movement category: {}", t.getMessage());
            return null;
        }
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(HUNTER_BUDDY_CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.hunterbuddy";
    }
}