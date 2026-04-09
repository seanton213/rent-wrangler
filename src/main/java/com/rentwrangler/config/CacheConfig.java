package com.rentwrangler.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Caffeine cache configuration.
 *
 * <h3>Why Caffeine?</h3>
 * Caffeine is a high-performance, near-optimal caching library for the JVM. It is
 * the default cache provider recommended by Spring Boot when {@code caffeine} is on
 * the classpath. Compared to the older Guava cache it supersedes, Caffeine offers
 * a better eviction algorithm (Window TinyLFU) and significantly lower overhead.
 *
 * <h3>Cache inventory</h3>
 * <ul>
 *   <li>{@link #CACHE_PROPERTIES} — {@code Optional<Property>} by ID.
 *       Properties are read far more often than written; caching prevents
 *       repeated primary-key lookups on every request.</li>
 *   <li>{@link #CACHE_TENANTS} — {@code Optional<Tenant>} by ID.
 *       Same rationale as properties; tenant records are stable across a session.</li>
 *   <li>{@link #CACHE_UNIT_LEASE_STATUS} — {@code boolean hasActiveLeaseForUnit(unitId)}.
 *       This boolean is checked on every lease-creation call path; caching it
 *       avoids a round-trip to the database on what is already a hot path.</li>
 *   <li>{@link #CACHE_ACTIVE_LEASE} — {@code Optional<Lease> findActiveLeaseForUnit(unitId)}.
 *       The active lease for a unit is requested during lease termination and reporting;
 *       short TTL keeps it fresh without hammering the database.</li>
 *   <li>{@link #CACHE_VACANT_PROPERTIES} — paged results from {@code findWithVacantUnits}.
 *       This query uses a DISTINCT join across three tables; caching paged results
 *       reduces load on the vacancy dashboard without needing explicit eviction hooks
 *       into the unit status lifecycle.</li>
 * </ul>
 *
 * <p>Cache stats ({@code recordStats()}) are exposed via Micrometer through the
 * {@code /actuator/metrics} endpoint once the Micrometer Caffeine integration is active.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_PROPERTIES        = "properties";
    public static final String CACHE_TENANTS           = "tenants";
    public static final String CACHE_UNIT_LEASE_STATUS = "unit-lease-status";
    public static final String CACHE_ACTIVE_LEASE      = "active-lease-for-unit";
    public static final String CACHE_VACANT_PROPERTIES = "vacant-properties";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                build(CACHE_PROPERTIES,        500,  Duration.ofMinutes(15)),
                build(CACHE_TENANTS,           500,  Duration.ofMinutes(15)),
                build(CACHE_UNIT_LEASE_STATUS, 1000, Duration.ofMinutes(5)),
                build(CACHE_ACTIVE_LEASE,      1000, Duration.ofMinutes(5)),
                build(CACHE_VACANT_PROPERTIES, 50,   Duration.ofMinutes(5))
        ));
        return manager;
    }

    private CaffeineCache build(String name, int maxSize, Duration ttl) {
        return new CaffeineCache(name,
                Caffeine.newBuilder()
                        .maximumSize(maxSize)
                        .expireAfterWrite(ttl)
                        .recordStats()
                        .build());
    }
}
