package com.hunterbuddy.modules.scaffold;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import static org.junit.jupiter.api.Assertions.*;

final class ScaffoldPredictionContractTest {
    @Test
    void actualMinecraftSequencedInteractionRegistersPredictionAndReconcilesServerResponses() throws Exception {
        var sequence = calls("net/minecraft/client/network/ClientPlayerInteractionManager", "sendSequencedPacket");
        assertTrue(sequence.contains("net/minecraft/client/network/PendingUpdateManager.incrementSequence"));
        assertTrue(sequence.contains("net/minecraft/client/network/PendingUpdateManager.getSequence"));
        assertTrue(sequence.contains("net/minecraft/client/network/PendingUpdateManager.close"));
        var state = calls("net/minecraft/client/world/ClientWorld", "setBlockState");
        assertTrue(state.contains("net/minecraft/client/network/PendingUpdateManager.hasPendingSequence"));
        assertTrue(state.contains("net/minecraft/client/network/PendingUpdateManager.addPendingUpdate"));
        var update = calls("net/minecraft/client/world/ClientWorld", "handleBlockUpdate");
        assertTrue(update.contains("net/minecraft/client/network/PendingUpdateManager.hasPendingUpdate"));
        var ack = calls("net/minecraft/client/world/ClientWorld", "handlePlayerActionResponse");
        assertTrue(ack.contains("net/minecraft/client/network/PendingUpdateManager.processPendingUpdates"));
        var rollback = calls("net/minecraft/client/network/PendingUpdateManager", "processPendingUpdates");
        assertTrue(rollback.contains("net/minecraft/client/world/ClientWorld.processPendingUpdate"));
    }

    @Test
    void scaffoldAndRawPlacementNeverConstructExtraMovementPackets() throws Exception {
        for (String type : new String[] {"com/hunterbuddy/modules/scaffold/ScaffoldPlacement",
            "com/hunterbuddy/modules/regear/util/PlacementUtils"}) {
            for (String call : calls(type, null))
                assertFalse(call.startsWith("net/minecraft/network/packet/c2s/play/PlayerMoveC2SPacket") && call.endsWith(".<init>"), call);
        }
    }

    private static Set<String> calls(String type, String method) throws Exception {
        Set<String> calls = new HashSet<>();
        try (var stream = ScaffoldPredictionContractTest.class.getClassLoader().getResourceAsStream(type + ".class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (method != null && !method.equals(name)) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                            calls.add(owner + "." + name);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return calls;
    }
}
