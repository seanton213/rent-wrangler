package com.rentwrangler.controller;

import com.rentwrangler.dto.request.TenantRequest;
import com.rentwrangler.dto.response.PagedResponse;
import com.rentwrangler.dto.response.TenantResponse;
import com.rentwrangler.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
@Tag(name = "Tenants", description = "Manage tenants — government ID is encrypted at rest")
public class TenantController {

    private final TenantService tenantService;

    @GetMapping
    @Operation(summary = "List tenants with optional search, sorting, and pagination")
    public PagedResponse<TenantResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "lastName") String sortBy,
            @RequestParam(defaultValue = "asc")      String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        return tenantService.findAll(search, PageRequest.of(page, size, sort));
    }

    @GetMapping("/active")
    @Operation(summary = "List tenants who currently have an active lease")
    public PagedResponse<TenantResponse> listActive(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return tenantService.findActiveTenants(PageRequest.of(page, size));
    }

    @GetMapping("/expiring-leases")
    @Operation(summary = "List tenants whose leases expire within N days (default 30)")
    public PagedResponse<TenantResponse> listWithExpiringLeases(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return tenantService.findWithExpiringLeases(days, PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a tenant by ID — government ID is masked in response")
    public TenantResponse getById(@PathVariable Long id) {
        return tenantService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Create a tenant — government ID is encrypted before storage")
    public TenantResponse create(@RequestBody @Valid TenantRequest request) {
        return tenantService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Update a tenant")
    public TenantResponse update(@PathVariable Long id,
                                 @RequestBody @Valid TenantRequest request) {
        return tenantService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a tenant (admin only)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        tenantService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
