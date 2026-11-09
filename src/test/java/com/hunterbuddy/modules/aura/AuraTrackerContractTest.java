package com.hunterbuddy.modules.aura;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import static org.junit.jupiter.api.Assertions.*;

final class AuraTrackerContractTest {
    @Test
    void minecraftAppliesTrackedHealthOnTheMainThreadBeforeTheHook() throws Exception {
        Set<String> calls = inspect("net/minecraft/client/network/ClientPlayNetworkHandler", "onEntityTrackerUpdate", false);
        assertTrue(calls.contains("net/minecraft/network/NetworkThreadUtils.forceMainThread"));
        assertTrue(calls.contains("net/minecraft/entity/data/DataTracker.writeUpdatedEntries"));
        assertTrue(inspect("net/minecraft/client/network/ClientPlayNetworkHandler", "onEntityAnimation", false)
            .contains("net/minecraft/network/NetworkThreadUtils.forceMainThread"));
        assertEquals(4, net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket.CRIT);
    }

    @Test
    void auraRenderAndProjectionDoNotAllocatePerEntity() throws Exception {
        inspect("com/hunterbuddy/modules/aura/AuraVitalsPainter", "render", true);
        inspect("com/hunterbuddy/modules/aura/AuraVitalsPainter", "project", true);
        inspect("com/hunterbuddy/modules/aura/AuraVitalsPainter", "bar", true);
        inspect("com/hunterbuddy/modules/aura/AuraNameplatePainter", "draw", true);
        inspect("com/hunterbuddy/modules/aura/AuraNameplatePainter", "text", true);
    }

    private static Set<String> inspect(String owner, String method, boolean noAllocations) throws IOException {
        Set<String> calls = new HashSet<>();
        try (var stream = AuraTrackerContractTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    if (!method.equals(name)) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitTypeInsn(int opcode, String type) {
                            if (noAllocations) assertNotEquals(Opcodes.NEW, opcode, method + ": " + type);
                            if (noAllocations) assertNotEquals(Opcodes.ANEWARRAY, opcode, method + ": " + type);
                        }
                        @Override
                        public void visitIntInsn(int opcode, int operand) {
                            if (noAllocations) assertNotEquals(Opcodes.NEWARRAY, opcode, method);
                        }
                        @Override
                        public void visitMethodInsn(int opcode, String type, String name, String desc, boolean itf) {
                            calls.add(type + "." + name);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return calls;
    }
}
