package com.rentwrangler.controller;

import com.rentwrangler.domain.enums.LeaseStatus;
import com.rentwrangler.dto.request.LeaseRequest;
import com.rentwrangler.dto.response.LeaseResponse;
import com.rentwrangler.dto.response.PagedResponse;
import com.rentwrangler.service.LeaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/leases")
@RequiredArgsConstructor
@Tag(name = "Leases", description = "Manage lease agreements between tenants and units")
public class LeaseController {

    private final LeaseService leaseService;

    @GetMapping
    @Operation(summary = "List all leases with sorting and pagination")
    public PagedResponse<LeaseResponse> list(
            @RequestParam(defaultValue = "0")         int page,
            @RequestParam(defaultValue = "20")        int size,
            @RequestParam(defaultValue = "startDate") String sortBy,
            @RequestParam(defaultValue = "desc")      String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        return leaseService.findAll(PageRequest.of(page, size, sort));
    }

    @GetMapping("/expiring")
    @Operation(summary = "List active leases expiring within N days")
    public List<LeaseResponse> expiring(@RequestParam(defaultValue = "30") int days) {
        return leaseService.findExpiringWithin(days);
    }

    @GetMapping("/by-property/{propertyId}")
    @Operation(summary = "List leases for all units in a property")
    public PagedResponse<LeaseResponse> byProperty(
            @PathVariable Long propertyId,
            @RequestParam(defaultValue = "ACTIVE") LeaseStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return leaseService.findByProperty(propertyId, status, PageRequest.of(page, size));
    }

    @GetMapping("/by-tenant/{tenantId}")
    @Operation(summary = "List all leases for a tenant (current and historical)")
    public PagedResponse<LeaseResponse> byTenant(
            @PathVariable Long tenantId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return leaseService.findByTenant(tenantId, PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a lease by ID")
    public LeaseResponse getById(@PathVariable Long id) {
        return leaseService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Create a lease (unit must not have an active lease)")
    public LeaseResponse create(@RequestBody @Valid LeaseRequest request) {
        return leaseService.create(request);
    }

    @PatchMapping("/{id}/terminate")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Terminate an active lease and set unit back to VACANT")
    public LeaseResponse terminate(@PathVariable Long id,
                                   @RequestParam(required = false) String reason) {
        return leaseService.terminate(id, reason);
    }
}
