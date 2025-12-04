package met.freehij.loader.util;

public class InjectionHelper {
    private final Object instance;
    private final Object[] args;
    private boolean cancelled = false;
    private Object returnValue = null;
    public static Reflector minecraft = null;

    static {
        try {
            minecraft = getClazz("net/minecraft/client/Minecraft").getField("instance");
        } catch (ClassNotFoundException e) {
            System.err.println("Could not find Minecraft class");
        }
    }

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
}