package io.github.freehij.loader;

import io.github.freehij.loader.annotation.Inject;
import io.github.freehij.loader.annotation.EditClass;
import io.github.freehij.loader.annotation.Local;
import io.github.freehij.loader.constant.At;
import io.github.freehij.loader.util.Logger;
import org.objectweb.asm.*;

import java.io.*;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Loader {
    static final String VERSION = "a1.0.0";
    static final Map<String, List<InjectionPoint>> injectionPoints = new HashMap<>();
    static final List<ModInfo> mods = new ArrayList<>();
    static final List<URL> modUrls = new ArrayList<>();

    public static void premain(String args, Instrumentation inst) {
        processInjectionClass("io/github/freehij/injections/KnotClassPathFixer",
                Thread.currentThread().getContextClassLoader());
        defineMods(true);
        try {
            Class.forName("net.fabricmc.loader.impl.FabricLoaderImpl");
        } catch (ClassNotFoundException ignored) {
            boolean isServer = true;
            try {
                Class.forName("net.minecraft.client.Minecraft");
                isServer = false;
            } catch (ClassNotFoundException ignored1) { }
            for (URL url : modUrls) {
                try {
                    if (isServer) {
                        inst.appendToBootstrapClassLoaderSearch(new JarFile(url.getFile()));
                    } else {
                        inst.appendToSystemClassLoaderSearch(new JarFile(url.getFile()));
                    }
                } catch (IOException e) {
                    System.err.println("Failed to add mod JAR to System ClassLoader search path: " + url);
                    e.printStackTrace();
                }
            }
        }
        scanInjections();
        inst.addTransformer(new MixinTransformer(), true);
    }

    static void defineMods(boolean log) {
        mods.add(new ModInfo(
                "loader",
                "Loader",
                VERSION,
                "freehij",
                "Synthetic loader modid for dependency checking.",
                "No license",
                new ArrayList<>(),
                null
        ));
        loadMods();
        if (log) {
            Logger.info("Found mods:", "Loader");
            for (ModInfo mod : mods) {
                Logger.info("	- " + mod.toString(), "Loader");
            }
        }
    }

    static void loadMods() {
        try {
            Path modsDir = Paths.get("mods");
            if (!Files.exists(modsDir)) {
                Files.createDirectories(modsDir);
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.{jar,zip}")) {
                for (Path jarPath : stream) {
                    try (JarFile jar = new JarFile(jarPath.toFile())) {
                        JarEntry config = jar.getJarEntry("mod.properties");
                        if (config == null) continue;

                        Properties props = new Properties();
                        props.load(jar.getInputStream(config));

                        ModInfo mod = new ModInfo(
                                props.getProperty("modid"),
                                props.getProperty("name"),
                                props.getProperty("version"),
                                props.getProperty("creator"),
                                props.getProperty("description", "No description"),
                                props.getProperty("license", "No license"),
                                Arrays.asList(props.getProperty("injections", "").split(",")),
                                jarPath
                        );
                        mods.add(mod);
                        modUrls.add(jarPath.toUri().toURL());
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void scanInjections() {
        URL[] urls = modUrls.toArray(new URL[0]);
        try (URLClassLoader modLoader = new URLClassLoader(urls, Thread.currentThread().getContextClassLoader())) {
            for (ModInfo mod : mods) {
                for (String className : mod.injections) {
                    if (className.isEmpty()) continue;
                    processInjectionClass(className, modLoader);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void processInjectionClass(String className, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(className.replace("/", "."), false, loader);
            EditClass target = clazz.getAnnotation(EditClass.class);
            if (target == null) return;

            for (Method method : clazz.getDeclaredMethods()) {
                Inject inject = method.getAnnotation(Inject.class);
                if (inject == null) continue;

                injectionPoints.computeIfAbsent(target.value(), k -> new ArrayList<>())
                        .add(new InjectionPoint(
                                inject,
                                target.value(),
                                clazz.getName().replace('.', '/'),
                                method.getName()
                        ));
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static List<URL> getModUrls() {
        return Collections.unmodifiableList(modUrls);
    }

    public static List<ModInfo> getMods() {
        // TODO: proper fix for evil knot conflicts
        if (mods.isEmpty()) defineMods(false);
        return Collections.unmodifiableList(mods);
    }

    public record ModInfo(String id, String name, String version, String creator,
                   String description, String license, List<String> injections, Path jarPath) {
        @Override
        public String toString() {
            return name + " (" + id + ") " + version + " by " + creator;
        }
    }

    record InjectionPoint(Inject inject, String targetClass, String handlerClass, String handlerMethod) { }

    static class MixinTransformer implements ClassFileTransformer {
        @Override
        public byte[] transform(ClassLoader l, String className, Class<?> c,
                                ProtectionDomain d, byte[] buffer) {
            if (!injectionPoints.containsKey(className)) return null;
            Logger.debug("Loading " + className + ", loader: " + l.getName(), this);
            ClassReader cr = new ClassReader(buffer);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            cr.accept(new InjectionClassVisitor(cw, className), 0);
            return cw.toByteArray();
        }
    }

    static class InjectionClassVisitor extends ClassVisitor {
        final String className;

        InjectionClassVisitor(ClassVisitor cv, String className) {
            super(Opcodes.ASM9, cv);
            this.className = className;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc,
                                         String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            List<InjectionPoint> points = injectionPoints.get(className);
            if (points == null) return mv;
            for (InjectionPoint point : points) {
                if (point.inject.method().equals(name) &&
                        (point.inject.descriptor().isEmpty() || point.inject.descriptor().equals(desc))) {
                    Logger.debug("Transforming " + name + desc +
                            ", handler: " + point.handlerClass + "." + point.handlerMethod, this);
                    mv = new InjectionMethodVisitor(mv, access, desc, point, this.className);
                }
            }
            return mv;
        }
    }

    static class InjectionMethodVisitor extends MethodVisitor {
        final InjectionPoint injection;
        final int methodAccess;
        final String methodDesc;
        final String className;
        boolean hasReturned;
        boolean inInjection;

        InjectionMethodVisitor(MethodVisitor mv, int access, String desc, InjectionPoint injection, String className) {
            super(Opcodes.ASM9, mv);
            this.injection = injection;
            this.methodAccess = access;
            this.methodDesc = desc;
            this.className = className;
        }

        @Override
        public void visitCode() {
            super.visitCode();
            if (injection.inject.at() == At.HEAD) injectHelper();
        }

        @Override
        public void visitInsn(int opcode) {
            if (inInjection) {
                super.visitInsn(opcode);
                return;
            }

            if (injection.inject.at() == At.RETURN && isReturn(opcode)) {
                injectHelper();
                super.visitInsn(opcode);
                hasReturned = true;
            } else {
                super.visitInsn(opcode);
                if (isReturn(opcode)) hasReturned = true;
            }
        }

        @Override
        public void visitEnd() {
            if (injection.inject.at() == At.TAIL && !hasReturned) injectHelper();
            super.visitEnd();
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            super.visitMaxs(Math.max(maxStack, 10), Math.max(maxLocals, 101));
        }

        boolean isReturn(int opcode) {
            return (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN);
        }

        void injectHelper() {
            if (inInjection) return;
            inInjection = true;
            generateHelperCall(this, methodAccess, methodDesc, injection, className);
            inInjection = false;
        }
    }

    static void generateHelperCall(MethodVisitor mv, int access, String desc, InjectionPoint injection,
                                   String className) {
        boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;

        mv.visitTypeInsn(Opcodes.NEW, "io/github/freehij/loader/util/InjectionHelper");
        mv.visitInsn(Opcodes.DUP);
        if (isStatic) {
            mv.visitInsn(Opcodes.ACONST_NULL);
        } else {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
        }
        mv.visitLdcInsn(Type.getObjectType(className));
        Type[] argTypes = Type.getArgumentTypes(desc);
        mv.visitIntInsn(Opcodes.BIPUSH, argTypes.length);
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
        int localIndex = isStatic ? 0 : 1;
        for (int i = 0; i < argTypes.length; i++) {
            mv.visitInsn(Opcodes.DUP);
            mv.visitIntInsn(Opcodes.BIPUSH, i);
            mv.visitVarInsn(argTypes[i].getOpcode(Opcodes.ILOAD), localIndex);
            boxElement(mv, argTypes[i]);
            mv.visitInsn(Opcodes.AASTORE);
            localIndex += argTypes[i].getSize();
        }

        Local[] locals = injection.inject.locals();
        mv.visitIntInsn(Opcodes.BIPUSH, locals.length);
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
        for (int i = 0; i < locals.length; i++) {
            Local local = locals[i];
            mv.visitInsn(Opcodes.DUP);
            mv.visitIntInsn(Opcodes.BIPUSH, i);
            Type localType = Type.getType(local.type());
            mv.visitVarInsn(localType.getOpcode(Opcodes.ILOAD), local.index());
            boxElement(mv, localType);
            mv.visitInsn(Opcodes.AASTORE);
        }
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "io/github/freehij/loader/util/InjectionHelper",
                "<init>",
                "(Ljava/lang/Object;Ljava/lang/Class;[Ljava/lang/Object;[Ljava/lang/Object;)V",
                false);

        mv.visitVarInsn(Opcodes.ASTORE, 100);
        mv.visitVarInsn(Opcodes.ALOAD, 100);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                injection.handlerClass(),
                injection.handlerMethod(),
                "(Lio/github/freehij/loader/util/InjectionHelper;)V",
                false);

        if (injection.inject.modifyLocals() && locals.length > 0) {
            mv.visitVarInsn(Opcodes.ALOAD, 100);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                    "io/github/freehij/loader/util/InjectionHelper",
                    "getLocals",
                    "()[Ljava/lang/Object;",
                    false);
            for (int i = 0; i < locals.length; i++) {
                Local local = locals[i];
                Type localType = Type.getType(local.type());
                mv.visitInsn(Opcodes.DUP);
                mv.visitIntInsn(Opcodes.BIPUSH, i);
                mv.visitInsn(Opcodes.AALOAD);
                if (localType.getSort() <= Type.DOUBLE) {
                    unbox(mv, localType);
                } else {
                    mv.visitTypeInsn(Opcodes.CHECKCAST, localType.getInternalName());
                }
                mv.visitVarInsn(localType.getOpcode(Opcodes.ISTORE), local.index());
            }
            mv.visitInsn(Opcodes.POP);
        }

        Label continueLabel = new Label();
        mv.visitVarInsn(Opcodes.ALOAD, 100);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                "io/github/freehij/loader/util/InjectionHelper", "isCancelled", "()Z", false);
        mv.visitJumpInsn(Opcodes.IFEQ, continueLabel);

        Type returnType = Type.getReturnType(desc);
        if (returnType == Type.VOID_TYPE) {
            mv.visitInsn(Opcodes.RETURN);
        } else {
            mv.visitVarInsn(Opcodes.ALOAD, 100);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                    "io/github/freehij/loader/util/InjectionHelper", "getReturnValue", "()Ljava/lang/Object;", false);
            unbox(mv, returnType);
            mv.visitInsn(returnType.getOpcode(Opcodes.IRETURN));
        }

        mv.visitLabel(continueLabel);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
    }

    static void boxElement(MethodVisitor mv, Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN: box(mv, "java/lang/Boolean", "(Z)Ljava/lang/Boolean;"); break;
            case Type.BYTE: box(mv, "java/lang/Byte", "(B)Ljava/lang/Byte;"); break;
            case Type.CHAR: box(mv, "java/lang/Character", "(C)Ljava/lang/Character;"); break;
            case Type.SHORT: box(mv, "java/lang/Short", "(S)Ljava/lang/Short;"); break;
            case Type.INT: box(mv, "java/lang/Integer", "(I)Ljava/lang/Integer;"); break;
            case Type.FLOAT: box(mv, "java/lang/Float", "(F)Ljava/lang/Float;"); break;
            case Type.LONG: box(mv, "java/lang/Long", "(J)Ljava/lang/Long;"); break;
            case Type.DOUBLE: box(mv, "java/lang/Double", "(D)Ljava/lang/Double;"); break;
        }
    }

    static void box(MethodVisitor mv, String owner, String descriptor) {
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, owner, "valueOf", descriptor, false);
    }

    static void unbox(MethodVisitor mv, Type type) {
        int sort = type.getSort();
        if (sort >= Type.BOOLEAN && sort <= Type.DOUBLE) {
            String[] wrappers = {
                    "java/lang/Boolean", "java/lang/Byte", "java/lang/Character",
                    "java/lang/Short", "java/lang/Integer", "java/lang/Float",
                    "java/lang/Long", "java/lang/Double"
            };
            String[] methods = {"booleanValue", "byteValue", "charValue", "shortValue",
                    "intValue", "floatValue", "longValue", "doubleValue"};
            String[] descs = {"()Z", "()B", "()C", "()S", "()I", "()F", "()J", "()D"};
            String wrapper = wrappers[sort - 1];
            mv.visitTypeInsn(Opcodes.CHECKCAST, wrapper);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, wrapper, methods[sort - 1], descs[sort - 1], false);
        } else if (sort == Type.ARRAY || sort == Type.OBJECT) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, type.getInternalName());
        }
    }
}