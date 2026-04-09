package com.rentwrangler.persistence.impl;

import com.rentwrangler.config.CacheConfig;
import com.rentwrangler.domain.entity.Tenant;
import com.rentwrangler.exception.ResourceNotFoundException;
import com.rentwrangler.persistence.TenantPersistenceService;
import com.rentwrangler.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantPersistenceServiceImpl implements TenantPersistenceService {

    private final TenantRepository tenantRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_TENANTS, key = "#result.id")
    public Tenant save(Tenant tenant) {
        return tenantRepository.save(tenant);
    }

    @Override
    @Cacheable(value = CacheConfig.CACHE_TENANTS, key = "#id")
    public Optional<Tenant> findById(Long id) {
        return tenantRepository.findById(id);
    }

    @Override
    public Optional<Tenant> findByEmail(String email) {
        return tenantRepository.findByEmail(email);
    }

    @Override
    public Page<Tenant> findAll(Pageable pageable) {
        return tenantRepository.findAll(pageable);
    }

    @Override
    public Page<Tenant> search(String query, Pageable pageable) {
        return tenantRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
                query, query, pageable);
    }

    /**
     * Returns tenants with at least one ACTIVE lease. Uses EntityManager
     * to join across the Lease→Unit→Tenant relationship in a single query
     * without loading all tenants into memory.
     */
    @Override
    public Page<Tenant> findActiveTenants(Pageable pageable) {
        String jpql = """
                SELECT DISTINCT t FROM Tenant t
                JOIN Lease l ON l.tenant.id = t.id
                WHERE l.status = 'ACTIVE'
                """;

        List<Tenant> results = entityManager.createQuery(jpql, Tenant.class)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        Long total = entityManager.createQuery(
                "SELECT COUNT(DISTINCT t) FROM Tenant t JOIN Lease l ON l.tenant.id = t.id WHERE l.status = 'ACTIVE'",
                Long.class).getSingleResult();

        return new PageImpl<>(results, pageable, total);
    }

    /**
     * Tenants whose active lease ends within the next {@code daysAhead} days —
     * drives the renewal notification workflow.
     */
    @Override
    public Page<Tenant> findWithExpiringLeases(int daysAhead, Pageable pageable) {
        LocalDate cutoff = LocalDate.now().plusDays(daysAhead);

        String jpql = """
                SELECT DISTINCT t FROM Tenant t
                JOIN Lease l ON l.tenant.id = t.id
                WHERE l.status = 'ACTIVE'
                  AND l.endDate <= :cutoff
                ORDER BY t.lastName, t.firstName
                """;

        List<Tenant> results = entityManager.createQuery(jpql, Tenant.class)
                .setParameter("cutoff", cutoff)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        Long total = entityManager.createQuery(
                "SELECT COUNT(DISTINCT t) FROM Tenant t JOIN Lease l ON l.tenant.id = t.id " +
                "WHERE l.status = 'ACTIVE' AND l.endDate <= :cutoff", Long.class)
                .setParameter("cutoff", cutoff)
                .getSingleResult();

        return new PageImpl<>(results, pageable, total);
    }

    @Override
    public boolean existsByEmail(String email) {
        return tenantRepository.existsByEmail(email);
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_TENANTS, key = "#id")
    public void deleteById(Long id) {
        if (!tenantRepository.existsById(id)) {
            throw new ResourceNotFoundException("Tenant", id);
        }
        tenantRepository.deleteById(id);
    }
}
