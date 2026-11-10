package com.hunterbuddy.hud.performance;

import java.io.IOException;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import static org.objectweb.asm.Opcodes.*;

final class PainterCaptureLoader extends ClassLoader {
    private static final String SURFACE = Type.getInternalName(PerformanceCaptureSurface.class);

    PainterCaptureLoader() {
        super(PainterCaptureLoader.class.getClassLoader());
    }

    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        boolean isolated = name.equals("meteordevelopment.meteorclient.systems.hud.HudRenderer")
            || name.equals("meteordevelopment.meteorclient.renderer.Texture")
            || name.equals("meteordevelopment.meteorclient.renderer.text.Font")
            || name.startsWith("meteordevelopment.meteorclient.renderer.text.Font$")
            || name.equals("com.hunterbuddy.hud.performance.PerformancePainter")
            || name.equals("com.hunterbuddy.hud.performance.PerformanceLayout")
            || name.equals("com.hunterbuddy.hud.glass.GlassPainter")
            || name.equals("com.hunterbuddy.hud.text.HudText")
            || name.startsWith("com.hunterbuddy.hud.toast.ModuleToastPainter");
        if (!isolated) return super.loadClass(name, resolve);
        Class<?> found = findLoadedClass(name);
        if (found == null) {
            byte[] bytes;
            if (name.endsWith(".HudRenderer")) bytes = renderer(name);
            else if (name.endsWith(".Texture")) bytes = texture(name);
            else {
                String resource = name.replace('.', '/') + ".class";
                try {
                    try (var stream = getParent().getResourceAsStream(resource)) {
                        if (stream == null) throw new ClassNotFoundException(name);
                        bytes = stream.readAllBytes();
                    }
                } catch (IOException error) { throw new ClassNotFoundException(name, error); }
            }
            found = defineClass(name, bytes, 0, bytes.length);
        }
        if (resolve) resolveClass(found);
        return found;
    }

    private static byte[] renderer(String name) {
        String owner = name.replace('.', '/');
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V21, ACC_PUBLIC, owner, null, "java/lang/Object", null);
        writer.visitField(ACC_PRIVATE | ACC_FINAL, "surface", "L" + SURFACE + ";", null, null).visitEnd();
        var constructor = writer.visitMethod(ACC_PUBLIC, "<init>", "(L" + SURFACE + ";)V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitVarInsn(ALOAD, 1);
        constructor.visitFieldInsn(PUTFIELD, owner, "surface", "L" + SURFACE + ";");
        constructor.visitInsn(RETURN); constructor.visitMaxs(0, 0); constructor.visitEnd();
        String color = "Lmeteordevelopment/meteorclient/utils/render/color/Color;";
        forward(writer, owner, "quad", "(DDDD" + color + ")V");
        forward(writer, owner, "quad", "(DDDD" + color.repeat(4) + ")V");
        forward(writer, owner, "triangle", "(DDDDDD" + color + ")V");
        forward(writer, owner, "textWidth", "(Ljava/lang/String;ZD)D");
        forward(writer, owner, "textHeight", "(ZD)D");
        forward(writer, owner, "text", "(Ljava/lang/String;DD" + color + "ZD)D");
        writer.visitEnd(); return writer.toByteArray();
    }

    private static void forward(ClassWriter writer, String owner, String name, String descriptor) {
        var method = writer.visitMethod(ACC_PUBLIC, name, descriptor, null, null);
        method.visitCode();
        method.visitVarInsn(ALOAD, 0);
        method.visitFieldInsn(GETFIELD, owner, "surface", "L" + SURFACE + ";");
        int slot = 1;
        for (Type type : Type.getArgumentTypes(descriptor)) {
            method.visitVarInsn(type.getOpcode(ILOAD), slot); slot += type.getSize();
        }
        method.visitMethodInsn(INVOKEVIRTUAL, SURFACE, name, descriptor, false);
        method.visitInsn(Type.getReturnType(descriptor).getOpcode(IRETURN));
        method.visitMaxs(0, 0); method.visitEnd();
    }

    private static byte[] texture(String name) {
        String owner = name.replace('.', '/');
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V21, ACC_PUBLIC, owner, null, "java/lang/Object", null);
        writer.visitField(ACC_PUBLIC, "bitmap", "Ljava/nio/ByteBuffer;", null, null).visitEnd();
        var constructor = writer.visitMethod(ACC_PUBLIC, "<init>",
            "(IILcom/mojang/blaze3d/textures/TextureFormat;Lcom/mojang/blaze3d/textures/FilterMode;Lcom/mojang/blaze3d/textures/FilterMode;)V", null, null);
        constructor.visitCode(); constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(RETURN); constructor.visitMaxs(0, 0); constructor.visitEnd();
        var upload = writer.visitMethod(ACC_PUBLIC, "upload", "(Ljava/nio/ByteBuffer;)V", null, null);
        upload.visitCode(); upload.visitVarInsn(ALOAD, 0); upload.visitVarInsn(ALOAD, 1);
        upload.visitFieldInsn(PUTFIELD, owner, "bitmap", "Ljava/nio/ByteBuffer;");
        upload.visitInsn(RETURN); upload.visitMaxs(0, 0); upload.visitEnd();
        writer.visitEnd(); return writer.toByteArray();
    }
}
