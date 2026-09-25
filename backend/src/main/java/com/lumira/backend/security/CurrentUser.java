package com.lumira.backend.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the currently authenticated {@link AuthenticatedUser} or {@link java.util.UUID} into controller handler methods.
 *
 * <p>Set {@code required = false} if authentication is optional for the endpoint.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {
    boolean required() default true;
}
