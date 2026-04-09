package com.rentwrangler.controller;

import com.rentwrangler.config.SecurityConfig;
import com.rentwrangler.dto.request.TenantRequest;
import com.rentwrangler.dto.response.PagedResponse;
import com.rentwrangler.dto.response.TenantResponse;
import com.rentwrangler.exception.GlobalExceptionHandler;
import com.rentwrangler.exception.ResourceNotFoundException;
import com.rentwrangler.service.TenantService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TenantController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class TenantControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean TenantService tenantService;

    // -----------------------------------------------------------------------
    // GET /api/v1/tenants
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser
    void list_returnsPagedResponse() throws Exception {
        given(tenantService.findAll(any(), any(Pageable.class)))
                .willReturn(pagedResponseOf(sampleTenantResponse()));

        mockMvc.perform(get("/api/v1/tenants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].firstName").value("Jane"))
                .andExpect(jsonPath("$.content[0].lastName").value("Doe"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser
    void list_withSearchParam_passesSearchToService() throws Exception {
        given(tenantService.findAll(eq("Doe"), any(Pageable.class)))
                .willReturn(pagedResponseOf(sampleTenantResponse()));

        mockMvc.perform(get("/api/v1/tenants").param("search", "Doe"))
                .andExpect(status().isOk());

        verify(tenantService).findAll(eq("Doe"), any(Pageable.class));
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/tenants"))
                .andExpect(status().isUnauthorized());
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/tenants/active
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser
    void listActive_delegatesToFindActiveTenants() throws Exception {
        given(tenantService.findActiveTenants(any(Pageable.class)))
                .willReturn(pagedResponseOf(sampleTenantResponse()));

        mockMvc.perform(get("/api/v1/tenants/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(tenantService).findActiveTenants(any(Pageable.class));
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/tenants/expiring-leases
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser
    void listExpiringLeases_withDefaultDays_passesThirtyDays() throws Exception {
        given(tenantService.findWithExpiringLeases(eq(30), any(Pageable.class)))
                .willReturn(pagedResponseOf(sampleTenantResponse()));

        mockMvc.perform(get("/api/v1/tenants/expiring-leases"))
                .andExpect(status().isOk());

        verify(tenantService).findWithExpiringLeases(eq(30), any(Pageable.class));
    }

    @Test
    @WithMockUser
    void listExpiringLeases_withCustomDays_passesCorrectValue() throws Exception {
        given(tenantService.findWithExpiringLeases(eq(60), any(Pageable.class)))
                .willReturn(pagedResponseOf(sampleTenantResponse()));

        mockMvc.perform(get("/api/v1/tenants/expiring-leases").param("days", "60"))
                .andExpect(status().isOk());

        verify(tenantService).findWithExpiringLeases(eq(60), any(Pageable.class));
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/tenants/{id}
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser
    void getById_whenFound_returns200WithMaskedGovernmentId() throws Exception {
        given(tenantService.findById(1L)).willReturn(sampleTenantResponse());

        mockMvc.perform(get("/api/v1/tenants/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("Jane"))
                // The raw governmentId field must NOT be in the response
                .andExpect(jsonPath("$.governmentId").doesNotExist())
                // Only the masked form is allowed
                .andExpect(jsonPath("$.governmentIdMasked").value(endsWith("6789")));
    }

    @Test
    @WithMockUser
    void getById_whenNotFound_returns404WithProblemDetail() throws Exception {
        given(tenantService.findById(99L))
                .willThrow(new ResourceNotFoundException("Tenant", 99L));

        mockMvc.perform(get("/api/v1/tenants/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(containsString("99")));
    }

    // -----------------------------------------------------------------------
    // POST /api/v1/tenants
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_asAdmin_returns201() throws Exception {
        given(tenantService.create(any(TenantRequest.class)))
                .willReturn(sampleTenantResponse());

        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validTenantRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("jane.doe@example.com"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void create_asManager_returns201() throws Exception {
        given(tenantService.create(any(TenantRequest.class)))
                .willReturn(sampleTenantResponse());

        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validTenantRequest())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void create_asStaff_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validTenantRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_withMissingFirstName_returns400() throws Exception {
        TenantRequest invalid = validTenantRequest();
        invalid.setFirstName(null);

        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Validation")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_withInvalidEmail_returns400() throws Exception {
        TenantRequest invalid = validTenantRequest();
        invalid.setEmail("not-an-email");

        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_withInvalidPhoneFormat_returns400() throws Exception {
        TenantRequest invalid = validTenantRequest();
        invalid.setPhone("5035550100");  // missing dashes

        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_withMissingGovernmentId_returns400() throws Exception {
        TenantRequest invalid = validTenantRequest();
        invalid.setGovernmentId(null);

        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // PUT /api/v1/tenants/{id}
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "MANAGER")
    void update_asManager_returns200() throws Exception {
        given(tenantService.update(eq(1L), any(TenantRequest.class)))
                .willReturn(sampleTenantResponse());

        mockMvc.perform(put("/api/v1/tenants/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validTenantRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void update_asStaff_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/tenants/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validTenantRequest())))
                .andExpect(status().isForbidden());
    }

    // -----------------------------------------------------------------------
    // DELETE /api/v1/tenants/{id}
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_asAdmin_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/tenants/1"))
                .andExpect(status().isNoContent());
        verify(tenantService).delete(1L);
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void delete_asManager_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/tenants/1"))
                .andExpect(status().isForbidden());
    }

    // -----------------------------------------------------------------------
    // HTTP Basic auth smoke test
    // -----------------------------------------------------------------------

    @Test
    void list_withValidBasicCredentials_returns200() throws Exception {
        given(tenantService.findAll(any(), any(Pageable.class)))
                .willReturn(pagedResponseOf(sampleTenantResponse()));

        mockMvc.perform(get("/api/v1/tenants")
                        .with(httpBasic("manager", "manager123")))
                .andExpect(status().isOk());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private TenantResponse sampleTenantResponse() {
        return TenantResponse.builder()
                .id(1L)
                .firstName("Jane")
                .lastName("Doe")
                .email("jane.doe@example.com")
                .phone("503-555-0100")
                .dateOfBirth(LocalDate.of(1985, 6, 15))
                .governmentIdMasked("*****6789")
                .emergencyContactName("John Doe")
                .emergencyContactPhone("503-555-0199")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private PagedResponse<TenantResponse> pagedResponseOf(TenantResponse response) {
        return new PagedResponse<>(List.of(response), 0, 20, 1, 1, true);
    }

    private TenantRequest validTenantRequest() {
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
