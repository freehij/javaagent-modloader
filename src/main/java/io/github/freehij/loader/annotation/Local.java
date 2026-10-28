package io.github.freehij.loader.annotation;

import io.github.freehij.loader.annotation.notes.PrioritizedParameter;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface Local {
    int index();
    @PrioritizedParameter
    String type() default "";
    Class<?> typeClass() default NotSpecified.class;
}
