package io.github.freehij.loader.annotation;

import io.github.freehij.loader.annotation.notes.NotImplemented;
import io.github.freehij.loader.annotation.notes.PrioritizedParameter;

@NotImplemented
public @interface AddMethod {
    String name();
    @PrioritizedParameter
    String returnString() default "";
    Class<?> returnClass() default void.class;
    @PrioritizedParameter
    String[] argsString() default { };
    Class<?>[] argsClass() default { };
    boolean isStatic() default false;
}
