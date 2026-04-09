package com.rentwrangler.service;

import com.rentwrangler.client.AddressValidationClient;
import com.rentwrangler.client.dto.AddressValidationRequest;
import com.rentwrangler.client.dto.AddressValidationResponse;
import com.rentwrangler.context.RequestContext;
import com.rentwrangler.domain.entity.Property;
import com.rentwrangler.domain.enums.PropertyStatus;
import com.rentwrangler.domain.enums.PropertyType;
import com.rentwrangler.dto.request.PropertyRequest;
import com.rentwrangler.dto.response.PagedResponse;
import com.rentwrangler.dto.response.PropertyResponse;
import com.rentwrangler.exception.ResourceNotFoundException;
import com.rentwrangler.persistence.PropertyPersistenceService;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyService {

    private final PropertyPersistenceService persistenceService;
    private final AddressValidationClient addressValidationClient;
    private final RequestContext requestContext;

    public PagedResponse<PropertyResponse> findAll(
            PropertyStatus status, PropertyType type, Pageable pageable) {

        Page<Property> page;
        if (status != null && type != null) {
            page = persistenceService.findByTypeAndStatus(type, status, pageable);
        } else if (status != null) {
            page = persistenceService.findByStatus(status, pageable);
        } else {
            page = persistenceService.findAll(pageable);
        }
        return PagedResponse.from(page.map(PropertyResponse::from));
    }

    public PropertyResponse findById(Long id) {
        return persistenceService.findById(id)
                .map(PropertyResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Property", id));
    }

    public PagedResponse<PropertyResponse> findWithVacantUnits(Pageable pageable) {
        return PagedResponse.from(persistenceService.findWithVacantUnits(pageable)
                .map(PropertyResponse::from));
    }

    /**
     * Creates a property after validating the address against the external
     * address validation service (WireMock in dev/test, real service in prod).
     *
     * <p>The {@code @Retry} annotation wraps the entire method in a Resilience4j
     * retry loop for transient network errors. The circuit breaker on the Feign
     * client handles sustained outages.
     */
    @Transactional
    @Retry(name = "address-validation", fallbackMethod = "createWithoutValidation")
    public PropertyResponse create(PropertyRequest request) {
        log.info("[{}] Creating property '{}' for user '{}'",
                requestContext.getRequestId(), request.getName(), requestContext.getUsername());

        AddressValidationResponse validation = addressValidationClient.validateAddress(
                AddressValidationRequest.builder()
                        .streetAddress(request.getStreetAddress())
                        .city(request.getCity())
                        .state(request.getState())
                        .zipCode(request.getZipCode())
                        .build());

        if (!validation.isValid()) {
            throw new IllegalArgumentException(
                    "Address validation failed: " + request.getStreetAddress());
        }

        Property property = Property.builder()
                .name(request.getName())
                .streetAddress(validation.getStandardizedStreetAddress() != null
                        ? validation.getStandardizedStreetAddress()
                        : request.getStreetAddress())
                .city(request.getCity())
                .state(request.getState().toUpperCase())
                .zipCode(request.getZipCode())
                .propertyType(request.getPropertyType())
                .status(request.getStatus())
                .totalUnits(request.getTotalUnits())
                .yearBuilt(request.getYearBuilt())
                .notes(request.getNotes())
                .build();

        return PropertyResponse.from(persistenceService.save(property));
    }

    /** Fallback: create property without address validation when the service is unavailable. */
    @Transactional
    public PropertyResponse createWithoutValidation(PropertyRequest request, Throwable cause) {
        log.warn("[{}] Address validation unavailable ({}), proceeding without validation",
                requestContext.getRequestId(), cause.getMessage());

        Property property = Property.builder()
                .name(request.getName())
                .streetAddress(request.getStreetAddress())
                .city(request.getCity())
                .state(request.getState().toUpperCase())
                .zipCode(request.getZipCode())
                .propertyType(request.getPropertyType())
                .status(request.getStatus())
                .totalUnits(request.getTotalUnits())
                .yearBuilt(request.getYearBuilt())
                .notes(request.getNotes())
                .build();

        return PropertyResponse.from(persistenceService.save(property));
    }

    @Transactional
    public PropertyResponse update(Long id, PropertyRequest request) {
        Property property = persistenceService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", id));

        property.setName(request.getName());
        property.setStreetAddress(request.getStreetAddress());
        property.setCity(request.getCity());
        property.setState(request.getState().toUpperCase());
        property.setZipCode(request.getZipCode());
        property.setPropertyType(request.getPropertyType());
        property.setStatus(request.getStatus());
        property.setTotalUnits(request.getTotalUnits());
        property.setYearBuilt(request.getYearBuilt());
        property.setNotes(request.getNotes());

        return PropertyResponse.from(persistenceService.save(property));
    }

    @Transactional
    public void delete(Long id) {
        persistenceService.deleteById(id);
    }
}
