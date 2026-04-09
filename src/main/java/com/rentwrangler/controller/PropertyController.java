package com.rentwrangler.controller;

import com.rentwrangler.domain.enums.PropertyStatus;
import com.rentwrangler.domain.enums.PropertyType;
import com.rentwrangler.dto.request.PropertyRequest;
import com.rentwrangler.dto.response.PagedResponse;
import com.rentwrangler.dto.response.PropertyResponse;
import com.rentwrangler.service.PropertyService;
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
@RequestMapping("/api/v1/properties")
@RequiredArgsConstructor
@Tag(name = "Properties", description = "Manage properties in the Rent Wrangler portfolio")
public class PropertyController {

    private final PropertyService propertyService;

    @GetMapping
    @Operation(summary = "List all properties with optional filtering, sorting, and pagination")
    public PagedResponse<PropertyResponse> list(
            @RequestParam(required = false) PropertyStatus status,
            @RequestParam(required = false) PropertyType type,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        return propertyService.findAll(status, type, PageRequest.of(page, size, sort));
    }

    @GetMapping("/vacant")
    @Operation(summary = "List properties that have at least one vacant unit")
    public PagedResponse<PropertyResponse> listWithVacancies(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return propertyService.findWithVacantUnits(PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a property by ID")
    public PropertyResponse getById(@PathVariable Long id) {
        return propertyService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Create a new property (validates address via external service)")
    public PropertyResponse create(@RequestBody @Valid PropertyRequest request) {
        return propertyService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Update a property")
    public PropertyResponse update(@PathVariable Long id,
                                   @RequestBody @Valid PropertyRequest request) {
        return propertyService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a property (admin only)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        propertyService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
