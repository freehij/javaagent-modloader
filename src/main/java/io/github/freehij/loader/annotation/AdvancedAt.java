package io.github.freehij.loader.annotation;

import io.github.freehij.loader.annotation.notes.Experimental;
import io.github.freehij.loader.constant.Shift;

@Experimental
public @interface AdvancedAt {
    enum At {
        ASSIGN_LOCAL,
        ASSIGN_FIELD,
        INVOKE
    }

    At at();
    String optional() default "";
    int ordinal() default -1;
    Shift shift() default Shift.BEFORE;
    // TODO
    // boolean cancelAction() default false;
}
