package io.github.freehij.transformers;

import io.github.freehij.loader.Loader;
import net.fabricmc.loader.impl.game.minecraft.MinecraftGameProvider;
import net.lenni0451.classtransform.annotations.CShadow;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.List;

@CTransformer(MinecraftGameProvider.class)
public class KnotClassPathFixer {
    @CShadow
    List<Path> miscGameLibraries;

    @CInject(method = "locateGame", target = @CTarget("HEAD"))
    public void locateGame() throws URISyntaxException {
        CodeSource cs = Loader.class.getProtectionDomain().getCodeSource();
        if (cs != null) miscGameLibraries.add(Path.of(cs.getLocation().toURI()));
        for (URL url : Loader.getModUrls()) miscGameLibraries.add(Path.of(url.toURI()));
    }
}
