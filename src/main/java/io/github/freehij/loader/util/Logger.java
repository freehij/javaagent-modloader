package io.github.freehij.loader.util;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {
    static final boolean DEBUG = System.getProperty("loader.DEBUG", "").equalsIgnoreCase("true");
    public static SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
    public static final PrintStream STDOUT = new PrintStream(
            new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8
    );
    static final StackWalker WALKER =
            StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    static final String LOGGER_CLASS = Logger.class.getName();

    static Object resolveSrc(Object src) {
        if (src != null) return src;
        return WALKER.walk(frames -> frames
                .filter(f -> !LOGGER_CLASS.equals(f.getClassName()))
                .findFirst()
                .map(f -> (Object) f.getClassName())
                .orElse("Unknown"));
    }

    public static void write(String message, Object src) {
        STDOUT.println("[" + dateFormat.format(new Date()) + "] [" + resolveSrc(src) + "] " + message);
    }

    public static void info(String message, Object src) {
        write(message, src);
    }

    public static void info(String message) {
        write(message, null);
    }

    public static void debug(String message, Object src) {
        if (DEBUG) write(message, src);
    }

    public static void debug(String message) {
        if (DEBUG) write(message, null);
    }
}
