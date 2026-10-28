package io.github.freehij.loader.annotation;

import io.github.freehij.loader.annotation.notes.Experimental;
import io.github.freehij.loader.annotation.notes.PrioritizedParameter;
import io.github.freehij.loader.constant.AccessMode;
import io.github.freehij.loader.constant.FailStrategy;
import io.github.freehij.loader.constant.FinalMode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Experimental
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface FieldFlags {
    Field[] value();

    @Retention(RetentionPolicy.CLASS)
    @Target({})
    @interface Field {
        @PrioritizedParameter
        String owner() default "";
        Class<?> ownerClazz() default NotSpecified.class;
        String[] names();
        AccessMode access() default AccessMode.KEEP;
        FinalMode finalMode() default FinalMode.NON_FINAL;
        FailStrategy failStrategy() default FailStrategy.NOTIFY;
    }
}
