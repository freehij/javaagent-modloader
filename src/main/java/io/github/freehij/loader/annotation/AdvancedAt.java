package io.github.freehij.loader.annotation;

import io.github.freehij.loader.annotation.notes.Experimental;

@Experimental("Early version. Might significantly change in future updates.")
public @interface AdvancedAt {
    enum At {
        ASSIGN_LOCAL
    }

    At at();
    int ordinal() default -1;
}
