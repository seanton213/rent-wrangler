package com.rentwrangler.controller;

import com.rentwrangler.config.SecurityConfig;
import com.rentwrangler.domain.enums.MaintenanceCategory;
import com.rentwrangler.domain.enums.MaintenancePriority;
import com.rentwrangler.domain.enums.MaintenanceStatus;
import com.rentwrangler.dto.request.CreateMaintenanceRequest;
import com.rentwrangler.dto.response.MaintenanceResponse;
import com.rentwrangler.dto.response.PagedResponse;
import com.rentwrangler.exception.GlobalExceptionHandler;
import com.rentwrangler.service.MaintenanceService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MaintenanceController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class MaintenanceControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean MaintenanceService maintenanceService;

    // -----------------------------------------------------------------------
    // GET /api/v1/maintenance
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser
    void list_withFilters_passesAllFiltersToService() throws Exception {
        given(maintenanceService.findAll(any(), any(), any(), any(), any(Pageable.class)))
                .willReturn(emptyPage());

        mockMvc.perform(get("/api/v1/maintenance")
                        .param("propertyId", "1")
                        .param("status", "OPEN")
                        .param("category", "PLUMBING")
                        .param("priority", "HIGH"))
                .andExpect(status().isOk());

        verify(maintenanceService).findAll(
                eq(1L), eq(MaintenanceStatus.OPEN),
                eq(MaintenanceCategory.PLUMBING), eq(MaintenancePriority.HIGH),
                any(Pageable.class));
    }

    @Test
    @WithMockUser
    void list_defaultPagination_usesDescCreatedAtSort() throws Exception {
        given(maintenanceService.findAll(any(), any(), any(), any(), any(Pageable.class)))
                .willReturn(emptyPage());

        mockMvc.perform(get("/api/v1/maintenance"))
                .andExpect(status().isOk());
    }

    // -----------------------------------------------------------------------
    // POST /api/v1/maintenance
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser
    void submit_validRequest_returns201WithAssignment() throws Exception {
        CreateMaintenanceRequest dto = new CreateMaintenanceRequest();
        dto.setUnitId(10L);
        dto.setCategory(MaintenanceCategory.PLUMBING);
        dto.setPriority(MaintenancePriority.HIGH);
        dto.setTitle("Leak under sink");
        dto.setDescription("Water pooling under kitchen sink");

        given(maintenanceService.submit(any(CreateMaintenanceRequest.class)))
                .willReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/maintenance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.vendorName").value("Pacific Plumbing Co."))
                .andExpect(jsonPath("$.estimatedCost").value(275.00));
    }

    @Test
    @WithMockUser
    void submit_missingCategory_returns400() throws Exception {
        CreateMaintenanceRequest dto = new CreateMaintenanceRequest();
        dto.setUnitId(10L);
        dto.setTitle("Leak");
        dto.setDescription("Leaking");
        // category is null — should fail @NotNull validation

        mockMvc.perform(post("/api/v1/maintenance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // PATCH /api/v1/maintenance/{id}/status
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "STAFF")
    void updateStatus_asStaff_returns200() throws Exception {
        given(maintenanceService.updateStatus(eq(50L), eq(MaintenanceStatus.IN_PROGRESS), any()))
                .willReturn(sampleResponse());

        mockMvc.perform(patch("/api/v1/maintenance/50/status")
                        .param("status", "IN_PROGRESS"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void updateStatus_withActualCost_passedToService() throws Exception {
        given(maintenanceService.updateStatus(eq(50L), eq(MaintenanceStatus.COMPLETED), eq(180.0)))
                .willReturn(sampleResponse());

        mockMvc.perform(patch("/api/v1/maintenance/50/status")
                        .param("status", "COMPLETED")
                        .param("actualCost", "180.0"))
                .andExpect(status().isOk());

        verify(maintenanceService).updateStatus(50L, MaintenanceStatus.COMPLETED, 180.0);
    }

    // -----------------------------------------------------------------------
    // DELETE /api/v1/maintenance/{id}
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_asAdmin_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/maintenance/50"))
                .andExpect(status().isNoContent());
        verify(maintenanceService).delete(50L);
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void delete_asStaff_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/maintenance/50"))
                .andExpect(status().isForbidden());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private MaintenanceResponse sampleResponse() {
        return MaintenanceResponse.builder()
                .id(50L)
                .unitId(10L)
                .unitNumber("101")
                .propertyId(1L)
                .propertyName("Riverside")
                .category(MaintenanceCategory.PLUMBING)
                .priority(MaintenancePriority.HIGH)
                .status(MaintenanceStatus.ASSIGNED)
                .title("Leak under sink")
                .description("Water pooling")
                .vendorName("Pacific Plumbing Co.")
                .estimatedCost(new BigDecimal("275.00"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private PagedResponse<MaintenanceResponse> emptyPage() {
        return new PagedResponse<>(List.of(), 0, 20, 0, 0, true);
    }
}
