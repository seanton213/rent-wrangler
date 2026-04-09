package com.rentwrangler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * HTTP security configuration using RFC 7617 HTTP Basic authentication.
 *
 * <p>Roles:
 * <ul>
 *   <li>{@code ADMIN}   — full CRUD access to all resources</li>
 *   <li>{@code MANAGER} — read + create/update; cannot delete tenants or properties</li>
 *   <li>{@code STAFF}   — read-only; can submit and update maintenance requests</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public: OpenAPI docs and actuator health
                .requestMatchers("/swagger-ui/**", "/api-docs/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                // Actuator management endpoints — admin only
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                // Destructive operations — admin only
                .requestMatchers(HttpMethod.DELETE, "/api/v1/**").hasRole("ADMIN")
                // Read operations — any authenticated user
                .requestMatchers(HttpMethod.GET, "/api/v1/**").authenticated()
                // Maintenance requests — staff and above
                .requestMatchers("/api/v1/maintenance/**").hasAnyRole("ADMIN", "MANAGER", "STAFF")
                // Everything else — manager and above
                .anyRequest().hasAnyRole("ADMIN", "MANAGER")
            )
            .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
            User.builder()
                .username("admin")
                .password(encoder.encode("admin123"))
                .roles("ADMIN", "MANAGER", "STAFF")
                .build(),
            User.builder()
                .username("manager")
                .password(encoder.encode("manager123"))
                .roles("MANAGER", "STAFF")
                .build(),
            User.builder()
                .username("staff")
                .password(encoder.encode("staff123"))
                .roles("STAFF")
                .build()
        );
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
