package com.rentwrangler.repository;

import com.rentwrangler.domain.entity.Unit;
import com.rentwrangler.domain.enums.UnitStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface UnitRepository extends JpaRepository<Unit, Long> {

    Page<Unit> findByPropertyId(Long propertyId, Pageable pageable);

    Page<Unit> findByPropertyIdAndStatus(Long propertyId, UnitStatus status, Pageable pageable);

    Page<Unit> findByStatus(UnitStatus status, Pageable pageable);

    Optional<Unit> findByPropertyIdAndUnitNumber(Long propertyId, String unitNumber);

    boolean existsByPropertyIdAndUnitNumber(Long propertyId, String unitNumber);

    @Query("SELECT u FROM Unit u WHERE u.property.id = :propertyId " +
           "AND u.monthlyRent BETWEEN :minRent AND :maxRent")
    Page<Unit> findByPropertyIdAndRentRange(
            @Param("propertyId") Long propertyId,
            @Param("minRent") BigDecimal minRent,
            @Param("maxRent") BigDecimal maxRent,
            Pageable pageable);

    long countByPropertyIdAndStatus(Long propertyId, UnitStatus status);
}
