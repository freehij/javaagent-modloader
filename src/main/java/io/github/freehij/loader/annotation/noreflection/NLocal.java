package io.github.freehij.loader.annotation.noreflection;

import io.github.freehij.loader.annotation.NotSpecified;
import io.github.freehij.loader.annotation.notes.PrioritizedParameter;
import io.github.freehij.loader.constant.FailStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface NLocal {
    int index();
    @PrioritizedParameter
    String typeString() default "";
    Class<?> typeClass() default NotSpecified.class;
    FailStrategy failStrategy() default FailStrategy.HARD;
}
