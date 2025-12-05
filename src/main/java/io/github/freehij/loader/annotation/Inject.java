package io.github.freehij.loader.annotation;

import io.github.freehij.loader.constant.At;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Inject {
    String method() default "<init>";
    String descriptor() default "()V";
    At at() default At.HEAD;
}
