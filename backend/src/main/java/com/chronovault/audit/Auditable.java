package com.chronovault.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    String action() default "";
    String changeType() default "USER_ACTION";
    /** Resource type: SERVER, SNAPSHOT, STORAGE, ALERT, USER, etc. */
    String resourceType() default "";
    /** SpEL expression to extract resource ID from method parameters, e.g. "#result.id" or "#id" */
    String resourceId() default "";
}