package com.hunterbuddy.journal;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.*;

final class JournalTargetsTest {
    @Test
    void actualMinecraftAndMeteorBytecodeContainsEveryRequiredTarget() throws IOException {
        String text = "Lnet/minecraft/text/Text;";
        String triple = "(" + text + "Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V";
        String hud = "net/minecraft/client/gui/hud/ChatHud";
        Set<String> chatMethods = inspect(hud, null, null, null, null);
        assertTrue(chatMethods.contains("addMessage" + triple));
        assertEquals(2, count("net/minecraft/client/network/message/MessageHandler", null, hud, "addMessage", "(" + text + ")V"));
        assertEquals(3, count("net/minecraft/client/network/message/MessageHandler", null, hud, "addMessage", triple));
        String meteor = "meteordevelopment/meteorclient/utils/player/ChatUtils";
        String method = "sendMsg(ILjava/lang/String;Lnet/minecraft/util/Formatting;" + text + ")V";
        assertTrue(inspect(meteor, null, null, null, null).contains(method));
        assertEquals(1, count(meteor, method, "net/minecraft/client/MinecraftClient", "execute", "(Ljava/lang/Runnable;)V"));
        Set<String> screen = inspect("net/minecraft/client/gui/screen/ChatScreen", null, null, null, null);
        assertTrue(screen.contains("render(Lnet/minecraft/client/gui/DrawContext;IIF)V"));
        assertTrue(screen.contains("mouseClicked(Lnet/minecraft/client/gui/Click;Z)Z"));
        assertTrue(screen.contains("mouseScrolled(DDDD)Z"));
        assertTrue(screen.contains("keyPressed(Lnet/minecraft/client/input/KeyInput;)Z"));
        assertTrue(screen.contains("removed()V"));
    }

    private static int count(String type, String method, String owner, String name, String descriptor) throws IOException {
        return inspect(type, method, owner, name, descriptor).size();
    }

    private static Set<String> inspect(String type, String method, String owner, String name, String descriptor) throws IOException {
        Set<String> found = new HashSet<>();
        try (var stream = JournalTargetsTest.class.getClassLoader().getResourceAsStream(type + ".class")) {
            assertNotNull(stream, type);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                int index;

                @Override
                public MethodVisitor visitMethod(int access, String candidate, String desc, String signature, String[] exceptions) {
                    if (owner == null) { found.add(candidate + desc); return null; }
                    if (method != null && !method.equals(candidate + desc)) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String actualOwner, String actualName, String actualDescriptor, boolean isInterface) {
                            if (owner.equals(actualOwner) && name.equals(actualName) && descriptor.equals(actualDescriptor))
                                found.add(candidate + desc + ":" + index++);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return found;
    }
}
