package com.rentwrangler.controller;

import com.rentwrangler.config.SecurityConfig;
import com.rentwrangler.domain.enums.PropertyStatus;
import com.rentwrangler.domain.enums.PropertyType;
import com.rentwrangler.dto.request.PropertyRequest;
import com.rentwrangler.dto.response.PagedResponse;
import com.rentwrangler.dto.response.PropertyResponse;
import com.rentwrangler.exception.GlobalExceptionHandler;
import com.rentwrangler.exception.ResourceNotFoundException;
import com.rentwrangler.service.PropertyService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PropertyController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PropertyControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean PropertyService propertyService;

    // -----------------------------------------------------------------------
    // GET /api/v1/properties
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser
    void list_returnsPagedResponse() throws Exception {
        given(propertyService.findAll(any(), any(), any(Pageable.class)))
                .willReturn(pagedResponseOf(samplePropertyResponse()));

        mockMvc.perform(get("/api/v1/properties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Riverside Apartments"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser
    void list_withStatusFilter_passesFilterToService() throws Exception {
        given(propertyService.findAll(eq(PropertyStatus.ACTIVE), any(), any(Pageable.class)))
                .willReturn(pagedResponseOf(samplePropertyResponse()));

        mockMvc.perform(get("/api/v1/properties").param("status", "ACTIVE"))
                .andExpect(status().isOk());

        verify(propertyService).findAll(eq(PropertyStatus.ACTIVE), any(), any(Pageable.class));
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/properties"))
                .andExpect(status().isUnauthorized());
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/properties/{id}
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser
    void getById_whenFound_returns200() throws Exception {
        given(propertyService.findById(1L)).willReturn(samplePropertyResponse());

        mockMvc.perform(get("/api/v1/properties/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Riverside Apartments"))
                .andExpect(jsonPath("$.propertyType").value("RESIDENTIAL"));
    }

    @Test
    @WithMockUser
    void getById_whenNotFound_returns404WithProblemDetail() throws Exception {
        given(propertyService.findById(99L))
                .willThrow(new ResourceNotFoundException("Property", 99L));

        mockMvc.perform(get("/api/v1/properties/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("99")));
    }

    // -----------------------------------------------------------------------
    // POST /api/v1/properties
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_asAdmin_returns201() throws Exception {
        given(propertyService.create(any(PropertyRequest.class)))
                .willReturn(samplePropertyResponse());

        mockMvc.perform(post("/api/v1/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validPropertyRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Riverside Apartments"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void create_asManager_returns201() throws Exception {
        given(propertyService.create(any(PropertyRequest.class)))
                .willReturn(samplePropertyResponse());

        mockMvc.perform(post("/api/v1/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validPropertyRequest())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void create_asStaff_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validPropertyRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_withMissingName_returns400() throws Exception {
        PropertyRequest invalidRequest = validPropertyRequest();
        invalidRequest.setName(null);

        mockMvc.perform(post("/api/v1/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Validation")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_withInvalidZip_returns400() throws Exception {
        PropertyRequest invalidRequest = validPropertyRequest();
        invalidRequest.setZipCode("BADZIP");

        mockMvc.perform(post("/api/v1/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // DELETE /api/v1/properties/{id}
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_asAdmin_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/properties/1"))
                .andExpect(status().isNoContent());
        verify(propertyService).delete(1L);
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void delete_asManager_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/properties/1"))
                .andExpect(status().isForbidden());
    }

    // -----------------------------------------------------------------------
    // HTTP Basic auth smoke test
    // -----------------------------------------------------------------------

    @Test
    void list_withValidBasicCredentials_returns200() throws Exception {
        given(propertyService.findAll(any(), any(), any(Pageable.class)))
                .willReturn(pagedResponseOf(samplePropertyResponse()));

        mockMvc.perform(get("/api/v1/properties")
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private PropertyResponse samplePropertyResponse() {
        return PropertyResponse.builder()
                .id(1L)
                .name("Riverside Apartments")
                .streetAddress("400 SW River Pkwy")
                .city("Portland")
                .state("OR")
                .zipCode("97201")
                .propertyType(PropertyType.RESIDENTIAL)
                .status(PropertyStatus.ACTIVE)
                .totalUnits(24)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private PagedResponse<PropertyResponse> pagedResponseOf(PropertyResponse response) {
        return new PagedResponse<>(List.of(response), 0, 20, 1, 1, true);
    }

    private PropertyRequest validPropertyRequest() {
        PropertyRequest r = new PropertyRequest();
        r.setName("Riverside Apartments");
        r.setStreetAddress("400 SW River Pkwy");
        r.setCity("Portland");
        r.setState("OR");
        r.setZipCode("97201");
        r.setPropertyType(PropertyType.RESIDENTIAL);
        r.setStatus(PropertyStatus.ACTIVE);
        r.setTotalUnits(24);
        return r;
    }
}
