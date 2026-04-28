package io.github.freehij.loader.util;

import io.github.freehij.loader.annotation.Inject;
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
        if (data == null || data.editClassTarget == null || data.editClassTarget.length == 0) {
            return new ParsedClass(null, Collections.emptyList());
        }

        List<ParsedMethod> methods = new ArrayList<>();
        for (Map.Entry<String, AnnotationData> entry : data.injectAnnotations.entrySet()) {
            Inject injectProxy = createAnnotationProxy(Inject.class, entry.getValue(), loader);
            methods.add(new ParsedMethod(entry.getKey(), injectProxy));
        }

        return new ParsedClass(data.editClassTarget, methods);
    }

    static ClassData parseClass(String className, ClassLoader loader) {
        try (InputStream is = loader.getResourceAsStream(className + ".class")) {
            if (is == null) return null;
            ClassReader cr = new ClassReader(is);
            ClassVisitorImpl visitor = new ClassVisitorImpl();
            cr.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return visitor.getData();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read class: " + className, e);
        }
    }

    static class ClassVisitorImpl extends ClassVisitor {
        String[] editClassValues;
        final Map<String, AnnotationData> injectAnnotations = new HashMap<>();

        ClassVisitorImpl() { super(Opcodes.ASM9); }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (descriptor.equals("Lio/github/freehij/loader/annotation/EditClass;")) {
                return new ValueCollector(values -> {
                    Object raw = values.get("value");
                    if (raw instanceof String) {
                        editClassValues = new String[]{(String) raw};
                    } else if (raw instanceof Object[] arr) {
                        String[] sa = new String[arr.length];
                        for (int i = 0; i < arr.length; i++) sa[i] = (String) arr[i];
                        editClassValues = sa;
                    }
                });
            }
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String annDesc, boolean visible) {
                    if (annDesc.equals("Lio/github/freehij/loader/annotation/Inject;")) {
                        return new ValueCollector(values -> injectAnnotations.put(name, new AnnotationData(annDesc, values)));
                    }
                    return null;
                }
            };
        }

        ClassData getData() {
            return new ClassData(editClassValues, injectAnnotations);
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
                public void visit(String ignored, Object value) {
                    list.add(value);
                }

                @Override
                public void visitEnum(String ignored, String descriptor, String value) {
                    list.add(new EnumPlaceholder(descriptor, value));
                }

                @Override
                public AnnotationVisitor visitAnnotation(String ignored, String descriptor) {
                    return new ValueCollector(nested -> list.add(new AnnotationData(descriptor, nested)));
                }

                @Override public void visitEnd() {
                    values.put(name, list.toArray());
                }
            };
        }

        @Override
        public void visitEnd() {
            onFinish.accept(values);
        }
    }

    record ClassData(String[] editClassTarget, Map<String, AnnotationData> injectAnnotations) { }

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

        AnnotationInvocationHandler(Class<? extends Annotation> annotationType, AnnotationData data, ClassLoader loader) {
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
            if (raw instanceof String && expectedType == String[].class) {
                return new String[]{(String) raw};
            }
            if (!raw.getClass().isArray()) {
                if (!(raw instanceof AnnotationData) && !(raw instanceof EnumPlaceholder)) {
                    return raw;
                }
                if (raw instanceof AnnotationData nested) {
                    String className = nested.descriptor.substring(1, nested.descriptor.length() - 1).replace('/', '.');
                    try {
                        Class<? extends Annotation> nestedType = (Class<? extends Annotation>) Class.forName(className,
                                false, loader);
                        return createAnnotationProxy(nestedType, nested, loader);
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException("Annotation class not found: " + className, e);
                    }
                }
                if (raw instanceof EnumPlaceholder) {
                    return ((EnumPlaceholder) raw).toEnum(loader);
                }
            }
            int len = Array.getLength(raw);
            Object resolvedArray = Array.newInstance(expectedType.getComponentType(), len);
            for (int i = 0; i < len; i++) {
                Array.set(resolvedArray, i, resolve(Array.get(raw, i), expectedType.getComponentType()));
            }
            return resolvedArray;
        }

        boolean hasComplexElements(Object array) {
            for (int i = 0; i < Array.getLength(array); i++) {
                Object e = Array.get(array, i);
                if (e instanceof AnnotationData || e instanceof EnumPlaceholder) return true;
            }
            return false;
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
            if (val.getClass().isArray()) {
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

        ParsedClass(String[] editClassTarget, List<ParsedMethod> methods) {
            this.editClassTarget = editClassTarget;
            this.methods = methods;
        }
    }

    public static class ParsedMethod {
        public final String name;
        public final Inject inject;

        ParsedMethod(String name, Inject inject) {
            this.name = name;
            this.inject = inject;
        }
    }
}