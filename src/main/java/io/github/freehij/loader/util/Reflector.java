package io.github.freehij.loader.util;

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