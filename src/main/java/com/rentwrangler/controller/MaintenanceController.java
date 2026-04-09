package com.rentwrangler.controller;

import com.rentwrangler.domain.enums.MaintenanceCategory;
import com.rentwrangler.domain.enums.MaintenancePriority;
import com.rentwrangler.domain.enums.MaintenanceStatus;
import com.rentwrangler.dto.request.CreateMaintenanceRequest;
import com.rentwrangler.dto.response.MaintenanceResponse;
import com.rentwrangler.dto.response.PagedResponse;
import com.rentwrangler.service.MaintenanceService;
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
@RequestMapping("/api/v1/maintenance")
@RequiredArgsConstructor
@Tag(name = "Maintenance", description = "Submit and manage maintenance requests; vendor assignment uses Strategy pattern")
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    @GetMapping
    @Operation(summary = "List maintenance requests with optional multi-criteria filtering, sorting, and pagination")
    public PagedResponse<MaintenanceResponse> list(
            @RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) MaintenanceStatus status,
            @RequestParam(required = false) MaintenanceCategory category,
            @RequestParam(required = false) MaintenancePriority priority,
            @RequestParam(defaultValue = "0")         int page,
            @RequestParam(defaultValue = "20")        int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc")      String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        return maintenanceService.findAll(propertyId, status, category, priority,
                PageRequest.of(page, size, sort));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a maintenance request by ID")
    public MaintenanceResponse getById(@PathVariable Long id) {
        return maintenanceService.findById(id);
    }

    @GetMapping("/by-unit/{unitId}")
    @Operation(summary = "List all maintenance requests for a unit")
    public PagedResponse<MaintenanceResponse> byUnit(
            @PathVariable Long unitId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return maintenanceService.findByUnit(unitId, PageRequest.of(page, size,
                Sort.by("createdAt").descending()));
    }

    @GetMapping("/by-tenant/{tenantId}")
    @Operation(summary = "List all maintenance requests submitted by a tenant")
    public PagedResponse<MaintenanceResponse> byTenant(
            @PathVariable Long tenantId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return maintenanceService.findByTenant(tenantId, PageRequest.of(page, size,
                Sort.by("createdAt").descending()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit a new maintenance request — automatically assigns vendor via category strategy")
    public MaintenanceResponse submit(@RequestBody @Valid CreateMaintenanceRequest request) {
        return maintenanceService.submit(request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    @Operation(summary = "Update maintenance request status (e.g. IN_PROGRESS → COMPLETED)")
    public MaintenanceResponse updateStatus(
            @PathVariable Long id,
            @RequestParam MaintenanceStatus status,
            @RequestParam(required = false) Double actualCost) {
        return maintenanceService.updateStatus(id, status, actualCost);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a maintenance request (admin only)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        maintenanceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
