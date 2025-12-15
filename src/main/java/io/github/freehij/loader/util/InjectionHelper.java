package io.github.freehij.loader.util;

public class InjectionHelper {
    final Object instance;
    final Object[] args;
    boolean cancelled = false;
    Object returnValue = null;

    public InjectionHelper(Object instance, Object[] args) {
        this.instance = instance;
        this.args = args;
    }

    public Object getArg(int index) {
        if (index < 1 || index > args.length) {
            throw new IndexOutOfBoundsException("Invalid argument index: " + index);
        }
        return args[index - 1];
    }

    public static Reflector getClazz(String className) throws ClassNotFoundException {
        Class<?> clazz = Class.forName(className.replace("/", "."));
        return new Reflector(clazz, null);
    }

    //kinda confusing but whatever </3
    public static Reflector getReflector(String className) throws ClassNotFoundException {
        return getClazz(className);
    }

    public Reflector getReflector() {
        return new Reflector(instance.getClass(), instance);
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

    public static Reflector getMinecraft() {
        try {
            return getReflector("net/minecraft/client/Minecraft").getField("instance");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Could not find Minecraft class", e);
        }
    }
}