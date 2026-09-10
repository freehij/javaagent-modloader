package io.github.freehij.injections;

import io.github.freehij.loader.Loader;
import io.github.freehij.loader.annotation.EditClass;
import io.github.freehij.loader.annotation.Inject;
import io.github.freehij.loader.constant.ArgMode;
import io.github.freehij.loader.constant.At;
import io.github.freehij.loader.util.InjectionHelper;
import io.github.freehij.loader.util.Logger;

import java.lang.module.ModuleDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
@EditClass("net/neoforged/fml/loading/FMLLoader")
public class FancyMLClassPathFixer {
    static Constructor<?> jarContentsModuleCtor;
    static Method jarContentsOfPathsMethod;
    static boolean reflectionFailed;

    @Inject(
            method = "buildTransformingLoader",
            descriptor = "(Lnet/neoforged/fml/classloading/transformation/ClassProcessorSet;" +
                    "Lnet/neoforged/fml/classloading/transformation/ClassProcessorAuditLog;" +
                    "Ljava/util/List;)" +
                    "Lnet/neoforged/fml/classloading/transformation/TransformingClassLoader;",
            at = At.HEAD,
            argMode = ArgMode.FETCH
    )
    public static void buildTransformingLoader(InjectionHelper helper) {
        if (reflectionFailed) return;
        if (jarContentsModuleCtor == null) {
            try {
                Class<?> jarContentsClass = Class.forName("net.neoforged.fml.jarcontents.JarContents");
                Class<?> jarContentsModuleClass = Class.forName("net.neoforged.fml.classloading.JarContentsModule");
                jarContentsOfPathsMethod = jarContentsClass.getMethod("ofPaths", Collection.class);
                jarContentsModuleCtor = jarContentsModuleClass
                        .getDeclaredConstructor(jarContentsClass, ModuleDescriptor.class);
                jarContentsModuleCtor.setAccessible(true);
            } catch (ReflectiveOperationException e) {
                reflectionFailed = true;
                Logger.debug("NeoForge classpath fixer: reflection setup failed: " + e,
                        FancyMLClassPathFixer.class.getName());
                return;
            }
        }
        Set<String> usedNames = new HashSet<>();
        int added = 0;
        for (URL url : Loader.getModUrls()) {
            try {
                Path jarPath = Path.of(url.toURI());
                if (!Files.isRegularFile(jarPath)) continue;
                ModuleDescriptor descriptor = buildDescriptor(jarPath, usedNames);
                if (descriptor == null) continue;
                Object jarContents = jarContentsOfPathsMethod.invoke(null, List.of(jarPath));
                ((List) helper.getArgs()[2]).add(jarContentsModuleCtor.newInstance(jarContents, descriptor));
                added++;
                Logger.debug("NeoForge classpath fixer: injected module " + descriptor.name(),
                        FancyMLClassPathFixer.class.getName());
            } catch (Exception e) {
                Logger.debug("NeoForge classpath fixer: failed to inject " + url + ": " + e,
                        FancyMLClassPathFixer.class.getName());
            }
        }
        Logger.debug("NeoForge classpath fixer: injected " + added + " module(s) into game layer",
                FancyMLClassPathFixer.class.getName());
    }

    static ModuleDescriptor buildDescriptor(Path jarPath, Set<String> usedNames) throws Exception {
        String name = null;
        Set<String> packages = new HashSet<>();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var manifest = jar.getManifest();
            if (manifest != null) {
                String explicit = manifest.getMainAttributes().getValue("Automatic-Module-Name");
                if (explicit != null && !explicit.isBlank() && isLegalModuleName(explicit)) {
                    name = explicit.trim();
                }
            }
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (entry.isDirectory() || entryName.startsWith("META-INF/")) continue;
                if (entryName.equals("module-info.class")) continue;
                int lastSlash = entryName.lastIndexOf('/');
                if (lastSlash <= 0) continue;
                packages.add(entryName.substring(0, lastSlash).replace('/', '.'));
            }
        }
        if (name == null) name = sanitizeFileName(jarPath.getFileName().toString());
        String base = name;
        int suffix = 2;
        while (!usedNames.add(name)) name = base + "_" + suffix++;
        if (packages.isEmpty()) {
            Logger.debug("NeoForge classpath fixer: " + jarPath + " contains no packages, skipping",
                    FancyMLClassPathFixer.class.getName());
            return null;
        }
        return ModuleDescriptor.newAutomaticModule(name).packages(packages).build();
    }

    static String sanitizeFileName(String fileName) {
        if (fileName.endsWith(".jar")) fileName = fileName.substring(0, fileName.length() - 4);
        if (fileName.endsWith(".zip")) fileName = fileName.substring(0, fileName.length() - 4);
        StringBuilder out = new StringBuilder();
        for (String seg : fileName.split("[^A-Za-z0-9]+")) {
            if (seg.isEmpty()) continue;
            if (Character.isDigit(seg.charAt(0))) seg = "_" + seg;
            if (!out.isEmpty()) out.append('.');
            out.append(seg);
        }
        if (out.isEmpty()) out.append("mod_").append(Integer.toHexString(fileName.hashCode()));
        return out.toString();
    }

    static boolean isLegalModuleName(String name) {
        if (name.isEmpty()) return false;
        for (String seg : name.split("\\.", -1)) {
            if (seg.isEmpty()) return false;
            if (!Character.isJavaIdentifierStart(seg.charAt(0))) return false;
            for (int i = 1; i < seg.length(); i++) {
                if (!Character.isJavaIdentifierPart(seg.charAt(i))) return false;
            }
        }
        return true;
    }
}