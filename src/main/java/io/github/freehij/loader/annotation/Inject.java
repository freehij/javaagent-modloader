package io.github.freehij.loader.annotation;

import io.github.freehij.loader.constant.ArgMode;
import io.github.freehij.loader.constant.At;
import io.github.freehij.loader.constant.FailStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Inject {
    String[] method() default "<init>";
    String descriptor() default "";
    @Deprecated
    At at() default At.HEAD;
    Local[] locals() default {};
    boolean modifyLocals() default false;
    AdvancedAt[] advancedAt() default {};
    ArgMode argMode() default ArgMode.FETCH;
    /**
     * Lower number => higher priority.
     */
    int priority() default 500;
    FailStrategy failStrategy() default FailStrategy.NOTIFY;
}
