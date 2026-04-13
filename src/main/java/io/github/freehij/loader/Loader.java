package io.github.freehij.loader;

import io.github.freehij.loader.util.Logger;
import net.lenni0451.classtransform.TransformerManager;
import net.lenni0451.classtransform.utils.tree.BasicClassProvider;

import java.io.*;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Loader {
    static final String VERSION = "a1.0.0";
    static final List<ModInfo> mods = new ArrayList<>();
    static final List<URL> modUrls = new ArrayList<>();

    public static void premain(String args, Instrumentation inst) throws IOException {
        defineMods(true);
        TransformerManager transformerManager = new TransformerManager(new BasicClassProvider());
        transformerManager.addTransformer("io.github.freehij.transformers.KnotClassPathFixer");
        for (ModInfo modInfo : mods) {
            if (modInfo.jarPath != null)
                inst.appendToSystemClassLoaderSearch(new JarFile(modInfo.jarPath.toFile()));
            for (String path : modInfo.injections) {
                transformerManager.addTransformer(path.trim().replace('/', '.'));
            }
        }
        transformerManager.hookInstrumentation(inst);
    }

    static void defineMods(boolean log) {
        mods.add(new ModInfo(
                "loader",
                "Loader",
                VERSION,
                "freehij",
                "Synthetic loader modid for dependency checking.",
                "No license",
                new ArrayList<>(),
                null
        ));
        loadMods();
        if (log) {
            Logger.info("Found mods:", "Loader");
            for (ModInfo mod : mods) {
                Logger.info("	- " + mod.toString(), "Loader");
            }
        }

    }

    static void loadMods() {
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
                                props.getProperty("description", "No description"),
                                props.getProperty("license", "No license"),
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

    public static List<URL> getModUrls() {
        return Collections.unmodifiableList(modUrls);
    }

    public static List<ModInfo> getMods() {
        // TODO: proper fix for evil knot conflicts
        if (mods.isEmpty()) defineMods(false);
        return Collections.unmodifiableList(mods);
    }

    public record ModInfo(String id, String name, String version, String creator,
                          String description, String license, List<String> injections, Path jarPath) {
        @Override
        public String toString() {
            return name + " (" + id + ") " + version + " by " + creator;
        }
    }
}