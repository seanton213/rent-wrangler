package com.rentwrangler.persistence;

import com.rentwrangler.domain.entity.MaintenanceRequest;
import com.rentwrangler.domain.enums.MaintenanceCategory;
import com.rentwrangler.domain.enums.MaintenancePriority;
import com.rentwrangler.domain.enums.MaintenanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface MaintenancePersistenceService {

    MaintenanceRequest save(MaintenanceRequest request);

    Optional<MaintenanceRequest> findById(Long id);

    Page<MaintenanceRequest> findAll(Pageable pageable);

    Page<MaintenanceRequest> findByUnitId(Long unitId, Pageable pageable);

    Page<MaintenanceRequest> findByTenantId(Long tenantId, Pageable pageable);

    Page<MaintenanceRequest> findByStatus(MaintenanceStatus status, Pageable pageable);

    Page<MaintenanceRequest> findByCategory(MaintenanceCategory category, Pageable pageable);

    /** Active (open/assigned/in-progress) tickets for all units of a property. */
    Page<MaintenanceRequest> findActiveByPropertyId(Long propertyId, Pageable pageable);

    /**
     * Complex filter with optional criteria, implemented via {@code CriteriaBuilder}
     * in the persistence layer so callers don't need to construct query predicates.
     */
    Page<MaintenanceRequest> findByFilters(
            Long propertyId,
            MaintenanceStatus status,
            MaintenanceCategory category,
            MaintenancePriority priority,
            Pageable pageable);

    void deleteById(Long id);
}
