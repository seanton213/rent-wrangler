package com.rentwrangler.context;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Request-scoped bean that holds contextual metadata for the current HTTP request.
 *
 * <p>A new instance is created by Spring for every inbound request and destroyed
 * when the request completes. Populated by {@link RequestContextInterceptor} after
 * Spring Security has authenticated the caller.
 *
 * <p>Inject this anywhere in the request call-stack to obtain the current user,
 * their roles, or the correlation ID for logging/auditing.
 */
@Component
@RequestScope
@Getter
@Setter
public class RequestContext {

    /** Unique ID for this request; propagated to logs and outbound calls. */
    private String requestId;

    /** Authenticated username from Spring Security. */
    private String username;

    /** Granted authorities for the authenticated user. */
    private List<String> roles = new ArrayList<>();

    /** Wall-clock time when the request was received. */
    private Instant requestTime;

    /** Remote IP address of the caller. */
    private String clientIp;

    public boolean hasRole(String role) {
        return roles.contains("ROLE_" + role) || roles.contains(role);
    }
}
