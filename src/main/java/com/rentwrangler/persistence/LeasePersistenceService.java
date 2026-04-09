package com.rentwrangler.persistence;

import com.rentwrangler.domain.entity.Lease;
import com.rentwrangler.domain.enums.LeaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LeasePersistenceService {

    Lease save(Lease lease);

    Optional<Lease> findById(Long id);

    Page<Lease> findAll(Pageable pageable);

    Page<Lease> findByTenantId(Long tenantId, Pageable pageable);

    Page<Lease> findByUnitId(Long unitId, Pageable pageable);

    Page<Lease> findByStatus(LeaseStatus status, Pageable pageable);

    Page<Lease> findByPropertyId(Long propertyId, LeaseStatus status, Pageable pageable);

    Optional<Lease> findActiveLeaseForUnit(Long unitId);

    boolean hasActiveLeaseForUnit(Long unitId);

    /** Leases expiring within the given window — drives renewal notifications. */
    List<Lease> findExpiringBetween(LocalDate from, LocalDate to);

    void deleteById(Long id);
}
