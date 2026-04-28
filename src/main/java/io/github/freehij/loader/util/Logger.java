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

    public static void info(String message, Object src) {
        STDOUT.println("[" + dateFormat.format(new Date()) + "] [" + src + "] " + message);
    }

    public static void info(String message) {
        info(message, "Unknown");
    }

    public static void debug(String message, Object src) {
        if (DEBUG) STDOUT.println("[" + dateFormat.format(new Date()) + "] [" + src + "] " + message);
    }

    public static void debug(String message) {
        debug(message, "Unknown");
    }
}
