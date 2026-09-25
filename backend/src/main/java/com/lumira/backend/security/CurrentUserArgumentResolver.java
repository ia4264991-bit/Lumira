package com.lumira.backend.security;

import com.lumira.backend.common.error.UnauthorizedException;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves {@link CurrentUser} annotated parameters in controller endpoints.
 */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class) &&
                (parameter.getParameterType().equals(AuthenticatedUser.class) ||
                 parameter.getParameterType().equals(UUID.class));
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        CurrentUser annotation = parameter.getParameterAnnotation(CurrentUser.class);
        boolean required = annotation == null || annotation.required();

        Optional<AuthenticatedUser> currentUser = SecurityContext.getCurrentUser();

        if (currentUser.isEmpty()) {
            if (required) {
                throw new UnauthorizedException("Authentication required to access this endpoint");
            }
            return null;
        }

        AuthenticatedUser user = currentUser.get();
        if (parameter.getParameterType().equals(UUID.class)) {
            return user.userId();
        }

        return user;
    }
}
