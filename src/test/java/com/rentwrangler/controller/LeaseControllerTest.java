package com.rentwrangler.controller;

import com.rentwrangler.config.SecurityConfig;
import com.rentwrangler.domain.enums.LeaseStatus;
import com.rentwrangler.dto.request.LeaseRequest;
import com.rentwrangler.dto.response.LeaseResponse;
import com.rentwrangler.dto.response.PagedResponse;
import com.rentwrangler.exception.GlobalExceptionHandler;
import com.rentwrangler.exception.LeaseConflictException;
import com.rentwrangler.service.LeaseService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LeaseController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class LeaseControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean LeaseService leaseService;

    // -----------------------------------------------------------------------
    // POST /api/v1/leases
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "MANAGER")
    void create_validRequest_returns201() throws Exception {
        given(leaseService.create(any(LeaseRequest.class))).willReturn(sampleLeaseResponse());

        mockMvc.perform(post("/api/v1/leases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLeaseRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.monthlyRent").value(1500.00));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void create_whenLeaseConflict_returns409() throws Exception {
        given(leaseService.create(any(LeaseRequest.class)))
                .willThrow(new LeaseConflictException(10L));

        mockMvc.perform(post("/api/v1/leases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLeaseRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("10")));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void create_asStaff_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/leases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLeaseRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void create_withNullUnitId_returns400() throws Exception {
        LeaseRequest invalidRequest = validLeaseRequest();
        invalidRequest.setUnitId(null);

        mockMvc.perform(post("/api/v1/leases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // PATCH /api/v1/leases/{id}/terminate
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "MANAGER")
    void terminate_withReason_returns200() throws Exception {
        LeaseResponse terminated = sampleLeaseResponse();
        terminated = LeaseResponse.builder()
                .id(terminated.getId()).unitId(terminated.getUnitId())
                .unitNumber(terminated.getUnitNumber())
                .propertyId(terminated.getPropertyId())
                .propertyName(terminated.getPropertyName())
                .tenantId(terminated.getTenantId())
                .tenantFullName(terminated.getTenantFullName())
                .startDate(terminated.getStartDate()).endDate(terminated.getEndDate())
                .monthlyRent(terminated.getMonthlyRent())
                .securityDeposit(terminated.getSecurityDeposit())
                .status(LeaseStatus.TERMINATED)
                .terminationDate(LocalDate.now())
                .createdAt(terminated.getCreatedAt()).updatedAt(Instant.now())
                .build();

        given(leaseService.terminate(eq(100L), eq("Tenant relocated"))).willReturn(terminated);

        mockMvc.perform(patch("/api/v1/leases/100/terminate")
                        .param("reason", "Tenant relocated"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TERMINATED"));
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/leases/expiring
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser
    void expiringLeases_returns200WithList() throws Exception {
        given(leaseService.findExpiringWithin(30)).willReturn(List.of(sampleLeaseResponse()));

        mockMvc.perform(get("/api/v1/leases/expiring").param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/leases/by-property/{propertyId}
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser
    void byProperty_withStatusFilter_callsServiceWithStatus() throws Exception {
        given(leaseService.findByProperty(eq(1L), eq(LeaseStatus.ACTIVE), any(Pageable.class)))
                .willReturn(new PagedResponse<>(List.of(sampleLeaseResponse()), 0, 20, 1, 1, true));

        mockMvc.perform(get("/api/v1/leases/by-property/1").param("status", "ACTIVE"))
                .andExpect(status().isOk());

        verify(leaseService).findByProperty(eq(1L), eq(LeaseStatus.ACTIVE), any(Pageable.class));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private LeaseResponse sampleLeaseResponse() {
        return LeaseResponse.builder()
                .id(100L)
                .unitId(10L)
                .unitNumber("101")
                .propertyId(1L)
                .propertyName("Riverside Apartments")
                .tenantId(20L)
                .tenantFullName("Jordan Alvarez")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusYears(1))
                .monthlyRent(new BigDecimal("1500.00"))
                .securityDeposit(new BigDecimal("1500.00"))
                .status(LeaseStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private LeaseRequest validLeaseRequest() {
        LeaseRequest r = new LeaseRequest();
        r.setUnitId(10L);
        r.setTenantId(20L);
        r.setStartDate(LocalDate.now());
        r.setEndDate(LocalDate.now().plusYears(1));
        r.setMonthlyRent(new BigDecimal("1500.00"));
        r.setSecurityDeposit(new BigDecimal("1500.00"));
        return r;
    }
}
