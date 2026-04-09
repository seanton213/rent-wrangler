package com.rentwrangler.persistence.impl;

import com.rentwrangler.config.CacheConfig;
import com.rentwrangler.domain.entity.Lease;
import com.rentwrangler.domain.enums.LeaseStatus;
import com.rentwrangler.exception.ResourceNotFoundException;
import com.rentwrangler.persistence.LeasePersistenceService;
import com.rentwrangler.repository.LeaseRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeasePersistenceServiceImpl implements LeasePersistenceService {

    private final LeaseRepository leaseRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Evicts the unit-lease-status and active-lease caches on any lease save.
     *
     * <p>{@code allEntries = true} is used here because accessing {@code lease.unit.id}
     * in a SpEL cache key expression would risk triggering a Hibernate lazy-load outside
     * the transaction boundary. Clearing all entries for these small caches is safe
     * and avoids that risk entirely.
     */
    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.CACHE_UNIT_LEASE_STATUS, allEntries = true),
            @CacheEvict(value = CacheConfig.CACHE_ACTIVE_LEASE,      allEntries = true)
    })
    public Lease save(Lease lease) {
        return leaseRepository.save(lease);
    }

    @Override
    public Optional<Lease> findById(Long id) {
        return leaseRepository.findById(id);
    }

    @Override
    public Page<Lease> findAll(Pageable pageable) {
        return leaseRepository.findAll(pageable);
    }

    @Override
    public Page<Lease> findByTenantId(Long tenantId, Pageable pageable) {
        return leaseRepository.findByTenantId(tenantId, pageable);
    }

    @Override
    public Page<Lease> findByUnitId(Long unitId, Pageable pageable) {
        return leaseRepository.findByUnitId(unitId, pageable);
    }

    @Override
    public Page<Lease> findByStatus(LeaseStatus status, Pageable pageable) {
        return leaseRepository.findByStatus(status, pageable);
    }

    @Override
    public Page<Lease> findByPropertyId(Long propertyId, LeaseStatus status, Pageable pageable) {
        return leaseRepository.findByPropertyIdAndStatus(propertyId, status, pageable);
    }

    @Override
    @Cacheable(value = CacheConfig.CACHE_ACTIVE_LEASE, key = "#unitId")
    public Optional<Lease> findActiveLeaseForUnit(Long unitId) {
        return leaseRepository.findByUnitIdAndStatus(unitId, LeaseStatus.ACTIVE);
    }

    /**
     * Cached boolean — this is the hot path checked on every lease creation attempt.
     * Cache entry is evicted by {@link #save} and {@link #deleteById}.
     */
    @Override
    @Cacheable(value = CacheConfig.CACHE_UNIT_LEASE_STATUS, key = "#unitId")
    public boolean hasActiveLeaseForUnit(Long unitId) {
        return leaseRepository.existsByUnitIdAndStatus(unitId, LeaseStatus.ACTIVE);
    }

    @Override
    public List<Lease> findExpiringBetween(LocalDate from, LocalDate to) {
        return leaseRepository.findExpiringBetween(from, to);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.CACHE_UNIT_LEASE_STATUS, allEntries = true),
            @CacheEvict(value = CacheConfig.CACHE_ACTIVE_LEASE,      allEntries = true)
    })
    public void deleteById(Long id) {
        if (!leaseRepository.existsById(id)) {
            throw new ResourceNotFoundException("Lease", id);
        }
        leaseRepository.deleteById(id);
    }
}
