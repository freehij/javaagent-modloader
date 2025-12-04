package met.freehij.injections;

import met.freehij.loader.Loader;
import met.freehij.loader.annotation.EditClass;
import met.freehij.loader.annotation.Inject;
import met.freehij.loader.util.InjectionHelper;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collection;

//knot loader support
//you can sefely remove this if you are not planning to run this with fabric
@EditClass("net/fabricmc/loader/impl/game/minecraft/MinecraftGameProvider")
public class TestInjection2 {
    @Inject(method = "initialize", descriptor = "(Lnet/fabricmc/loader/impl/launch/FabricLauncher;)V")
    public static void test(InjectionHelper helper) throws URISyntaxException {
        Collection<Path> validParentClassPath =
                (Collection<Path>) helper.getReflector().getField("validParentClassPath").get();
        validParentClassPath.add(Path.of(Loader.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
    }
}