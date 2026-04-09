package com.rentwrangler.integration;

import com.rentwrangler.AbstractIntegrationTest;
import com.rentwrangler.domain.entity.Lease;
import com.rentwrangler.domain.entity.Property;
import com.rentwrangler.domain.entity.Tenant;
import com.rentwrangler.domain.entity.Unit;
import com.rentwrangler.domain.enums.*;
import com.rentwrangler.persistence.LeasePersistenceService;
import com.rentwrangler.repository.LeaseRepository;
import com.rentwrangler.repository.PropertyRepository;
import com.rentwrangler.repository.TenantRepository;
import com.rentwrangler.repository.UnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link LeasePersistenceService}.
 *
 * <p>Verifies the complex JPQL queries executed via {@code @PersistenceContext EntityManager},
 * the cache eviction behaviour on lease mutations, and the expiring-lease window query.
 * All tests use a real PostgreSQL database via Testcontainers.
 */
@Transactional
class LeasePersistenceIntegrationTest extends AbstractIntegrationTest {

    @Autowired LeasePersistenceService leasePersistenceService;
    @Autowired LeaseRepository leaseRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired UnitRepository unitRepository;
    @Autowired TenantRepository tenantRepository;

    private Unit unit;
    private Tenant tenant;

    @BeforeEach
    void seed() {
        Property property = propertyRepository.save(Property.builder()
                .name("Integration Test Property")
                .streetAddress("1 Test St")
                .city("Portland").state("OR").zipCode("97201")
                .propertyType(PropertyType.RESIDENTIAL)
                .status(PropertyStatus.ACTIVE)
                .totalUnits(5)
                .build());

        unit = unitRepository.save(Unit.builder()
                .property(property)
                .unitNumber("IT-101")
                .bedrooms(1)
                .bathrooms(BigDecimal.ONE)
                .status(UnitStatus.VACANT)
                .monthlyRent(new BigDecimal("1200.00"))
                .build());

        tenant = tenantRepository.save(Tenant.builder()
                .firstName("Integration")
                .lastName("Tester")
                .email("integration@test.com")
                .governmentId("555-55-5555")  // encrypted by Hibernate listener
                .build());
    }

    // -----------------------------------------------------------------------
    // Active lease detection
    // -----------------------------------------------------------------------

    @Test
    void hasActiveLeaseForUnit_whenNoActiveLease_returnsFalse() {
        assertThat(leasePersistenceService.hasActiveLeaseForUnit(unit.getId())).isFalse();
    }

    @Test
    void hasActiveLeaseForUnit_whenActiveLease_returnsTrue() {
        saveActiveLease(LocalDate.now(), LocalDate.now().plusYears(1));
        assertThat(leasePersistenceService.hasActiveLeaseForUnit(unit.getId())).isTrue();
    }

    @Test
    void hasActiveLeaseForUnit_whenLeaseTerminated_returnsFalse() {
        Lease lease = saveActiveLease(LocalDate.now(), LocalDate.now().plusYears(1));
        lease.setStatus(LeaseStatus.TERMINATED);
        leaseRepository.save(lease);
        leaseRepository.flush();

        assertThat(leasePersistenceService.hasActiveLeaseForUnit(unit.getId())).isFalse();
    }

    @Test
    void findActiveLeaseForUnit_returnsCorrectLease() {
        saveActiveLease(LocalDate.now(), LocalDate.now().plusYears(1));

        Optional<Lease> found = leasePersistenceService.findActiveLeaseForUnit(unit.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(LeaseStatus.ACTIVE);
        assertThat(found.get().getTenant().getId()).isEqualTo(tenant.getId());
    }

    // -----------------------------------------------------------------------
    // Expiring leases window
    // -----------------------------------------------------------------------

    @Test
    void findExpiringBetween_returnsLeasesInWindow() {
        LocalDate expiresIn20Days = LocalDate.now().plusDays(20);
        LocalDate expiresIn60Days = LocalDate.now().plusDays(60);

        saveActiveLease(LocalDate.now().minusMonths(11), expiresIn20Days);

        // This lease expires outside the 30-day window and should NOT appear
        Tenant anotherTenant = tenantRepository.save(Tenant.builder()
                .firstName("Other").lastName("Tenant").email("other@test.com").governmentId("111-11-1111").build());
        Unit anotherUnit = unitRepository.save(Unit.builder()
                .property(unit.getProperty()).unitNumber("IT-102")
                .bedrooms(1).bathrooms(BigDecimal.ONE)
                .status(UnitStatus.VACANT).monthlyRent(new BigDecimal("1300.00")).build());
        leaseRepository.save(Lease.builder()
                .unit(anotherUnit).tenant(anotherTenant)
                .startDate(LocalDate.now().minusMonths(6))
                .endDate(expiresIn60Days)
                .monthlyRent(new BigDecimal("1300.00"))
                .securityDeposit(new BigDecimal("1300.00"))
                .status(LeaseStatus.ACTIVE).build());

        List<Lease> expiring = leasePersistenceService.findExpiringBetween(
                LocalDate.now(), LocalDate.now().plusDays(30));

        assertThat(expiring)
                .hasSize(1)
                .allSatisfy(l -> assertThat(l.getEndDate()).isBefore(LocalDate.now().plusDays(31)));
    }

    // -----------------------------------------------------------------------
    // Pagination and sorting
    // -----------------------------------------------------------------------

    @Test
    void findByTenantId_paginationWorks() {
        saveActiveLease(LocalDate.now().minusMonths(12), LocalDate.now().minusDays(1));
        saveActiveLease(LocalDate.now(), LocalDate.now().plusYears(1));

        Page<Lease> page = leasePersistenceService.findByTenantId(
                tenant.getId(),
                PageRequest.of(0, 1, Sort.by("startDate").descending()));

        // Only one result returned per page
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    // -----------------------------------------------------------------------
    // Cache eviction
    // -----------------------------------------------------------------------

    @Test
    void save_evictsUnitLeaseStatusCache() {
        // Prime the cache with false (no active lease)
        boolean beforeSave = leasePersistenceService.hasActiveLeaseForUnit(unit.getId());
        assertThat(beforeSave).isFalse();

        // Save a new active lease — should evict the cache
        saveActiveLease(LocalDate.now(), LocalDate.now().plusYears(1));

        // After eviction, the next call must hit the database and return true
        boolean afterSave = leasePersistenceService.hasActiveLeaseForUnit(unit.getId());
        assertThat(afterSave).isTrue();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Lease saveActiveLease(LocalDate start, LocalDate end) {
        return leasePersistenceService.save(Lease.builder()
                .unit(unit).tenant(tenant)
                .startDate(start).endDate(end)
                .monthlyRent(new BigDecimal("1200.00"))
                .securityDeposit(new BigDecimal("1200.00"))
                .status(LeaseStatus.ACTIVE)
                .build());
    }
}
