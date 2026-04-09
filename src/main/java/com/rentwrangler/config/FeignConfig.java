package com.rentwrangler.config;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Feign HTTP client configuration.
 *
 * <p>Uses OkHttp instead of the default JDK {@code HttpURLConnection} to gain:
 * <ul>
 *   <li>Connection pooling — reuses TCP connections across requests</li>
 *   <li>Keep-alive — avoids repeated TLS handshakes</li>
 *   <li>Configurable per-call timeouts</li>
 * </ul>
 *
 * <p>{@code feign.okhttp.enabled=true} in {@code application.yml} tells Spring
 * Cloud OpenFeign to use this bean instead of the default client.
 */
@Configuration
public class FeignConfig {

    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .writeTimeout(Duration.ofSeconds(10))
                // Pool up to 20 keep-alive connections, evict idle ones after 5 minutes
                .connectionPool(new ConnectionPool(20, 5, TimeUnit.MINUTES))
                .build();
    }
}
