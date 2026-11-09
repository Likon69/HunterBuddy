package com.hunterbuddy.hud.toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.*;

final class ModuleToastInputTest {
    private static void toggle(Object module, List<Boolean> results, Runnable callback) {
        ModuleToastInput input = ModuleToastInput.current();
        int previous = input.enter(module);
        try {
            results.add(input.accepts());
            if (callback != null) callback.run();
        } finally {
            input.leave(previous);
        }
    }

    @Test
    void directPlayerToggleIsAcceptedButItsAutomaticChildrenAreNot() {
        Object playerModule = new Object();
        Object flightModule = new Object();
        List<Boolean> results = new ArrayList<>();
        ModuleToastInput.perform(playerModule, module -> toggle(module, results, () -> {
            toggle(flightModule, results, null);
            toggle(playerModule, results, null);
        }));
        assertEquals(List.of(true, false, false), results);
        assertFalse(ModuleToastInput.current().accepts());
    }

    @Test
    void automaticWaypointStyleChangesAreNeverAcceptedEvenJustAfterAPlayerAction() {
        Object module = new Object();
        List<Boolean> results = new ArrayList<>();
        ModuleToastInput.perform(module, target -> toggle(target, results, null));
        for (int i = 0; i < 111; i++) toggle(module, results, null);
        assertTrue(results.getFirst());
        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
    }

    @Test
    void unusedWrongTargetAndExceptionalMarkersCannotLeak() {
        Object module = new Object();
        List<Boolean> results = new ArrayList<>();
        ModuleToastInput.perform(module, target -> { });
        toggle(module, results, null);
        ModuleToastInput.perform(module, target -> toggle(new Object(), results, () -> toggle(module, results, null)));
        assertThrows(IllegalStateException.class, () -> ModuleToastInput.perform(module,
            target -> toggle(target, results, () -> { throw new IllegalStateException(); })));
        toggle(module, results, null);
        assertEquals(List.of(false, false, false, true, false), results);
        assertFalse(ModuleToastInput.current().accepts());
    }

    @Test
    void markerIsThreadLocal() throws InterruptedException {
        Object module = new Object();
        AtomicBoolean otherAccepted = new AtomicBoolean(true);
        ModuleToastInput.perform(module, target -> {
            Thread other = new Thread(() -> {
                ModuleToastInput input = ModuleToastInput.current();
                int previous = input.enter(target);
                try {
                    otherAccepted.set(input.accepts());
                } finally {
                    input.leave(previous);
                }
            });
            other.start();
            try {
                other.join();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        });
        assertFalse(otherAccepted.get());
    }

    @Test
    void actualDependencyBytecodeContainsEveryInstrumentedEntryPoint() throws IOException {
        String root = "meteordevelopment/meteorclient/";
        String module = root + "systems/modules/Module";
        assertEquals(1, calls(root + "systems/modules/Modules", "onAction", module, "toggle"));
        assertEquals(1, calls(root + "gui/themes/meteor/widgets/WMeteorModule", "onPressed", module, "toggle"));
        assertEquals(1, calls(root + "gui/screens/ModuleScreen", "*", module, "toggle"));
        assertEquals(1, calls("com/hunterbuddy/gui/theme/widgets/HbModule", "onPressed", module, "toggle"));
        assertEquals(1, calls("com/hunterbuddy/gui/theme/widgets/HbModuleSettings", "*", module, "toggle"));
        String command = root + "commands/commands/ToggleCommand";
        assertEquals(1, calls(command, "*", module, "toggle"));
        assertEquals(1, calls(command, "*", module, "enable"));
        assertEquals(1, calls(command, "*", module, "disable"));
        assertEquals(2, calls(command, "*", "java/util/ArrayList", "forEach"));
        assertEquals(1, calls(module, "toggle", root + "systems/modules/Modules", "addActive"));
        assertEquals(1, calls(module, "toggle", root + "systems/modules/Modules", "removeActive"));
    }

    private static int calls(String type, String method, String owner, String name) throws IOException {
        int[] count = {0};
        try (var stream = ModuleToastInputTest.class.getClassLoader().getResourceAsStream(type + ".class")) {
            assertNotNull(stream, type);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String candidate, String descriptor, String signature, String[] exceptions) {
                    if (!method.equals("*") && !method.equals(candidate)) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String invokedOwner, String invokedName, String invokedDescriptor, boolean isInterface) {
                            if (owner.equals(invokedOwner) && name.equals(invokedName)) count[0]++;
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return count[0];
    }
}
