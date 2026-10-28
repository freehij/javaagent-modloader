package io.github.freehij.loader.util;

import io.github.freehij.loader.annotation.EditClass;
import io.github.freehij.loader.annotation.FieldFlags;
import io.github.freehij.loader.annotation.Inject;
import io.github.freehij.loader.annotation.InterfaceSetup;
import io.github.freehij.loader.annotation.noreflection.NField;
import io.github.freehij.loader.annotation.noreflection.NLocal;
import io.github.freehij.loader.constant.AccessMode;
import io.github.freehij.loader.constant.FailStrategy;
import io.github.freehij.loader.constant.FinalMode;
import io.github.freehij.loader.constant.InterfaceOperation;
import org.objectweb.asm.*;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Consumer;

@SuppressWarnings({"unchecked", "rawtypes"})
public class AnnotationParser {
    public static ParsedClass parseClassForInjections(String className, ClassLoader loader) {
        ClassData data = parseClass(className, loader);
        if (data == null) {
            return new ParsedClass(null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }

        String[] editClassTarget = null;
        List<ParsedInterfaceSetup> interfaceSetups = new ArrayList<>();
        if (data.editClassData != null) {
            Map<String, Object> ec = data.editClassData.attributes();
            editClassTarget = mergeTargets(getStringArray(ec, "value"), getTypeArray(ec, "targets"));
            for (Object o : getObjectArray(ec, "interfaces")) {
                if (o instanceof AnnotationData isData) {
                    Map<String, Object> is = isData.attributes();
                    String[] targets = mergeTargets(getStringArray(is, "targetsString"),
                            getTypeArray(is, "targetsClass"));
                    if (targets != null) {
                        InterfaceOperation op = getEnum(is, "operation", InterfaceOperation.class, loader,
                                InterfaceOperation.ADD);
                        interfaceSetups.add(new ParsedInterfaceSetup(targets, op));
                    }
                }
            }
        }

        List<ParsedMethod> methods = new ArrayList<>();
        for (MethodVisitorImpl mvi : data.methods) {
            ParsedMethod pm = mvi.toParsed(loader);
            if (pm != null) methods.add(pm);
        }

        List<ParsedFieldFlags> fieldFlags = new ArrayList<>();
        if (data.fieldFlagsData != null) {
            for (Object o : getObjectArray(data.fieldFlagsData.attributes(), "value")) {
                if (o instanceof AnnotationData fData) {
                    Map<String, Object> f = fData.attributes();
                    String owner = preferString(getString(f, "owner", ""), getType(f, "ownerClazz"));
                    if (owner == null) continue;
                    String[] names = getStringArray(f, "names");
                    AccessMode access = getEnum(f, "access", AccessMode.class, loader, AccessMode.KEEP);
                    FinalMode finalMode = getEnum(f, "finalMode", FinalMode.class, loader, FinalMode.NON_FINAL);
                    FailStrategy fs = getEnum(f, "failStrategy", FailStrategy.class, loader, FailStrategy.NOTIFY);
                    fieldFlags.add(new ParsedFieldFlags(owner, names, access, finalMode, fs));
                }
            }
        }

        return new ParsedClass(editClassTarget, methods, fieldFlags, interfaceSetups);
    }

    static String[] mergeTargets(String[] strings, Type[] types) {
        Set<String> result = new LinkedHashSet<>();
        if (strings != null) {
            for (String s : strings) {
                if (s != null && !s.isEmpty()) result.add(s.replace('.', '/'));
            }
        }
        if (types != null) {
            for (Type t : types) {
                if (t != null) result.add(t.getInternalName());
            }
        }
        return result.isEmpty() ? null : result.toArray(new String[0]);
    }

    static String preferString(String str, Type type) {
        if (str != null && !str.isEmpty()) return str.replace('.', '/');
        if (type != null) return type.getInternalName();
        return null;
    }

    static String preferTypeString(String str, Type type) {
        if (str != null && !str.isEmpty()) {
            String s = str.replace('.', '/');
            if (s.length() == 1) return s;
            if (s.startsWith("[")) return s;
            if (s.startsWith("L") && s.endsWith(";")) return s;
            return "L" + s + ";";
        }
        if (type != null) return type.getDescriptor();
        return null;
    }

    static String getString(Map<String, Object> values, String key, String def) {
        Object v = values.get(key);
        return v instanceof String s ? s : def;
    }

    static String[] getStringArray(Map<String, Object> values, String key) {
        Object v = values.get(key);
        if (v instanceof Object[] arr) {
            List<String> list = new ArrayList<>();
            for (Object o : arr) {
                if (o instanceof String s) list.add(s);
            }
            return list.toArray(new String[0]);
        }
        if (v instanceof String s) return new String[]{s};
        return new String[0];
    }

    static Type[] getTypeArray(Map<String, Object> values, String key) {
        Object v = values.get(key);
        if (v instanceof Object[] arr) {
            List<Type> list = new ArrayList<>();
            for (Object o : arr) {
                if (o instanceof Type t) list.add(t);
            }
            return list.toArray(new Type[0]);
        }
        if (v instanceof Type t) return new Type[]{t};
        return new Type[0];
    }

    static Type getType(Map<String, Object> values, String key) {
        Object v = values.get(key);
        return v instanceof Type t ? t : null;
    }

    static Object[] getObjectArray(Map<String, Object> values, String key) {
        Object v = values.get(key);
        if (v instanceof Object[] arr) return arr;
        if (v != null) return new Object[]{v};
        return new Object[0];
    }

    static <E extends Enum<E>> E getEnum(Map<String, Object> values, String key, Class<E> enumClass,
                                         ClassLoader loader, E def) {
        Object v = values.get(key);
        if (v instanceof EnumPlaceholder ep) {
            try {
                String cn = ep.enumType().substring(1, ep.enumType().length() - 1).replace('/', '.');
                Class<E> clazz = (Class<E>) Class.forName(cn, false, loader);
                return Enum.valueOf(clazz, ep.constantName());
            } catch (Exception e) {
                return def;
            }
        }
        return def;
    }

    static ClassData parseClass(String className, ClassLoader loader) {
        try (InputStream is = loader.getResourceAsStream(className + ".class")) {
            if (is == null) return null;
            ClassReader cr = new ClassReader(is);
            ClassVisitorImpl visitor = new ClassVisitorImpl();
            cr.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return new ClassData(visitor.editClassData, visitor.fieldFlagsData, visitor.methods);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read class: " + className, e);
        }
    }

    static class ClassVisitorImpl extends ClassVisitor {
        AnnotationData editClassData;
        AnnotationData fieldFlagsData;
        List<MethodVisitorImpl> methods = new ArrayList<>();

        ClassVisitorImpl() { super(Opcodes.ASM9); }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (descriptor.equals("Lio/github/freehij/loader/annotation/EditClass;")) {
                return new ValueCollector(values -> editClassData = new AnnotationData(descriptor, values));
            }
            if (descriptor.equals("Lio/github/freehij/loader/annotation/FieldFlags;")) {
                return new ValueCollector(values -> fieldFlagsData = new AnnotationData(descriptor, values));
            }
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitorImpl mv = new MethodVisitorImpl(name, descriptor);
            methods.add(mv);
            return mv;
        }
    }

    static class MethodVisitorImpl extends MethodVisitor {
        final String name, descriptor;
        AnnotationData injectData;
        final Map<Integer, AnnotationData> nFields = new HashMap<>();
        final Map<Integer, AnnotationData> nLocals = new HashMap<>();

        MethodVisitorImpl(String name, String descriptor) {
            super(Opcodes.ASM9);
            this.name = name;
            this.descriptor = descriptor;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String annDesc, boolean visible) {
            if (annDesc.equals("Lio/github/freehij/loader/annotation/Inject;")) {
                return new ValueCollector(values -> injectData = new AnnotationData(annDesc, values));
            }
            return null;
        }

        @Override
        public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
            if (desc.equals("Lio/github/freehij/loader/annotation/noreflection/NField;")) {
                return new ValueCollector(values -> nFields.put(parameter, new AnnotationData(desc, values)));
            }
            if (desc.equals("Lio/github/freehij/loader/annotation/noreflection/NLocal;")) {
                return new ValueCollector(values -> nLocals.put(parameter, new AnnotationData(desc, values)));
            }
            return null;
        }

        ParsedMethod toParsed(ClassLoader loader) {
            if (injectData == null) return null;
            Inject inject = createAnnotationProxy(Inject.class, injectData, loader);
            Type[] argTypes = Type.getArgumentTypes(descriptor);
            List<ParsedParameter> params = new ArrayList<>();
            for (int i = 0; i < argTypes.length; i++) {
                AnnotationData nf = nFields.get(i);
                AnnotationData nl = nLocals.get(i);
                NFieldData nField = null;
                NLocalData nLocal = null;
                if (nf != null) {
                    Map<String, Object> v = nf.attributes();
                    String fieldName = getString(v, "name", "");
                    String typeStr = getString(v, "typeString", "");
                    Type typeT = getType(v, "typeClass");
                    String typeDesc = preferTypeString(typeStr, typeT);
                    FailStrategy fs = getEnum(v, "failStrategy", FailStrategy.class, loader, FailStrategy.HARD);
                    nField = new NFieldData(fieldName, typeDesc, fs);
                }
                if (nl != null) {
                    Map<String, Object> v = nl.attributes();
                    int index = v.get("index") instanceof Integer i1 ? i1 : 0;
                    String typeStr = getString(v, "typeString", "");
                    Type typeT = getType(v, "typeClass");
                    String typeDesc = preferTypeString(typeStr, typeT);
                    FailStrategy fs = getEnum(v, "failStrategy", FailStrategy.class, loader, FailStrategy.HARD);
                    nLocal = new NLocalData(index, typeDesc, fs);
                }
                params.add(new ParsedParameter(i, argTypes[i], nField, nLocal));
            }
            return new ParsedMethod(name, descriptor, inject, params);
        }
    }

    static class ValueCollector extends AnnotationVisitor {
        final Map<String, Object> values = new HashMap<>();
        final Consumer<Map<String, Object>> onFinish;

        ValueCollector(Consumer<Map<String, Object>> onFinish) {
            super(Opcodes.ASM9);
            this.onFinish = onFinish;
        }

        @Override
        public void visit(String name, Object value) { values.put(name, value); }

        @Override
        public void visitEnum(String name, String descriptor, String value) {
            values.put(name, new EnumPlaceholder(descriptor, value));
        }

        @Override
        public AnnotationVisitor visitAnnotation(String name, String descriptor) {
            return new ValueCollector(nested -> values.put(name, new AnnotationData(descriptor, nested)));
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            return new AnnotationVisitor(Opcodes.ASM9) {
                final List<Object> list = new ArrayList<>();

                @Override
                public void visit(String ignored, Object value) { list.add(value); }

                @Override
                public void visitEnum(String ignored, String descriptor, String value) {
                    list.add(new EnumPlaceholder(descriptor, value));
                }

                @Override
                public AnnotationVisitor visitAnnotation(String ignored, String descriptor) {
                    return new ValueCollector(nested -> list.add(new AnnotationData(descriptor, nested)));
                }

                @Override
                public void visitEnd() { values.put(name, list.toArray()); }
            };
        }

        @Override
        public void visitEnd() { onFinish.accept(values); }
    }

    record ClassData(AnnotationData editClassData, AnnotationData fieldFlagsData,
                     List<MethodVisitorImpl> methods) { }

    record AnnotationData(String descriptor, Map<String, Object> attributes) { }

    record EnumPlaceholder(String enumType, String constantName) {
        Enum<?> toEnum(ClassLoader loader) {
            try {
                String className = enumType.substring(1, enumType.length() - 1).replace('/', '.');
                Class<Enum> clazz = (Class<Enum>) Class.forName(className, false, loader);
                return Enum.valueOf(clazz, constantName);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Enum class not found: " + enumType, e);
            }
        }
    }

    static <A extends Annotation> A createAnnotationProxy(Class<A> annotationType, AnnotationData data,
                                                          ClassLoader loader) {
        return (A) Proxy.newProxyInstance(loader, new Class<?>[]{annotationType},
                new AnnotationInvocationHandler(annotationType, data, loader)
        );
    }

    static class AnnotationInvocationHandler implements InvocationHandler {
        final Class<? extends Annotation> annotationType;
        final AnnotationData data;
        final ClassLoader loader;
        final Map<String, Object> resolved = new HashMap<>();

        AnnotationInvocationHandler(Class<? extends Annotation> annotationType, AnnotationData data,
                                    ClassLoader loader) {
            this.annotationType = annotationType;
            this.data = data;
            this.loader = loader;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            switch (name) {
                case "toString": return annotationToString();
                case "hashCode": return annotationHashCode();
                case "equals": return annotationEquals(args[0]);
                case "annotationType": return annotationType;
            }

            if (resolved.containsKey(name)) return resolved.get(name);

            Object raw = data.attributes.get(name);
            Object value = resolve(raw, method.getReturnType());
            if (value == null) value = method.getDefaultValue();
            resolved.put(name, value);
            return value;
        }

        Object resolve(Object raw, Class<?> expectedType) {
            if (raw == null) return null;
            if (raw instanceof Type t) {
                try {
                    return Class.forName(t.getClassName(), false, loader);
                } catch (ClassNotFoundException e) {
                    return null;
                }
            }
            if (raw instanceof String && expectedType == String[].class) {
                return new String[]{(String) raw};
            }
            if (!raw.getClass().isArray()) {
                if (raw instanceof AnnotationData nested) {
                    String className = nested.descriptor.substring(1, nested.descriptor.length() - 1)
                            .replace('/', '.');
                    try {
                        Class<? extends Annotation> nestedType = (Class<? extends Annotation>) Class.forName(
                                className, false, loader);
                        return createAnnotationProxy(nestedType, nested, loader);
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException("Annotation class not found: " + className, e);
                    }
                }
                if (raw instanceof EnumPlaceholder) {
                    return ((EnumPlaceholder) raw).toEnum(loader);
                }
                return raw;
            }
            int len = Array.getLength(raw);
            Object resolvedArray = Array.newInstance(expectedType.getComponentType(), len);
            for (int i = 0; i < len; i++) {
                Array.set(resolvedArray, i, resolve(Array.get(raw, i), expectedType.getComponentType()));
            }
            return resolvedArray;
        }

        String annotationToString() {
            StringBuilder sb = new StringBuilder("@").append(annotationType.getName()).append("(");
            boolean first = true;
            for (Method m : annotationType.getDeclaredMethods()) {
                if (!first) sb.append(", ");
                first = false;
                sb.append(m.getName()).append("=");
                try {
                    sb.append(valueToString(invoke(null, m, null)));
                } catch (Throwable e) {
                    sb.append("<error>");
                }
            }
            return sb.append(")").toString();
        }

        String valueToString(Object val) {
            if (val instanceof String) return "\"" + val + "\"";
            if (val instanceof Character) return "'" + val + "'";
            if (val != null && val.getClass().isArray()) {
                StringBuilder sb = new StringBuilder("[");
                int len = Array.getLength(val);
                for (int i = 0; i < len; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(valueToString(Array.get(val, i)));
                }
                return sb.append("]").toString();
            }
            return String.valueOf(val);
        }

        int annotationHashCode() {
            int hash = 0;
            for (Method m : annotationType.getDeclaredMethods()) {
                try {
                    Object val = invoke(null, m, null);
                    hash += (127 * m.getName().hashCode()) ^ valueHashCode(val);
                } catch (Throwable ignored) {}
            }
            return hash;
        }

        int valueHashCode(Object val) {
            if (val == null) return 0;
            if (!val.getClass().isArray()) return val.hashCode();
            int h = 1;
            for (int i = 0; i < Array.getLength(val); i++) {
                Object e = Array.get(val, i);
                h = 31 * h + (e == null ? 0 : valueHashCode(e));
            }
            return h;
        }

        boolean annotationEquals(Object other) {
            if (this == other) return true;
            if (!annotationType.isInstance(other)) return false;
            for (Method m : annotationType.getDeclaredMethods()) {
                try {
                    Object thisVal = invoke(null, m, null);
                    Object otherVal = m.invoke(other);
                    if (!valuesEqual(thisVal, otherVal)) return false;
                } catch (Throwable e) {
                    return false;
                }
            }
            return true;
        }

        boolean valuesEqual(Object a, Object b) {
            if (a == b) return true;
            if (a == null || b == null) return false;
            if (a.getClass() != b.getClass()) return false;
            if (a.getClass().isArray()) {
                int len = Array.getLength(a);
                if (len != Array.getLength(b)) return false;
                for (int i = 0; i < len; i++) {
                    if (!valuesEqual(Array.get(a, i), Array.get(b, i))) return false;
                }
                return true;
            }
            return a.equals(b);
        }
    }

    public static class ParsedClass {
        public final String[] editClassTarget;
        public final List<ParsedMethod> methods;
        public final List<ParsedFieldFlags> fieldFlags;
        public final List<ParsedInterfaceSetup> interfaceSetups;

        ParsedClass(String[] editClassTarget, List<ParsedMethod> methods,
                    List<ParsedFieldFlags> fieldFlags, List<ParsedInterfaceSetup> interfaceSetups) {
            this.editClassTarget = editClassTarget;
            this.methods = methods;
            this.fieldFlags = fieldFlags;
            this.interfaceSetups = interfaceSetups;
        }
    }

    public static class ParsedMethod {
        public final String name;
        public final String descriptor;
        public final Inject inject;
        public final List<ParsedParameter> parameters;

        ParsedMethod(String name, String descriptor, Inject inject, List<ParsedParameter> parameters) {
            this.name = name;
            this.descriptor = descriptor;
            this.inject = inject;
            this.parameters = parameters;
        }
    }

    public static class ParsedParameter {
        public final int index;
        public final Type type;
        public final NFieldData nField;
        public final NLocalData nLocal;

        ParsedParameter(int index, Type type, NFieldData nField, NLocalData nLocal) {
            this.index = index;
            this.type = type;
            this.nField = nField;
            this.nLocal = nLocal;
        }
    }

    public record NFieldData(String name, String typeDesc, FailStrategy failStrategy) { }
    public record NLocalData(int index, String typeDesc, FailStrategy failStrategy) { }

    public static class ParsedFieldFlags {
        public final String owner;
        public final String[] names;
        public final AccessMode access;
        public final FinalMode finalMode;
        public final FailStrategy failStrategy;

        ParsedFieldFlags(String owner, String[] names, AccessMode access, FinalMode finalMode,
                         FailStrategy failStrategy) {
            this.owner = owner;
            this.names = names;
            this.access = access;
            this.finalMode = finalMode;
            this.failStrategy = failStrategy;
        }
    }

    public static class ParsedInterfaceSetup {
        public final String[] targets;
        public final InterfaceOperation operation;

        ParsedInterfaceSetup(String[] targets, InterfaceOperation operation) {
            this.targets = targets;
            this.operation = operation;
        }
    }
}