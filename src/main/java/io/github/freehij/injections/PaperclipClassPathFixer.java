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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings({"deprecation"})
@EditClass("io/papermc/paperclip/Paperclip")
public class PaperclipClassPathFixer {
    @Inject(method = "main", descriptor = "([Ljava/lang/String;)V", at = At.NONE, argMode = ArgMode.NONE,
            advancedAt = @AdvancedAt(at = AdvancedAt.At.ASSIGN_LOCAL, ordinal = 1),
            locals = { @Local(index = 1, type = "[Ljava/net/URL;") }, modifyLocals = true)
    public static void main(InjectionHelper helper) {
        List<URL> urls = new ArrayList<>(Arrays.asList((URL[]) helper.getLocals()[0]));
        try {
            CodeSource cs = Loader.class.getProtectionDomain().getCodeSource();
            if (cs != null) urls.add(cs.getLocation());
        } catch (Exception ignored) {}
        urls.addAll(Loader.getModUrls());
        helper.getLocals()[0] = urls.toArray(new URL[0]);
        Logger.debug("Applied Paperclip class path fix", PaperclipClassPathFixer.class.getName());
    }
}
