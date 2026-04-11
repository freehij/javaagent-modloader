package io.github.freehij.injections;

import io.github.freehij.loader.Loader;
import io.github.freehij.loader.annotation.EditClass;
import io.github.freehij.loader.annotation.Inject;
import io.github.freehij.loader.constant.At;
import io.github.freehij.loader.util.InjectionHelper;
import io.github.freehij.loader.util.Logger;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.jarcontents.JarFileContents;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.neoforgespi.language.IConfigurable;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import net.neoforged.neoforgespi.locating.ModFileInfoParser;

import java.net.URL;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@SuppressWarnings({"unchecked", "deprecation"})
@EditClass("net/neoforged/fml/loading/LoadingModList")
public class FancyMLPathFixer {
    @Inject(at = At.RETURN)
    public static void init(InjectionHelper helper) throws Exception {
        Set<IModFile> allModFiles = (Set<IModFile>) helper.getReflector().getField("allModFiles").get();
        for (URL modUrl : Loader.getModUrls()) {
            Path modPath = Path.of(modUrl.toURI());
            JarContents jarContents = new JarFileContents(modPath);
            ModFileInfoParser parser = parser();
            IModFile modFile = new ModFile(jarContents, parser, ModFileDiscoveryAttributes.DEFAULT);
            allModFiles.add(modFile);
            Logger.debug("Injected mod file: " + modPath.getFileName(), FancyMLPathFixer.class.getName());
        }

        CodeSource cs = Loader.class.getProtectionDomain().getCodeSource();
        if (cs != null) {
            Path loaderPath = Path.of(cs.getLocation().toURI());
            JarContents loaderJarContents = new JarFileContents(loaderPath);
            IModFile loaderModFile = new ModFile(loaderJarContents, parser(), ModFileDiscoveryAttributes.DEFAULT);
            allModFiles.add(loaderModFile);
            Logger.debug("Injected loader file: " + loaderPath.getFileName(), FancyMLPathFixer.class.getName());
        }
    }

    @Inject(method = "getAllModFiles")
    public static void getAllModFiles(InjectionHelper helper) {
        // shows that allModFiles actually has mods and loader jars inside
        System.out.println(helper.getReflector().getField("allModFiles").get());
    }

    public static ModFileInfoParser parser() {
        return (c) -> new IModFileInfo() {
            final IConfigurable config = new IConfigurable() {
                @Override
                public <T> Optional<T> getConfigElement(String... strings) {
                    return Optional.empty();
                }

                @Override
                public List<? extends IConfigurable> getConfigList(String... strings) {
                    return List.of();
                }
            };

            @Override
            public List<IModInfo> getMods() {
                return List.of();
            }

            @Override
            public List<LanguageSpec> requiredLanguageLoaders() {
                return List.of();
            }

            @Override
            public boolean showAsResourcePack() {
                return false;
            }

            @Override
            public boolean showAsDataPack() {
                return false;
            }

            @Override
            public Map<String, Object> getFileProperties() {
                return Map.of();
            }

            @Override
            public String getLicense() {
                return "";
            }

            @Override
            public String versionString() {
                return "";
            }

            @Override
            public List<String> usesServices() {
                return List.of();
            }

            @Override
            public IModFile getFile() {
                return null;
            }

            @Override
            public IConfigurable getConfig() {
                return config;
            }
        };
    }
}
