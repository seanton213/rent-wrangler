package com.rentwrangler.persistence.impl;

import com.rentwrangler.domain.entity.MaintenanceRequest;
import com.rentwrangler.domain.entity.Unit;
import com.rentwrangler.domain.enums.MaintenanceCategory;
import com.rentwrangler.domain.enums.MaintenancePriority;
import com.rentwrangler.domain.enums.MaintenanceStatus;
import com.rentwrangler.exception.ResourceNotFoundException;
import com.rentwrangler.persistence.MaintenancePersistenceService;
import com.rentwrangler.repository.MaintenanceRequestRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
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
public class MaintenancePersistenceServiceImpl implements MaintenancePersistenceService {

    private final MaintenanceRequestRepository maintenanceRequestRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public MaintenanceRequest save(MaintenanceRequest request) {
        return maintenanceRequestRepository.save(request);
    }

    @Override
    public Optional<MaintenanceRequest> findById(Long id) {
        return maintenanceRequestRepository.findById(id);
    }

    @Override
    public Page<MaintenanceRequest> findAll(Pageable pageable) {
        return maintenanceRequestRepository.findAll(pageable);
    }

    @Override
    public Page<MaintenanceRequest> findByUnitId(Long unitId, Pageable pageable) {
        return maintenanceRequestRepository.findByUnitId(unitId, pageable);
    }

    @Override
    public Page<MaintenanceRequest> findByTenantId(Long tenantId, Pageable pageable) {
        return maintenanceRequestRepository.findByTenantId(tenantId, pageable);
    }

    @Override
    public Page<MaintenanceRequest> findByStatus(MaintenanceStatus status, Pageable pageable) {
        return maintenanceRequestRepository.findByStatus(status, pageable);
    }

    @Override
    public Page<MaintenanceRequest> findByCategory(MaintenanceCategory category, Pageable pageable) {
        return maintenanceRequestRepository.findByCategory(category, pageable);
    }

    @Override
    public Page<MaintenanceRequest> findActiveByPropertyId(Long propertyId, Pageable pageable) {
        return maintenanceRequestRepository.findActiveByPropertyId(propertyId, pageable);
    }

    /**
     * Dynamic multi-criteria filter using the JPA Criteria API.
     * All parameters are optional — null values are simply omitted from the WHERE clause.
     * This avoids writing N separate repository methods for every filter combination.
     */
    @Override
    public Page<MaintenanceRequest> findByFilters(
            Long propertyId,
            MaintenanceStatus status,
            MaintenanceCategory category,
            MaintenancePriority priority,
            Pageable pageable) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        // Data query
        CriteriaQuery<MaintenanceRequest> query = cb.createQuery(MaintenanceRequest.class);
        Root<MaintenanceRequest> root = query.from(MaintenanceRequest.class);
        Join<MaintenanceRequest, Unit> unitJoin = root.join("unit", JoinType.INNER);

        List<Predicate> predicates = buildPredicates(cb, root, unitJoin, propertyId, status, category, priority);

        query.where(predicates.toArray(new Predicate[0]))
             .orderBy(cb.desc(root.get("createdAt")));

        List<MaintenanceRequest> results = entityManager.createQuery(query)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        // Count query
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<MaintenanceRequest> countRoot = countQuery.from(MaintenanceRequest.class);
        Join<MaintenanceRequest, Unit> countUnitJoin = countRoot.join("unit", JoinType.INNER);

        List<Predicate> countPredicates = buildPredicates(cb, countRoot, countUnitJoin,
                propertyId, status, category, priority);

        countQuery.select(cb.count(countRoot)).where(countPredicates.toArray(new Predicate[0]));
        Long total = entityManager.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(results, pageable, total);
    }

    private List<Predicate> buildPredicates(
            CriteriaBuilder cb,
            Root<MaintenanceRequest> root,
            Join<MaintenanceRequest, Unit> unitJoin,
            Long propertyId,
            MaintenanceStatus status,
            MaintenanceCategory category,
            MaintenancePriority priority) {

        List<Predicate> predicates = new ArrayList<>();

        if (propertyId != null) {
            predicates.add(cb.equal(unitJoin.get("property").get("id"), propertyId));
        }
        if (status != null) {
            predicates.add(cb.equal(root.get("status"), status));
        }
        if (category != null) {
            predicates.add(cb.equal(root.get("category"), category));
        }
        if (priority != null) {
            predicates.add(cb.equal(root.get("priority"), priority));
        }

        return predicates;
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        if (!maintenanceRequestRepository.existsById(id)) {
            throw new ResourceNotFoundException("MaintenanceRequest", id);
        }
        maintenanceRequestRepository.deleteById(id);
    }
}
