package io.github.freehij.loader.annotation;

import io.github.freehij.loader.constant.InterfaceOperation;

public @interface InterfaceSetup {
    String[] targetsString() default {};
    Class<?>[] targetsClass() default {};
    InterfaceOperation operation() default InterfaceOperation.ADD;
}
