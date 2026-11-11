package com.hunterbuddy.hud.system;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import static org.objectweb.asm.Opcodes.*;

final class SystemStatsRenderLoader extends ClassLoader {
    private static final String PROBE = Type.getInternalName(SystemStatsRenderProbe.class);
    private static final String VALUE = Type.getInternalName(SystemStatsRenderProbe.Value.class);
    private final Path baseline;

    SystemStatsRenderLoader() { this(null); }
    SystemStatsRenderLoader(Path baseline) { this.baseline = baseline; }

    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        boolean old = baseline != null && name.equals("com.hunterbuddy.hud.SystemStatsHud");
        boolean isolated = old || name.equals("meteordevelopment.meteorclient.systems.hud.HudRenderer")
            || name.equals("com.hunterbuddy.hud.system.SystemStatsPainter")
            || name.equals("com.hunterbuddy.hud.text.HudText")
            || name.equals("com.hunterbuddy.hud.glass.GlassPainter");
        if (!isolated) return super.loadClass(name, resolve);
        Class<?> found = findLoadedClass(name);
        if (found == null) {
            try {
                byte[] bytes;
                if (old) bytes = baseline(Files.readAllBytes(baseline));
                else if (name.endsWith(".HudRenderer")) bytes = renderer(name);
                else try (var stream = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                    if (stream == null) throw new ClassNotFoundException(name);
                    bytes = stream.readAllBytes();
                }
                found = defineClass(name, bytes, 0, bytes.length);
            } catch (IOException error) { throw new ClassNotFoundException(name, error); }
        }
        if (resolve) resolveClass(found);
        return found;
    }

    private static byte[] renderer(String name) {
        String owner = name.replace('.', '/');
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V21, ACC_PUBLIC, owner, null, "java/lang/Object", null);
        writer.visitField(ACC_PRIVATE | ACC_FINAL, "probe", "L" + PROBE + ";", null, null).visitEnd();
        var init = writer.visitMethod(ACC_PUBLIC, "<init>", "(L" + PROBE + ";)V", null, null);
        init.visitCode(); init.visitVarInsn(ALOAD, 0);
        init.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitVarInsn(ALOAD, 0); init.visitVarInsn(ALOAD, 1);
        init.visitFieldInsn(PUTFIELD, owner, "probe", "L" + PROBE + ";");
        init.visitInsn(RETURN); init.visitMaxs(0, 0); init.visitEnd();
        String color = "Lmeteordevelopment/meteorclient/utils/render/color/Color;";
        forward(writer, owner, "quad", "(DDDD" + color + ")V");
        forward(writer, owner, "quad", "(DDDD" + color.repeat(4) + ")V");
        forward(writer, owner, "triangle", "(DDDDDD" + color + ")V");
        forward(writer, owner, "textWidth", "(Ljava/lang/String;ZD)D");
        forward(writer, owner, "textHeight", "(ZD)D");
        forward(writer, owner, "text", "(Ljava/lang/String;DD" + color + "ZD)D");
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void forward(ClassWriter writer, String owner, String name, String descriptor) {
        var method = writer.visitMethod(ACC_PUBLIC, name, descriptor, null, null);
        method.visitCode(); method.visitVarInsn(ALOAD, 0);
        method.visitFieldInsn(GETFIELD, owner, "probe", "L" + PROBE + ";");
        int slot = 1;
        for (Type type : Type.getArgumentTypes(descriptor)) {
            method.visitVarInsn(type.getOpcode(ILOAD), slot);
            slot += type.getSize();
        }
        method.visitMethodInsn(INVOKEVIRTUAL, PROBE, name, descriptor, false);
        method.visitInsn(Type.getReturnType(descriptor).getOpcode(IRETURN));
        method.visitMaxs(0, 0); method.visitEnd();
    }

    private static byte[] baseline(byte[] original) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        new ClassReader(original).accept(new ClassVisitor(ASM9, writer) {
            @Override
            public void visit(int version, int access, String name, String signature, String parent, String[] interfaces) {
                super.visit(version, access, name, null, "java/lang/Object", interfaces);
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                return super.visitField(access, name, remap(descriptor), null, value);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (name.equals("<init>") || name.equals("<clinit>")) return null;
                return new MethodVisitor(ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String field, String type) {
                        super.visitFieldInsn(opcode, owner, field, remap(type));
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method, String type, boolean itf) {
                        if (owner.equals("meteordevelopment/meteorclient/settings/Setting") && method.equals("get")) owner = VALUE;
                        super.visitMethodInsn(opcode, owner, method, type, itf);
                    }
                };
            }
        }, 0);
        writer.visitField(ACC_PUBLIC, "x", "I", null, null).visitEnd();
        writer.visitField(ACC_PUBLIC, "y", "I", null, null).visitEnd();
        var init = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode(); init.visitVarInsn(ALOAD, 0);
        init.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(RETURN); init.visitMaxs(0, 0); init.visitEnd();
        var size = writer.visitMethod(ACC_PUBLIC, "setSize", "(DD)V", null, null);
        size.visitCode(); size.visitInsn(RETURN); size.visitMaxs(0, 0); size.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static String remap(String descriptor) {
        return descriptor.replace("Lmeteordevelopment/meteorclient/settings/Setting;", "L" + VALUE + ";");
    }
}
