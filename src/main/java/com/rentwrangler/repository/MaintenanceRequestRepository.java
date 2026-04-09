package com.rentwrangler.repository;

import com.rentwrangler.domain.entity.MaintenanceRequest;
import com.rentwrangler.domain.enums.MaintenanceCategory;
import com.rentwrangler.domain.enums.MaintenancePriority;
import com.rentwrangler.domain.enums.MaintenanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, Long> {

    Page<MaintenanceRequest> findByUnitId(Long unitId, Pageable pageable);

    Page<MaintenanceRequest> findByTenantId(Long tenantId, Pageable pageable);

    Page<MaintenanceRequest> findByStatus(MaintenanceStatus status, Pageable pageable);

    Page<MaintenanceRequest> findByCategory(MaintenanceCategory category, Pageable pageable);

    Page<MaintenanceRequest> findByPriority(MaintenancePriority priority, Pageable pageable);

    Page<MaintenanceRequest> findByStatusAndPriority(
            MaintenanceStatus status, MaintenancePriority priority, Pageable pageable);

    /** All open/assigned tickets for units belonging to a given property. */
    @Query("SELECT m FROM MaintenanceRequest m JOIN m.unit u " +
           "WHERE u.property.id = :propertyId AND m.status IN ('OPEN','ASSIGNED','IN_PROGRESS')")
    Page<MaintenanceRequest> findActiveByPropertyId(
            @Param("propertyId") Long propertyId, Pageable pageable);

    long countByStatusAndPriority(MaintenanceStatus status, MaintenancePriority priority);
}
