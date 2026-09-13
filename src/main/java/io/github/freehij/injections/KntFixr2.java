package io.github.freehij.injections;

import io.github.freehij.loader.annotation.EditClass;
import io.github.freehij.loader.annotation.Inject;
import io.github.freehij.loader.util.InjectionHelper;

//cnt fixer 2
@EditClass("net/fabricmc/loader/impl/launch/knot/KnotClassDelegate")
public class KntFixr2 {
    @Inject(method = "loadClass")
    public static void loadClass(InjectionHelper helper) throws Exception {
        String className = (String) helper.getArgs()[0];
        if (className.startsWith("io.github.freehij.loader")) {
            helper.setReturnValue(ClassLoader.getSystemClassLoader().loadClass(className));
            helper.setCancelled(true);
        }
    }
}
