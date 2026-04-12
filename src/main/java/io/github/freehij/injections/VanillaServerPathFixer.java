package io.github.freehij.injections;

import io.github.freehij.loader.Loader;
import io.github.freehij.loader.annotation.AdvancedAt;
import io.github.freehij.loader.annotation.EditClass;
import io.github.freehij.loader.annotation.Inject;
import io.github.freehij.loader.annotation.Local;
import io.github.freehij.loader.constant.ArgMode;
import io.github.freehij.loader.constant.At;
import io.github.freehij.loader.util.InjectionHelper;
import io.github.freehij.loader.util.Logger;

import java.net.URL;
import java.security.CodeSource;
import java.util.List;

@SuppressWarnings({"unchecked", "deprecation"})
@EditClass("net/minecraft/bundler/Main")
public class VanillaServerPathFixer {
    @Inject(method = "run", descriptor = "([Ljava/lang/String;)V", at = At.NONE, argMode = ArgMode.NONE,
            advancedAt = @AdvancedAt(at = AdvancedAt.At.ASSIGN_LOCAL, ordinal = 5),
            locals = { @Local(index = 6, type = "Ljava/util/List;") }, modifyLocals = true)
    public static void run(InjectionHelper helper) {
        List<URL> extractedUrls = (List<URL>) helper.getLocals()[0];
        try {
            CodeSource cs = Loader.class.getProtectionDomain().getCodeSource();
            if (cs != null) extractedUrls.add(cs.getLocation());
        } catch (Exception ignored) {}
        extractedUrls.addAll(Loader.getModUrls());
        Logger.debug("Applied server class path fix", KnotClassPathFixer.class.getName());
    }
}
