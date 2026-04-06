package io.github.freehij.injections;

import io.github.freehij.loader.Loader;
import io.github.freehij.loader.annotation.EditClass;
import io.github.freehij.loader.annotation.Inject;
import io.github.freehij.loader.util.InjectionHelper;
import io.github.freehij.loader.util.Logger;

import java.net.URL;
import java.nio.file.Path;
import java.util.Collection;

@SuppressWarnings("unchecked")
@EditClass("net/fabricmc/loader/impl/game/minecraft/MinecraftGameProvider")
public class KnotClassPathFixer {
    @Inject(method = "locateGame",
            descriptor = "(Lnet/fabricmc/loader/impl/launch/FabricLauncher;[Ljava/lang/String;)Z")
    public static void locateGame(InjectionHelper helper) throws Exception {
        Collection<Path> miscGameLibraries =
                (Collection<Path>) helper.getReflector().getField("miscGameLibraries").get();
        try {
            java.security.CodeSource cs = Loader.class.getProtectionDomain().getCodeSource();
            if (cs != null) miscGameLibraries.add(Path.of(cs.getLocation().toURI()));
        } catch (Exception ignored) {}
        for (URL url : Loader.getModUrls()) miscGameLibraries.add(Path.of(url.toURI()));
        Logger.debug("Applied knot class path fix", KnotClassPathFixer.class);
    }
}