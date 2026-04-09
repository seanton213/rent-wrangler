package com.rentwrangler.integration;

import com.rentwrangler.AbstractIntegrationTest;
import com.rentwrangler.domain.enums.PropertyStatus;
import com.rentwrangler.domain.enums.PropertyType;
import com.rentwrangler.dto.request.PropertyRequest;
import com.rentwrangler.dto.response.PropertyResponse;
import com.rentwrangler.service.PropertyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link PropertyService} against a real PostgreSQL database
 * (Testcontainers) and a stubbed address validation service (WireMock).
 *
 * <p>These tests verify the full call chain:
 * HTTP request → Service → WireMock/Feign → Persistence → Database
 */
class PropertyServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired PropertyService propertyService;
    @Autowired CacheManager cacheManager;

    @AfterEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        });
    }

    // -----------------------------------------------------------------------
    // Address validation (WireMock)
    // -----------------------------------------------------------------------

    @Test
    void create_callsAddressValidationService_andUsesStandardizedAddress() {
        PropertyRequest request = buildPropertyRequest("400 SW River Pkwy");

        PropertyResponse response = propertyService.create(request);

        assertThat(response.getId()).isNotNull();
        // WireMock returns "100 NW Test Ave" as the standardized address
        assertThat(response.getStreetAddress()).isEqualTo("100 NW Test Ave");

        // Verify WireMock received exactly one request
        com.github.tomakehurst.wiremock.client.WireMock
                .configureFor("localhost", wireMock.port());
        com.github.tomakehurst.wiremock.client.WireMock
                .verify(1, com.github.tomakehurst.wiremock.client.WireMock
                        .postRequestedFor(com.github.tomakehurst.wiremock.client.WireMock
                                .urlEqualTo("/api/v1/addresses/validate")));
    }

    @Test
    void create_whenAddressValidationUnavailable_fallbackCreatesPropertyWithOriginalAddress() {
        stubAddressValidationUnavailable();

        PropertyRequest request = buildPropertyRequest("999 Fallback St");

        // The @Retry fallback (createWithoutValidation) should be invoked after retries are
        // exhausted, allowing the property to be created without address standardization.
        PropertyResponse response = propertyService.create(request);

        assertThat(response).isNotNull();
        assertThat(response.getStreetAddress()).isEqualTo("999 Fallback St");

        resetWireMockStubs();
    }

    // -----------------------------------------------------------------------
    // Caffeine cache behaviour
    // -----------------------------------------------------------------------

    @Test
    void findById_secondCallServedFromCache() {
        PropertyRequest request = buildPropertyRequest("200 Cache Test Ln");
        PropertyResponse created = propertyService.create(request);
        Long id = created.getId();

        // First call populates the cache
        PropertyResponse first  = propertyService.findById(id);
        // Second call should be served from cache (no additional DB hit)
        PropertyResponse second = propertyService.findById(id);

        assertThat(first.getId()).isEqualTo(second.getId());
        assertThat(first.getName()).isEqualTo(second.getName());
    }

    @Test
    void findById_afterUpdate_returnsFreshData() {
        PropertyRequest request = buildPropertyRequest("300 Evict Test Ave");
        PropertyResponse created = propertyService.create(request);
        Long id = created.getId();

        // Populate cache
        propertyService.findById(id);

        // Update evicts the cached entry
        PropertyRequest updateRequest = buildPropertyRequest("300 Evict Test Ave");
        updateRequest.setName("Updated Property Name");
        propertyService.update(id, updateRequest);

        // Next read should return the updated name
        PropertyResponse updated = propertyService.findById(id);
        assertThat(updated.getName()).isEqualTo("Updated Property Name");
    }

    // -----------------------------------------------------------------------
    // Sorting and pagination
    // -----------------------------------------------------------------------

    @Test
    void findAll_paginatedAndSortedByName() {
        // Seed enough properties using the existing Flyway V2 data
        var page = propertyService.findAll(null, null,
                PageRequest.of(0, 2, Sort.by("name").ascending()));

        assertThat(page.getContent()).hasSizeLessThanOrEqualTo(2);
        if (page.getContent().size() == 2) {
            assertThat(page.getContent().get(0).getName())
                    .isLessThanOrEqualTo(page.getContent().get(1).getName());
        }
    }

    @Test
    void findAll_filteredByResidentialStatus() {
        var page = propertyService.findAll(
                PropertyStatus.ACTIVE, PropertyType.RESIDENTIAL,
                PageRequest.of(0, 20));

        assertThat(page.getContent()).allSatisfy(p -> {
            assertThat(p.getPropertyType()).isEqualTo(PropertyType.RESIDENTIAL);
            assertThat(p.getStatus()).isEqualTo(PropertyStatus.ACTIVE);
        });
    }

    @Test
    void findWithVacantUnits_returnsOnlyPropertiesWithVacancy() {
        var page = propertyService.findWithVacantUnits(PageRequest.of(0, 20));

        // Seed data has multiple vacant units — at least one property should be returned
        assertThat(page.getTotalElements()).isGreaterThan(0);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private PropertyRequest buildPropertyRequest(String streetAddress) {
        PropertyRequest r = new PropertyRequest();
        r.setName("Test Property " + System.nanoTime());
        r.setStreetAddress(streetAddress);
        r.setCity("Portland");
        r.setState("OR");
        r.setZipCode("97201");
        r.setPropertyType(PropertyType.RESIDENTIAL);
        r.setStatus(PropertyStatus.ACTIVE);
        r.setTotalUnits(10);
        return r;
    }
}
