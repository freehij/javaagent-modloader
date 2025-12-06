package io.github.freehij.loader;

import io.github.freehij.loader.annotation.Inject;
import io.github.freehij.loader.annotation.EditClass;
import io.github.freehij.loader.constant.At;
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
    private static final System.Logger LOGGER = System.getLogger("loader");
    private static final Map<String, List<InjectionPoint>> injectionPoints = new HashMap<>();
    private static final List<ModInfo> mods = new ArrayList<>();
    private static final List<URL> modUrls = new ArrayList<>();

    public static void premain(String args, Instrumentation inst) {
        processInjectionClass("io/github/freehij/injections/KnotClassPathFixer",
                Thread.currentThread().getContextClassLoader());
        loadMods();
        System.out.println(Arrays.toString(mods.toArray()));
        for (URL url : modUrls) {
            try {
                inst.appendToSystemClassLoaderSearch(new JarFile(url.getFile()));
            } catch (IOException e) {
                System.err.println("Failed to add mod JAR to System ClassLoader search path: " + url.toString());
                e.printStackTrace();
            }
        }
        scanInjections();
        inst.addTransformer(new MixinTransformer(), true);
    }

    private static void loadMods() {
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

    private static void scanInjections() {
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

    private static void processInjectionClass(String className, ClassLoader loader) {
        try {
            // need to replace class loading with bytecode analysis
            // because of recursive class loading
            // without that there are ain't no REAL fabric support
            // without direct having direct reference i'm just forced to use reflector (reflector sucks ngl)
            // :(
            Class<?> clazz = Class.forName(className.replace("/", "."), false, loader);
            EditClass injection = clazz.getAnnotation(EditClass.class);
            if (injection == null) return;

            for (Method method : clazz.getDeclaredMethods()) {
                Inject inject = method.getAnnotation(Inject.class);
                if (inject == null) continue;

                injectionPoints.computeIfAbsent(injection.value(), k -> new ArrayList<>())
                        .add(new InjectionPoint(
                                injection.value(),
                                inject.method(),
                                inject.descriptor(),
                                inject.at(),
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
        return Collections.unmodifiableList(mods);
    }

    public record ModInfo(String id, String name, String version, String creator,
                   List<String> injections, Path jarPath) { }

    record InjectionPoint(String targetClass, String methodName, String descriptor, At location, String handlerClass,
                          String handlerMethod) { }

    static class MixinTransformer implements ClassFileTransformer {
        @Override
        public byte[] transform(ClassLoader l, String className, Class<?> c,
                                ProtectionDomain d, byte[] buffer) {
            if (!injectionPoints.containsKey(className)) return null;
            LOGGER.log(System.Logger.Level.DEBUG, className + ", loader: " + l.getName());
            ClassReader cr = new ClassReader(buffer);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            cr.accept(new InjectionClassVisitor(cw, className), 0);
            return cw.toByteArray();
        }
    }

    static class InjectionClassVisitor extends ClassVisitor {
        private final String className;

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
                if (point.methodName.equals(name) && point.descriptor.equals(desc)) {
                    LOGGER.log(System.Logger.Level.DEBUG, name + desc);
                    mv = new InjectionMethodVisitor(mv, access, desc, point);
                }
            }
            return mv;
        }
    }

    static class InjectionMethodVisitor extends MethodVisitor {
        private final InjectionPoint injection;
        private final int methodAccess;
        private final String methodDesc;
        private boolean hasReturned;
        private boolean inInjection;

        InjectionMethodVisitor(MethodVisitor mv, int access, String desc, InjectionPoint injection) {
            super(Opcodes.ASM9, mv);
            this.injection = injection;
            this.methodAccess = access;
            this.methodDesc = desc;
        }

        @Override
        public void visitCode() {
            super.visitCode();
            if (injection.location == At.HEAD) injectHelper();
        }

        @Override
        public void visitInsn(int opcode) {
            if (inInjection) {
                super.visitInsn(opcode);
                return;
            }

            if (injection.location == At.RETURN && isReturn(opcode)) {
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
            if (injection.location == At.TAIL && !hasReturned) injectHelper();
            super.visitEnd();
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            super.visitMaxs(Math.max(maxStack, 10), Math.max(maxLocals, 101));
        }

        private boolean isReturn(int opcode) {
            return (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN);
        }

        private void injectHelper() {
            if (inInjection) return;
            inInjection = true;
            generateHelperCall(this, methodAccess, methodDesc, injection);
            inInjection = false;
        }
    }

    static void generateHelperCall(MethodVisitor mv, int access, String desc, InjectionPoint injection) {
        boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;

        mv.visitTypeInsn(Opcodes.NEW, "io/github/freehij/loader/util/InjectionHelper");
        mv.visitInsn(Opcodes.DUP);
        if (isStatic) {
            mv.visitInsn(Opcodes.ACONST_NULL);
        } else {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
        }
        Type[] args = Type.getArgumentTypes(desc);
        mv.visitIntInsn(Opcodes.BIPUSH, args.length);
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
        int localIndex = isStatic ? 0 : 1;
        for (int i = 0; i < args.length; i++) {
            mv.visitInsn(Opcodes.DUP);
            mv.visitIntInsn(Opcodes.BIPUSH, i);
            loadAndBoxArgument(mv, localIndex, args[i]);
            mv.visitInsn(Opcodes.AASTORE);
            localIndex += args[i].getSize();
        }
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "io/github/freehij/loader/util/InjectionHelper",
                "<init>",
                "(Ljava/lang/Object;[Ljava/lang/Object;)V",
                false);

        mv.visitVarInsn(Opcodes.ASTORE, 100);
        mv.visitVarInsn(Opcodes.ALOAD, 100);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                injection.handlerClass,
                injection.handlerMethod,
                "(Lio/github/freehij/loader/util/InjectionHelper;)V",
                false);
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

    private static void loadAndBoxArgument(MethodVisitor mv, int index, Type type) {
        mv.visitVarInsn(type.getOpcode(Opcodes.ILOAD), index);
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

    private static void box(MethodVisitor mv, String owner, String descriptor) {
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, owner, "valueOf", descriptor, false);
    }

    private static void unbox(MethodVisitor mv, Type type) {
        String className = type.getClassName().replace(".", "/");
        String[] method = {"booleanValue", "byteValue", "charValue", "shortValue",
                "intValue", "floatValue", "longValue", "doubleValue"};
        String[] desc = {"()Z", "()B", "()C", "()S", "()I", "()F", "()J", "()D"};

        int sort = type.getSort();
        if (sort >= Type.BOOLEAN && sort <= Type.DOUBLE) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, className);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, className,
                    method[sort - Type.BOOLEAN], desc[sort - Type.BOOLEAN], false);
        } else if (sort == Type.ARRAY || sort == Type.OBJECT) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, type.getInternalName());
        }
    }
}