package met.freehij.loader.util;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;

public class Reflector {
    private final Class<?> clazz;
    private final Object object;

    public Reflector(Class<?> clazz, Object object) {
        this.clazz = clazz;
        this.object = object;
    }

    public Object get() {
        return object;
    }

    public int getInt() {
        return (int) object;
    }

    public long getLong() {
        return (long) object;
    }

    public float getFloat() {
        return (float) object;
    }

    public double getDouble() {
        return (double) object;
    }

    public byte getByte() {
        return (byte) object;
    }

    public char getChar() {
        return (char) object;
    }

    public short getShort() {
        return (short) object;
    }

    public boolean getBoolean() {
        return (boolean) object;
    }

    public String getString() {
        return (String) object;
    }

    public Class<?> getActualClass() {
        return clazz;
    }

    public Reflector getField(String fieldName) {
        //if this.object is null get a static variable else from an instanced class
        //returns Reflector with field value
        //throws Exception if field is not static and no object instance is presented
        //throws RuntimeException when other errors occur
        try {
            Field field = this.clazz.getDeclaredField(fieldName);
            field.setAccessible(true);

            Object value;
            if (this.object == null) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    throw new Exception("Not a static field: " + fieldName);
                }
                value = field.get(null);
            } else {
                value = field.get(this.object);
            }

            return new Reflector(field.getType(), value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get field: " + fieldName, e);
        }
    }

    public void setField(String fieldName, Object value) {
        //if this.object is null set a static variable else in an instanced class
        //throws Exception if field is not static and no object instance is presented
        //throws RuntimeException when other errors occur
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);

            if (object == null) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    throw new Exception("Not a static field: " + fieldName);
                }
                field.set(null, value);
            } else {
                field.set(object, value);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }

    public Reflector invoke(String methodName, String descriptor, Object... args) {
        //calculate the paramTypes and call invokeRaw
        return this.invokeRaw(methodName, parseDescriptor(descriptor), args);
    }

    public Reflector invokeRaw(String methodName, Class<?>[] paramTypes, Object... args) {
        //should call the method using paramTypes and args
        //if this.object is null call method as static else call from instanced class
        //returns Reflector with methods return
        //throws Exception if method is not static and no object instance is presented
        //throws RuntimeException when other errors occur
        try {
            Method method = clazz.getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);

            Object result;
            if (object == null) {
                if (!Modifier.isStatic(method.getModifiers())) {
                    throw new Exception("Not a static method: " + methodName);
                }
                result = method.invoke(null, args);
            } else {
                result = method.invoke(object, args);
            }

            return new Reflector(method.getReturnType(), result);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke method: " + methodName, e);
        }
    }

    public Reflector newInstance(String descriptor, Object... args) {
        //should calculate the paramTypes and call newInstanceRaw
        return newInstanceRaw(parseDescriptor(descriptor), args);
    }

    public Reflector newInstanceRaw(Class<?>[] paramTypes, Object... args) {
        //create new instance of this.clazz
        //return Reflector with newly created class instance
        //throws RuntimeException when errors occur
        try {
            Constructor<?> constructor = clazz.getDeclaredConstructor(paramTypes);
            constructor.setAccessible(true);
            Object instance = constructor.newInstance(args);
            return new Reflector(clazz, instance);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create new instance", e);
        }
    }

    static Class<?>[] parseDescriptor(String descriptor) {
        List<Class<?>> classes = new ArrayList<>();
        boolean isArray = false;
        for (int i = 0; i < descriptor.length(); i++) {
            switch (descriptor.charAt(i)) {
                case '[':
                    isArray = true;
                    break;
                case 'Z':
                    classes.add(isArray ? boolean[].class : boolean.class);
                    isArray = false;
                    break;
                case 'B':
                    classes.add(isArray ? byte[].class : byte.class);
                    isArray = false;
                    break;
                case 'C':
                    classes.add(isArray ? char[].class : char.class);
                    isArray = false;
                    break;
                case 'S':
                    classes.add(isArray ? short[].class : short.class);
                    isArray = false;
                    break;
                case 'I':
                    classes.add(isArray ? int[].class : int.class);
                    isArray = false;
                    break;
                case 'J':
                    classes.add(isArray ? long[].class : long.class);
                    isArray = false;
                    break;
                case 'F':
                    classes.add(isArray ? float[].class : float.class);
                    isArray = false;
                    break;
                case 'D':
                    classes.add(isArray ? double[].class : double.class);
                    isArray = false;
                    break;
                case 'L':
                    StringBuilder className = new StringBuilder();
                    while (i + 1 < descriptor.length() && descriptor.charAt(i + 1) != ';') {
                        className.append(descriptor.charAt(i + 1));
                        i++;
                    }
                    String clsName = className.toString().replace("/", ".");
                    try {
                        classes.add(isArray ? Array.newInstance(Class.forName(clsName), 0).getClass() : Class.forName(clsName));
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException("Failed to load class: " + clsName, e);
                    }
                    isArray = false;
                    break;
                case ')':
                    return classes.toArray(new Class<?>[0]);
            }
        }
        return classes.toArray(new Class<?>[0]);
    }
}
    /*public Reflector getField(String name) {
        Class<?> current = this.getActualClass();
        String originalName = name;

        while (current != null) {
            String mappedName = FieldMappings.get(current.getName().replace(".", "/"), name);
            if (mappedName != null) {
                name = mappedName;
                break;
            }
            current = current.getSuperclass();
        }
        if (current == null) {
            throw new RuntimeException("No mapping found for " + originalName);
        }

        try {
            HandleCache cache = getHandleCache(current);
            MethodHandle getter = cache.getFieldGetter(name);
            Object value = cache.isFieldStatic(name) ? getter.invoke() : getter.invoke(object);
            return new Reflector(
                    value != null ? value.getClass() : cache.getFieldType(name),
                    value
            );
        } catch (Throwable e) {
            throw new RuntimeException("Failed to get field: " + name, e);
        }
    }

    public Reflector getFieldRaw(String name) {
        try {
            HandleCache cache = getHandleCache(this.getActualClass());
            MethodHandle getter = cache.getFieldGetter(name);
            Object value = cache.isFieldStatic(name) ? getter.invoke() : getter.invoke(object);
            return new Reflector(
                    value != null ? value.getClass() : cache.getFieldType(name),
                    value
            );
        } catch (Throwable e) {
            throw new RuntimeException("Failed to get field: " + name, e);
        }
    }

    public void setField(String name, Object value) {
        Class<?> current = this.getActualClass();
        String originalName = name;

        while (current != null) {
            String mappedName = FieldMappings.get(current.getName().replace(".", "/"), name);
            if (mappedName != null) {
                name = mappedName;
                break;
            }
            current = current.getSuperclass();
        }
        if (current == null) {
            throw new RuntimeException("No mapping found for " + originalName);
        }

        try {
            HandleCache cache = getHandleCache(current);
            MethodHandle setter = cache.getFieldSetter(name);
            if (cache.isFieldStatic(name)) {
                setter.invoke(value);
            } else {
                setter.invoke(object, value);
            }
        } catch (Throwable e) {
            throw new RuntimeException("Failed to set field: " + name, e);
        }
    }

    public void setFieldRaw(String name, Object value) {
        try {
            HandleCache cache = getHandleCache(this.getActualClass());
            MethodHandle setter = cache.getFieldSetter(name);
            if (cache.isFieldStatic(name)) {
                setter.invoke(value);
            } else {
                setter.invoke(object, value);
            }
        } catch (Throwable e) {
            throw new RuntimeException("Failed to set field: " + name, e);
        }
    }

    public Reflector invoke(String methodName, Object... args) {
        Class<?>[] params = new Class<?>[0];
        Class<?> current = this.getActualClass();

        while (current != null) {
            MethodMapping mappedName = MethodMappings.get(current.getName().replace(".", "/"), methodName);
            if (mappedName != null) {
                methodName = mappedName.method;
                if (mappedName.params == null) {
                    try {
                        mappedName.params = parseDescriptor(mappedName.descriptor);
                    } catch (Exception ignored) {}
                }
                params = mappedName.params;
                break;
            }
            current = current.getSuperclass();
        }
        if (current == null) {
            throw new RuntimeException("No mapping found for " + methodName);
        }

        try {
            return this.invokeRaw(methodName, params, args);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    public Reflector invokeRaw(String methodName, Class<?>[] paramTypes, Object... args) {
        try {
            HandleCache cache = getHandleCache(object != null ? object.getClass() : clazz);
            MethodHandle method = cache.getMethod(methodName, paramTypes);
            Object result = cache.isMethodStatic(methodName, paramTypes) ?
                    method.invokeWithArguments(args) :
                    method.invokeWithArguments(prependObject(args));
            return new Reflector(
                    result != null ? result.getClass() : method.type().returnType(),
                    result
            );
        } catch (Throwable e) {
            throw new RuntimeException("Failed to invoke: " + methodName, e);
        }
    }

    public Reflector newInstance(String descriptor, Object... args) {
        try {
            return this.newInstanceRaw(parseDescriptor(descriptor), args);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    public Reflector newInstanceRaw(Class<?>[] paramTypes, Object... args) {
        try {
            HandleCache cache = getHandleCache(this.getActualClass());
            MethodHandle constructor = cache.getConstructor(paramTypes);
            Object instance = constructor.invokeWithArguments(args);
            return new Reflector(instance.getClass(), instance);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to create new instance", e);
        }
    }

    private Object[] prependObject(Object[] args) {
        if (args == null) {
            return new Object[] { object };
        }
        Object[] newArgs = new Object[args.length + 1];
        newArgs[0] = object;
        System.arraycopy(args, 0, newArgs, 1, args.length);
        return newArgs;
    }

    private static HandleCache getHandleCache(Class<?> clazz) {
        synchronized (HANDLE_CACHES) {
            return HANDLE_CACHES.computeIfAbsent(clazz, HandleCache::new);
        }
    }*/

    /*private static class HandleCache {
        private final Class<?> clazz;
        private final Map<String, MethodHandle> fieldGetters = new HashMap<>();
        private final Map<String, MethodHandle> fieldSetters = new HashMap<>();
        private final Map<MethodKey, MethodHandle> methods = new HashMap<>();
        private final Map<ConstructorKey, MethodHandle> constructors = new HashMap<>();
        private final Map<String, Boolean> fieldStaticFlags = new HashMap<>();
        private final Map<String, Class<?>> fieldTypes = new HashMap<>();
        private final Map<MethodKey, Boolean> methodStaticFlags = new HashMap<>();

        HandleCache(Class<?> clazz) {
            this.clazz = clazz;
        }

        MethodHandle getFieldGetter(String name) throws NoSuchFieldException, IllegalAccessException {
            return fieldGetters.computeIfAbsent(name, n -> {
                try {
                    Field field = findField(clazz, n);
                    field.setAccessible(true);
                    fieldStaticFlags.put(n, Modifier.isStatic(field.getModifiers()));
                    fieldTypes.put(n, field.getType());
                    return LOOKUP.unreflectGetter(field);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        MethodHandle getFieldSetter(String name) throws NoSuchFieldException, IllegalAccessException {
            return fieldSetters.computeIfAbsent(name, n -> {
                try {
                    Field field = findField(clazz, n);
                    field.setAccessible(true);
                    fieldStaticFlags.put(n, Modifier.isStatic(field.getModifiers()));
                    fieldTypes.put(n, field.getType());
                    return LOOKUP.unreflectSetter(field);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        MethodHandle getMethod(String name, Class<?>[] paramTypes) throws NoSuchMethodException, IllegalAccessException {
            MethodKey key = new MethodKey(name, paramTypes);
            return methods.computeIfAbsent(key, k -> {
                try {
                    Method method = findMethod(clazz, name, paramTypes);
                    method.setAccessible(true);
                    methodStaticFlags.put(key, Modifier.isStatic(method.getModifiers()));
                    return LOOKUP.unreflect(method);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        MethodHandle getConstructor(Class<?>[] paramTypes) throws NoSuchMethodException, IllegalAccessException {
            ConstructorKey key = new ConstructorKey(paramTypes);
            return constructors.computeIfAbsent(key, k -> {
                try {
                    Constructor<?> constructor = clazz.getDeclaredConstructor(paramTypes);
                    constructor.setAccessible(true);
                    return LOOKUP.unreflectConstructor(constructor);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        boolean isFieldStatic(String name) {
            return fieldStaticFlags.getOrDefault(name, false);
        }

        Class<?> getFieldType(String name) {
            return fieldTypes.get(name);
        }

        boolean isMethodStatic(String name, Class<?>[] paramTypes) {
            return methodStaticFlags.getOrDefault(new MethodKey(name, paramTypes), false);
        }

        private static Field findField(Class<?> searchClass, String name) throws NoSuchFieldException {
            Class<?> current = searchClass;
            while (current != null) {
                try {
                    return current.getDeclaredField(name);
                } catch (NoSuchFieldException e) {
                    current = current.getSuperclass();
                }
            }
            throw new NoSuchFieldException("Field not found: " + name);
        }

        private static Method findMethod(Class<?> searchClass, String name, Class<?>[] paramTypes) throws NoSuchMethodException {
            Class<?> current = searchClass;
            while (current != null) {
                try {
                    return current.getDeclaredMethod(name, paramTypes);
                } catch (NoSuchMethodException e) {
                    current = current.getSuperclass();
                }
            }
            throw new NoSuchMethodException("Method not found: " + name);
        }
    }

    private static class MethodKey {
        private final String name;
        private final Class<?>[] paramTypes;

        MethodKey(String name, Class<?>[] paramTypes) {
            this.name = name;
            this.paramTypes = paramTypes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MethodKey methodKey = (MethodKey) o;
            if (!name.equals(methodKey.name)) return false;
            if (paramTypes.length != methodKey.paramTypes.length) return false;
            for (int i = 0; i < paramTypes.length; i++) {
                if (!paramTypes[i].equals(methodKey.paramTypes[i])) return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            for (Class<?> paramType : paramTypes) {
                result = 31 * result + paramType.hashCode();
            }
            return result;
        }
    }

    private static class ConstructorKey {
        private final Class<?>[] paramTypes;

        ConstructorKey(Class<?>[] paramTypes) {
            this.paramTypes = paramTypes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ConstructorKey that = (ConstructorKey) o;
            if (paramTypes.length != that.paramTypes.length) return false;
            for (int i = 0; i < paramTypes.length; i++) {
                if (!paramTypes[i].equals(that.paramTypes[i])) return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int result = 1;
            for (Class<?> paramType : paramTypes) {
                result = 31 * result + paramType.hashCode();
            }
            return result;
        }
    }*/