package com.rentwrangler.repository;

import com.rentwrangler.domain.entity.Lease;
import com.rentwrangler.domain.enums.LeaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LeaseRepository extends JpaRepository<Lease, Long> {

    Page<Lease> findByTenantId(Long tenantId, Pageable pageable);

    Page<Lease> findByUnitId(Long unitId, Pageable pageable);

    Page<Lease> findByStatus(LeaseStatus status, Pageable pageable);

    Optional<Lease> findByUnitIdAndStatus(Long unitId, LeaseStatus status);

    boolean existsByUnitIdAndStatus(Long unitId, LeaseStatus status);

    /** Leases expiring within a given date window — used for renewal reminders. */
    @Query("SELECT l FROM Lease l WHERE l.status = 'ACTIVE' " +
           "AND l.endDate BETWEEN :from AND :to")
    List<Lease> findExpiringBetween(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** Active leases for a given property, navigating through the unit relationship. */
    @Query("SELECT l FROM Lease l JOIN l.unit u " +
           "WHERE u.property.id = :propertyId AND l.status = :status")
    Page<Lease> findByPropertyIdAndStatus(
            @Param("propertyId") Long propertyId,
            @Param("status") LeaseStatus status,
            Pageable pageable);
}
