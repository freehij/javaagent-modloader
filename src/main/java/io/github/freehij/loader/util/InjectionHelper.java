package io.github.freehij.loader.util;

public class InjectionHelper {
    final Object instance;
    final Class<?> type;
    final Object[] args, locals;
    boolean cancelled = false;
    Object returnValue;

    public InjectionHelper(Object instance, Class<?> type, Object[] args, Object[] locals) {
        this.instance = instance;
        this.type = type;
        this.args = args;
        this.locals = locals;
    }

    public Object getArg(int index) {
        if (index < 1 || index > args.length) {
            throw new IndexOutOfBoundsException("Invalid argument index: " + index);
        }
        return args[index - 1];
    }

    public static Reflector getReflector(String className) throws ClassNotFoundException {
        Class<?> clazz = Class.forName(className.replace("/", "."));
        return new Reflector(clazz, null);
    }

    public Reflector getReflector() {
        return new Reflector(type, instance);
    }

    public Object getSelf() {
        return instance;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setReturnValue(Object value) {
        this.returnValue = value;
    }

    public Object getReturnValue() {
        return returnValue;
    }

    public Object[] getLocals() {
        return this.locals;
    }

    public static Reflector getMinecraft() {
        try {
            return getReflector("net/minecraft/client/Minecraft").getField("instance");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Could not find Minecraft class", e);
        }
    }
}