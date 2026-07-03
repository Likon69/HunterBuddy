package com.hunterbuddy;


import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;

import com.hunterbuddy.modules.AireForce;
import com.hunterbuddy.modules.AnvilRename;
import com.hunterbuddy.modules.AutoFarm;
import com.hunterbuddy.modules.ElytraAutoFly;
import com.hunterbuddy.modules.ElytraBoost;
import com.hunterbuddy.modules.ItemsSucker;
import com.hunterbuddy.modules.SandMineAddon;
import com.hunterbuddy.modules.ShulkerColor;
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

        // Modules
        Modules.get().add(new AireForce(HUNTER_BUDDY_CATEGORY));
        Modules.get().add(new SandMineAddon(HUNTER_BUDDY_CATEGORY));
        Modules.get().add(new ShulkerColor(HUNTER_BUDDY_CATEGORY));
        Modules.get().add(new ItemsSucker (HUNTER_BUDDY_CATEGORY));
        Modules.get().add(new AutoFarm (HUNTER_BUDDY_CATEGORY));
        Modules.get().add(new AnvilRename(HUNTER_BUDDY_CATEGORY));
        Modules.get().add(new ElytraAutoFly(HUNTER_BUDDY_CATEGORY));
        Modules.get().add(new ElytraBoost(HUNTER_BUDDY_CATEGORY));
        ExperienceTraderModule module = new ExperienceTraderModule(HUNTER_BUDDY_CATEGORY);
        Modules.get().add(module);
        Modules.get().add(new ExperienceTraderStarterModule(HUNTER_BUDDY_CATEGORY, module));

        // ----------------------------------------------------------------------
        // Meteor 0.5.8 / MC 1.21.1 workaround:
        //
        // Meteor 0.5.8 was compiled against Yarn 1.21.4 mappings. The static field
        // Categories.Movement is initialised with Items.class.field_8285 which maps
        // to a different Item on Yarn 1.21.1, causing the static init to fail
        // (NoSuchFieldError or similar). As a result, ElytraFly AND ElytraBoost from
        // Meteor fail to register in Modules.REGISTRY.
        //
        // Meteor's FireworkRocketEntityMixin calls
        //   Modules.get().get(ElytraBoost.class).isFirework(entity)
        // every firework tick. With ElytraBoost unregistered, this returns null and
        // NPEs the entire game the moment a firework rocket exists in the world
        // (player- or server-launched).
        //
        // Fix: forcibly instantiate these modules and inject them into Meteor's
        // internal module map. If Categories.Movement is null (the bug), we replace
        // it with a fallback category built from any static Item field we can find
        // via reflection.
        // ----------------------------------------------------------------------
        ensureMeteorMovementModulesRegistered(ElytraFly.class);
        ensureMeteorMovementModulesRegistered(meteordevelopment.meteorclient.systems.modules.movement.ElytraBoost.class);
    }

    /**
     * If Meteor's {@code ElytraFly} or {@code ElytraBoost} failed to auto-register
     * (because of the Categories.Movement bug described above), instantiate them and
     * inject them into Meteor's internal {@code Modules} registry so subsequent lookups
     * via {@code Modules.get(klass)} return a non-null instance.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void ensureMeteorMovementModulesRegistered(Class<? extends Module> moduleClass) {
        try {
            Module existing = Modules.get().get((Class<? extends Module>) moduleClass);
            if (existing != null) return;

            Module instance = moduleClass.getDeclaredConstructor().newInstance();

            Category category = getOrCreateMovementCategory();
            if (category == null) {
                LOG.warn("Skipping {} registration: no Movement category available.", moduleClass.getSimpleName());
                return;
            }

            // Inject into Meteor's internal module registry.
            Field moduleInstancesField = Modules.class.getDeclaredField("moduleInstances");
            moduleInstancesField.setAccessible(true);
            Map<Class<? extends Module>, Module> moduleInstances =
                (Map<Class<? extends Module>, Module>) moduleInstancesField.get(Modules.get());
            moduleInstances.put(moduleClass, instance);

            Field groupsField = Modules.class.getDeclaredField("groups");
            groupsField.setAccessible(true);
            Map<Category, List<Module>> groups = (Map<Category, List<Module>>) groupsField.get(Modules.get());
            groups.computeIfAbsent(category, k -> new ArrayList<>()).add(instance);

            Field modulesField = Modules.class.getDeclaredField("modules");
            modulesField.setAccessible(true);
            List<Module> modulesList = (List<Module>) modulesField.get(Modules.get());
            modulesList.add(instance);

            LOG.info("HunterBuddy: pre-registered Meteor's {} to bypass 0.5.8/1.21.1 Categories.Movement bug.",
                moduleClass.getSimpleName());
        } catch (Throwable t) {
            LOG.warn("Could not pre-register Meteor {}: {}", moduleClass.getSimpleName(), t.getMessage());
        }
    }

    /**
     * Returns Meteor's {@code Categories.Movement} instance, building a fallback
     * (and patching the static field) if it failed to initialise on Yarn 1.21.1.
     */
    @SuppressWarnings("unchecked")
    private Category getOrCreateMovementCategory() {
        try {
            Class<?> categoriesClass = Class.forName("meteordevelopment.meteorclient.systems.modules.Categories");
            Field movementField = categoriesClass.getDeclaredField("Movement");
            movementField.setAccessible(true);
            Object existing = movementField.get(null);
            if (existing != null) return (Category) existing;

            // Fallback: build a Movement category with any ItemStack as the icon.
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

            // Also push it into Modules.CATEGORIES so other code can find it.
            try {
                Field categoriesListField = Modules.class.getDeclaredField("CATEGORIES");
                categoriesListField.setAccessible(true);
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
