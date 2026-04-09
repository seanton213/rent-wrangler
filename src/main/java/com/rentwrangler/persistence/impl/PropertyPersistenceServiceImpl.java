package com.rentwrangler.persistence.impl;

import com.rentwrangler.config.CacheConfig;
import com.rentwrangler.domain.entity.Property;
import com.rentwrangler.domain.enums.PropertyStatus;
import com.rentwrangler.domain.enums.PropertyType;
import com.rentwrangler.domain.enums.UnitStatus;
import com.rentwrangler.exception.ResourceNotFoundException;
import com.rentwrangler.persistence.PropertyPersistenceService;
import com.rentwrangler.repository.PropertyRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PropertyPersistenceServiceImpl implements PropertyPersistenceService {

    private final PropertyRepository propertyRepository;

    /**
     * EntityManager injected via {@code @PersistenceContext} — used for
     * complex Criteria API queries that are not expressible as derived
     * repository methods.
     */
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Evicts the cached entry for this property so the next {@link #findById} call
     * fetches fresh data. We evict rather than {@code @CachePut} because {@code save}
     * returns {@code Property} while {@code findById} caches {@code Optional<Property>};
     * eviction avoids the type mismatch and ensures the cache is always populated by
     * a canonical read path.
     */
    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_PROPERTIES, key = "#result.id")
    public Property save(Property property) {
        return propertyRepository.save(property);
    }

    @Override
    @Cacheable(value = CacheConfig.CACHE_PROPERTIES, key = "#id")
    public Optional<Property> findById(Long id) {
        return propertyRepository.findById(id);
    }

    @Override
    public Page<Property> findAll(Pageable pageable) {
        return propertyRepository.findAll(pageable);
    }

    @Override
    public Page<Property> findByStatus(PropertyStatus status, Pageable pageable) {
        return propertyRepository.findByStatus(status, pageable);
    }

    @Override
    public Page<Property> findByTypeAndStatus(PropertyType type, PropertyStatus status, Pageable pageable) {
        return propertyRepository.findByStatusAndPropertyType(status, type, pageable);
    }

    /**
     * Returns properties that have at least one VACANT unit.
     *
     * <p>Results are cached because this query executes a DISTINCT cross-join on three
     * tables and is called by the vacancy dashboard on every page load. A 5-minute
     * TTL provides reasonable freshness without requiring explicit eviction hooks into
     * the unit status lifecycle (see {@link CacheConfig#CACHE_VACANT_PROPERTIES}).
     */
    @Override
    @Cacheable(value = CacheConfig.CACHE_VACANT_PROPERTIES,
               key = "#pageable.pageNumber + '-' + #pageable.pageSize")
    public Page<Property> findWithVacantUnits(Pageable pageable) {
        String jpql = """
                SELECT DISTINCT p FROM Property p
                JOIN p.units u
                WHERE u.status = :status
                ORDER BY p.name ASC
                """;

        List<Property> results = entityManager.createQuery(jpql, Property.class)
                .setParameter("status", UnitStatus.VACANT)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        Long total = entityManager.createQuery(
                "SELECT COUNT(DISTINCT p) FROM Property p JOIN p.units u WHERE u.status = :status",
                Long.class)
                .setParameter("status", UnitStatus.VACANT)
                .getSingleResult();

        return new PageImpl<>(results, pageable, total);
    }

    @Override
    public boolean existsByAddress(String streetAddress, String city, String state) {
        return propertyRepository.existsByStreetAddressAndCityAndState(streetAddress, city, state);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.CACHE_PROPERTIES,        key  = "#id"),
            @CacheEvict(value = CacheConfig.CACHE_VACANT_PROPERTIES,  allEntries = true)
    })
    public void deleteById(Long id) {
        if (!propertyRepository.existsById(id)) {
            throw new ResourceNotFoundException("Property", id);
        }
        propertyRepository.deleteById(id);
    }
}
