package com.rentwrangler.context;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * MVC interceptor that populates {@link RequestContext} at the start of every
 * request, after Spring Security has processed authentication.
 *
 * <p>The {@code X-Request-ID} header is honoured if present (useful for
 * distributed tracing); otherwise a new UUID is generated.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestContextInterceptor implements HandlerInterceptor {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    private final RequestContext requestContext;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {

        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        requestContext.setRequestId(requestId);
        requestContext.setRequestTime(Instant.now());
        requestContext.setClientIp(request.getRemoteAddr());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            requestContext.setUsername(auth.getName());
            requestContext.setRoles(auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList()));
        }

        // Echo the request ID back so clients can correlate logs
        response.setHeader(REQUEST_ID_HEADER, requestId);

        log.debug("[{}] {} {} — user={}", requestId, request.getMethod(),
                request.getRequestURI(), requestContext.getUsername());

        return true;
    }
}
