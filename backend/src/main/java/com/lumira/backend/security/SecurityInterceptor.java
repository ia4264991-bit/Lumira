package com.lumira.backend.security;

import com.lumira.backend.common.error.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/**
 * Interceptor that parses the Authorization header, resolves the user principal,
 * binds it to {@link SecurityContext}, and enforces authentication where required.
 */
@Component
public class SecurityInterceptor implements HandlerInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenResolver tokenResolver;

    public SecurityInterceptor(TokenResolver tokenResolver) {
        this.tokenResolver = tokenResolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();
            Optional<AuthenticatedUser> user = tokenResolver.resolve(token);
            user.ifPresent(SecurityContext::setCurrentUser);
        }

        if (handler instanceof HandlerMethod handlerMethod) {
            boolean requireAuth = handlerMethod.hasMethodAnnotation(RequireAuth.class) ||
                    handlerMethod.getBeanType().isAnnotationPresent(RequireAuth.class);

            if (requireAuth && SecurityContext.getCurrentUser().isEmpty()) {
                throw new UnauthorizedException("Authentication required to access this endpoint");
            }
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        SecurityContext.clear();
    }
}
