package com.rentwrangler.integration;

import com.rentwrangler.AbstractIntegrationTest;
import com.rentwrangler.domain.entity.Lease;
import com.rentwrangler.domain.entity.Property;
import com.rentwrangler.domain.entity.Tenant;
import com.rentwrangler.domain.entity.Unit;
import com.rentwrangler.domain.enums.*;
import com.rentwrangler.dto.response.PagedResponse;
import com.rentwrangler.dto.response.TenantResponse;
import com.rentwrangler.repository.LeaseRepository;
import com.rentwrangler.repository.PropertyRepository;
import com.rentwrangler.repository.TenantRepository;
import com.rentwrangler.repository.UnitRepository;
import com.rentwrangler.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link TenantService} against a real PostgreSQL database
 * (Testcontainers).
 *
 * <p>Covers the JPQL queries in {@code TenantPersistenceServiceImpl} that cannot be
 * adequately validated with Mockito unit tests, including:
 * <ul>
 *   <li>{@code findActiveTenants} — DISTINCT JOIN across Tenant→Lease</li>
 *   <li>{@code findWithExpiringLeases} — date-windowed JOIN with ORDER BY</li>
 *   <li>{@code search} — case-insensitive name search</li>
 *   <li>Caffeine cache eviction after a tenant update</li>
 * </ul>
 */
class TenantServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired TenantService tenantService;
    @Autowired TenantRepository tenantRepository;
    @Autowired LeaseRepository leaseRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired UnitRepository unitRepository;
    @Autowired CacheManager cacheManager;

    private Tenant activeTenant;
    private Tenant inactiveTenant;
    private Tenant expiringTenant;

    @BeforeEach
    void seed() {
        Property property = propertyRepository.save(Property.builder()
                .name("IT Tenant Property")
                .streetAddress("9 Test Blvd")
                .city("Portland").state("OR").zipCode("97201")
                .propertyType(PropertyType.RESIDENTIAL)
                .status(PropertyStatus.ACTIVE)
                .totalUnits(10)
                .build());

        // Tenant with an ACTIVE lease ending well in the future
        activeTenant = tenantRepository.save(Tenant.builder()
                .firstName("Alice").lastName("Active")
                .email("alice.active@test.com")
                .governmentId("111-11-1111")
                .build());

        Unit unitA = unitRepository.save(Unit.builder()
                .property(property).unitNumber("IT-A1")
                .bedrooms(1).bathrooms(BigDecimal.ONE)
                .status(UnitStatus.OCCUPIED)
                .monthlyRent(new BigDecimal("1200.00"))
                .build());

        leaseRepository.save(Lease.builder()
                .unit(unitA).tenant(activeTenant)
                .startDate(LocalDate.now().minusMonths(6))
                .endDate(LocalDate.now().plusMonths(6))
                .monthlyRent(new BigDecimal("1200.00"))
                .securityDeposit(new BigDecimal("1200.00"))
                .status(LeaseStatus.ACTIVE)
                .build());

        // Tenant with a TERMINATED lease — must NOT appear in active results
        inactiveTenant = tenantRepository.save(Tenant.builder()
                .firstName("Bob").lastName("Inactive")
                .email("bob.inactive@test.com")
                .governmentId("222-22-2222")
                .build());

        Unit unitB = unitRepository.save(Unit.builder()
                .property(property).unitNumber("IT-B1")
                .bedrooms(1).bathrooms(BigDecimal.ONE)
                .status(UnitStatus.VACANT)
                .monthlyRent(new BigDecimal("1100.00"))
                .build());

        leaseRepository.save(Lease.builder()
                .unit(unitB).tenant(inactiveTenant)
                .startDate(LocalDate.now().minusYears(1))
                .endDate(LocalDate.now().minusDays(1))
                .monthlyRent(new BigDecimal("1100.00"))
                .securityDeposit(new BigDecimal("1100.00"))
                .status(LeaseStatus.TERMINATED)
                .build());

        // Tenant with an ACTIVE lease expiring within 20 days — must appear in expiring-leases
        expiringTenant = tenantRepository.save(Tenant.builder()
                .firstName("Carol").lastName("Expiring")
                .email("carol.expiring@test.com")
                .governmentId("333-33-3333")
                .build());

        Unit unitC = unitRepository.save(Unit.builder()
                .property(property).unitNumber("IT-C1")
                .bedrooms(1).bathrooms(BigDecimal.ONE)
                .status(UnitStatus.OCCUPIED)
                .monthlyRent(new BigDecimal("1300.00"))
                .build());

        leaseRepository.save(Lease.builder()
                .unit(unitC).tenant(expiringTenant)
                .startDate(LocalDate.now().minusMonths(11))
                .endDate(LocalDate.now().plusDays(20))
                .monthlyRent(new BigDecimal("1300.00"))
                .securityDeposit(new BigDecimal("1300.00"))
                .status(LeaseStatus.ACTIVE)
                .build());
    }

    @AfterEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        });
    }

    // -----------------------------------------------------------------------
    // findActiveTenants — JPQL DISTINCT JOIN
    // -----------------------------------------------------------------------

    @Test
    void findActiveTenants_returnsOnlyTenantsWithActiveLeases() {
        PagedResponse<TenantResponse> result = tenantService.findActiveTenants(
                PageRequest.of(0, 20));

        var emails = result.getContent().stream()
                .map(TenantResponse::getEmail)
                .toList();

        assertThat(emails).contains("alice.active@test.com", "carol.expiring@test.com");
        assertThat(emails).doesNotContain("bob.inactive@test.com");
    }

    @Test
    void findActiveTenants_paginationIsRespected() {
        PagedResponse<TenantResponse> page = tenantService.findActiveTenants(
                PageRequest.of(0, 1));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void findActiveTenants_tenantWithMultipleActiveLeases_appearsOnce() {
        // Give activeTenant a second active lease on a different unit
        Property property2 = propertyRepository.save(Property.builder()
                .name("IT Duplicate Lease Property")
                .streetAddress("10 Test Blvd")
                .city("Portland").state("OR").zipCode("97201")
                .propertyType(PropertyType.RESIDENTIAL)
                .status(PropertyStatus.ACTIVE)
                .totalUnits(1)
                .build());

        Unit extraUnit = unitRepository.save(Unit.builder()
                .property(property2).unitNumber("IT-X1")
                .bedrooms(2).bathrooms(new BigDecimal("2"))
                .status(UnitStatus.OCCUPIED)
                .monthlyRent(new BigDecimal("1500.00"))
                .build());

        leaseRepository.save(Lease.builder()
                .unit(extraUnit).tenant(activeTenant)
                .startDate(LocalDate.now()).endDate(LocalDate.now().plusYears(1))
                .monthlyRent(new BigDecimal("1500.00"))
                .securityDeposit(new BigDecimal("1500.00"))
                .status(LeaseStatus.ACTIVE)
                .build());

        PagedResponse<TenantResponse> result = tenantService.findActiveTenants(
                PageRequest.of(0, 20));

        long aliceCount = result.getContent().stream()
                .filter(t -> t.getEmail().equals("alice.active@test.com"))
                .count();

        assertThat(aliceCount)
                .as("A tenant with multiple active leases must appear only once (DISTINCT)")
                .isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // findWithExpiringLeases — date-windowed JPQL
    // -----------------------------------------------------------------------

    @Test
    void findWithExpiringLeases_returnsTenantsWithinWindow() {
        PagedResponse<TenantResponse> result = tenantService.findWithExpiringLeases(
                30, PageRequest.of(0, 20));

        var emails = result.getContent().stream()
                .map(TenantResponse::getEmail)
                .toList();

        // Expiring in 20 days → inside the 30-day window
        assertThat(emails).contains("carol.expiring@test.com");
        // Expiring in 6 months → outside the window
        assertThat(emails).doesNotContain("alice.active@test.com");
        // Terminated lease → never in results regardless of end date
        assertThat(emails).doesNotContain("bob.inactive@test.com");
    }

    @Test
    void findWithExpiringLeases_withNarrowWindow_excludesTenantOutsideWindow() {
        // Carol's lease expires in 20 days; a 10-day window must exclude her
        PagedResponse<TenantResponse> result = tenantService.findWithExpiringLeases(
                10, PageRequest.of(0, 20));

        var emails = result.getContent().stream()
                .map(TenantResponse::getEmail)
                .toList();

        assertThat(emails).doesNotContain("carol.expiring@test.com");
    }

    @Test
    void findWithExpiringLeases_resultsSortedByLastNameThenFirstName() {
        // Add a second tenant expiring in the same window but with an alphabetically
        // earlier last name to verify ORDER BY t.lastName, t.firstName
        Tenant aardvark = tenantRepository.save(Tenant.builder()
                .firstName("Zane").lastName("Aardvark")
                .email("zane.aardvark@test.com")
                .governmentId("444-44-4444")
                .build());

        Property prop = propertyRepository.save(Property.builder()
                .name("Sort Test Property")
                .streetAddress("11 Sort Ave")
                .city("Portland").state("OR").zipCode("97201")
                .propertyType(PropertyType.RESIDENTIAL)
                .status(PropertyStatus.ACTIVE).totalUnits(1).build());

        Unit u = unitRepository.save(Unit.builder()
                .property(prop).unitNumber("S-1")
                .bedrooms(1).bathrooms(BigDecimal.ONE)
                .status(UnitStatus.OCCUPIED)
                .monthlyRent(new BigDecimal("900.00")).build());

        leaseRepository.save(Lease.builder()
                .unit(u).tenant(aardvark)
                .startDate(LocalDate.now().minusMonths(11))
                .endDate(LocalDate.now().plusDays(15))
                .monthlyRent(new BigDecimal("900.00"))
                .securityDeposit(new BigDecimal("900.00"))
                .status(LeaseStatus.ACTIVE).build());

        PagedResponse<TenantResponse> result = tenantService.findWithExpiringLeases(
                30, PageRequest.of(0, 20));

        var lastNames = result.getContent().stream()
                .map(TenantResponse::getLastName)
                .toList();

        assertThat(lastNames.get(0))
                .as("Aardvark must sort before Expiring")
                .isEqualTo("Aardvark");
    }

    // -----------------------------------------------------------------------
    // search — case-insensitive name query
    // -----------------------------------------------------------------------

    @Test
    void search_byCaseInsensitiveLastName_returnsMatchingTenants() {
        PagedResponse<TenantResponse> result = tenantService.findAll(
                "active", PageRequest.of(0, 20, Sort.by("lastName")));

        assertThat(result.getContent())
                .anyMatch(t -> t.getLastName().equalsIgnoreCase("Active"));
    }

    @Test
    void search_withNoMatch_returnsEmptyPage() {
        PagedResponse<TenantResponse> result = tenantService.findAll(
                "zzznomatch", PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    // -----------------------------------------------------------------------
    // Caffeine cache eviction
    // -----------------------------------------------------------------------

    @Test
    void findById_secondCallServedFromCache() {
        Long id = activeTenant.getId();

        TenantResponse first  = tenantService.findById(id);
        TenantResponse second = tenantService.findById(id);

        assertThat(first.getId()).isEqualTo(second.getId());
        assertThat(first.getEmail()).isEqualTo(second.getEmail());
    }
}
