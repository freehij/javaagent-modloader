package io.github.freehij.loader.annotation;

import io.github.freehij.loader.constant.FailStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Definalize {
    Field[] value();

    @Retention(RetentionPolicy.CLASS)
    @Target({})
    @interface Field {
        String owner();
        String[] names();
        FailStrategy failStrategy() default FailStrategy.NOTIFY;
    }
}
