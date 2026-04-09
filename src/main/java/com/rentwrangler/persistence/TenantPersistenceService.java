package com.rentwrangler.persistence;

import com.rentwrangler.domain.entity.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface TenantPersistenceService {

    Tenant save(Tenant tenant);

    Optional<Tenant> findById(Long id);

    Optional<Tenant> findByEmail(String email);

    Page<Tenant> findAll(Pageable pageable);

    Page<Tenant> search(String query, Pageable pageable);

    /** Tenants who currently hold an active lease on any unit. */
    Page<Tenant> findActiveTenants(Pageable pageable);

    /** Tenants whose leases expire within the next {@code daysAhead} days. */
    Page<Tenant> findWithExpiringLeases(int daysAhead, Pageable pageable);

    boolean existsByEmail(String email);

    void deleteById(Long id);
}
