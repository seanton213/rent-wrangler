package com.rentwrangler.service;

import com.rentwrangler.context.RequestContext;
import com.rentwrangler.domain.entity.Tenant;
import com.rentwrangler.dto.request.TenantRequest;
import com.rentwrangler.dto.response.PagedResponse;
import com.rentwrangler.dto.response.TenantResponse;
import com.rentwrangler.exception.ResourceNotFoundException;
import com.rentwrangler.persistence.TenantPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock private TenantPersistenceService persistenceService;
    @Mock private RequestContext requestContext;

    @InjectMocks
    private TenantService tenantService;

    private Tenant sampleTenant;

    @BeforeEach
    void setUp() {
        sampleTenant = Tenant.builder()
                .id(1L)
                .firstName("Jane")
                .lastName("Doe")
                .email("jane.doe@example.com")
                .phone("503-555-0100")
                .dateOfBirth(LocalDate.of(1985, 6, 15))
                .governmentId("123-45-6789")
                .emergencyContactName("John Doe")
                .emergencyContactPhone("503-555-0199")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .build();
    }

    // -----------------------------------------------------------------------
    // findAll
    // -----------------------------------------------------------------------

    @Test
    void findAll_withNoSearch_delegatesToFindAll() {
        Pageable pageable = PageRequest.of(0, 20);
        given(persistenceService.findAll(pageable))
                .willReturn(new PageImpl<>(List.of(sampleTenant)));

        PagedResponse<TenantResponse> result = tenantService.findAll(null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getEmail()).isEqualTo("jane.doe@example.com");
        verify(persistenceService).findAll(pageable);
        verify(persistenceService, never()).search(anyString(), any());
    }

    @Test
    void findAll_withBlankSearch_delegatesToFindAll() {
        Pageable pageable = PageRequest.of(0, 20);
        given(persistenceService.findAll(pageable))
                .willReturn(new PageImpl<>(List.of(sampleTenant)));

        tenantService.findAll("   ", pageable);

        verify(persistenceService).findAll(pageable);
        verify(persistenceService, never()).search(anyString(), any());
    }

    @Test
    void findAll_withSearchTerm_delegatesToSearch() {
        Pageable pageable = PageRequest.of(0, 20);
        given(persistenceService.search("Doe", pageable))
                .willReturn(new PageImpl<>(List.of(sampleTenant)));

        PagedResponse<TenantResponse> result = tenantService.findAll("Doe", pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(persistenceService).search("Doe", pageable);
        verify(persistenceService, never()).findAll(any());
    }

    // -----------------------------------------------------------------------
    // findById
    // -----------------------------------------------------------------------

    @Test
    void findById_whenFound_returnsMaskedResponse() {
        given(persistenceService.findById(1L)).willReturn(Optional.of(sampleTenant));

        TenantResponse response = tenantService.findById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getFirstName()).isEqualTo("Jane");
        // Government ID must never appear in plain text — only the masked form
        assertThat(response.getGovernmentIdMasked()).isEqualTo("*****6789");
    }

    @Test
    void findById_whenNotFound_throwsResourceNotFoundException() {
        given(persistenceService.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // -----------------------------------------------------------------------
    // findActiveTenants
    // -----------------------------------------------------------------------

    @Test
    void findActiveTenants_delegatesToPersistenceService() {
        Pageable pageable = PageRequest.of(0, 20);
        given(persistenceService.findActiveTenants(pageable))
                .willReturn(new PageImpl<>(List.of(sampleTenant)));

        PagedResponse<TenantResponse> result = tenantService.findActiveTenants(pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(persistenceService).findActiveTenants(pageable);
    }

    // -----------------------------------------------------------------------
    // findWithExpiringLeases
    // -----------------------------------------------------------------------

    @Test
    void findWithExpiringLeases_passesCorrectDaysAhead() {
        Pageable pageable = PageRequest.of(0, 20);
        given(persistenceService.findWithExpiringLeases(30, pageable))
                .willReturn(new PageImpl<>(List.of(sampleTenant)));

        PagedResponse<TenantResponse> result = tenantService.findWithExpiringLeases(30, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(persistenceService).findWithExpiringLeases(eq(30), eq(pageable));
    }

    // -----------------------------------------------------------------------
    // create
    // -----------------------------------------------------------------------

    @Test
    void create_withUniqueEmail_savesAndReturnsMaskedResponse() {
        TenantRequest request = buildTenantRequest();
        given(persistenceService.existsByEmail(request.getEmail())).willReturn(false);
        given(persistenceService.save(any(Tenant.class))).willReturn(sampleTenant);

        TenantResponse response = tenantService.create(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getGovernmentIdMasked()).endsWith("6789");
        verify(persistenceService).save(any(Tenant.class));
    }

    @Test
    void create_withDuplicateEmail_throwsIllegalArgument() {
        TenantRequest request = buildTenantRequest();
        given(persistenceService.existsByEmail(request.getEmail())).willReturn(true);

        assertThatThrownBy(() -> tenantService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(request.getEmail());

        verify(persistenceService, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // update
    // -----------------------------------------------------------------------

    @Test
    void update_whenFound_updatesAllFields() {
        TenantRequest request = buildTenantRequest();
        request.setFirstName("Janet");
        request.setPhone("503-555-0200");

        given(persistenceService.findById(1L)).willReturn(Optional.of(sampleTenant));
        given(persistenceService.save(any(Tenant.class))).willAnswer(inv -> inv.getArgument(0));

        TenantResponse response = tenantService.update(1L, request);

        assertThat(response.getFirstName()).isEqualTo("Janet");
        assertThat(response.getPhone()).isEqualTo("503-555-0200");
    }

    @Test
    void update_whenNotFound_throwsResourceNotFoundException() {
        given(persistenceService.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.update(99L, buildTenantRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // -----------------------------------------------------------------------
    // delete
    // -----------------------------------------------------------------------

    @Test
    void delete_delegatesToPersistenceService() {
        tenantService.delete(1L);
        verify(persistenceService).deleteById(1L);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private TenantRequest buildTenantRequest() {
        TenantRequest r = new TenantRequest();
        r.setFirstName("Jane");
        r.setLastName("Doe");
        r.setEmail("jane.doe@example.com");
        r.setPhone("503-555-0100");
        r.setDateOfBirth(LocalDate.of(1985, 6, 15));
        r.setGovernmentId("123-45-6789");
        r.setEmergencyContactName("John Doe");
        r.setEmergencyContactPhone("503-555-0199");
        return r;
    }
}
