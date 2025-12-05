package io.github.freehij.injections;

import io.github.freehij.loader.Loader;
import io.github.freehij.loader.annotation.EditClass;
import io.github.freehij.loader.annotation.Inject;
import io.github.freehij.loader.util.InjectionHelper;

import java.net.URL;
import java.nio.file.Path;
import java.util.Collection;

@EditClass("net/fabricmc/loader/impl/game/minecraft/MinecraftGameProvider")
public class KnotClassPathFixer {
    @Inject(method = "initialize", descriptor = "(Lnet/fabricmc/loader/impl/launch/FabricLauncher;)V")
    public static void initialize(InjectionHelper helper) throws Exception {
        Collection<Path> validParentClassPath =
                (Collection<Path>) helper.getReflector().getField("validParentClassPath").get();
        validParentClassPath.add(Path.of(Loader.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
        for (URL url : Loader.getModUrls()) {
            validParentClassPath.add(Path.of(url.toURI()));
        }
    }
}