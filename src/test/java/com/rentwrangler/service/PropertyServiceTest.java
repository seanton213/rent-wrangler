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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PropertyServiceTest {

    @Mock private PropertyPersistenceService persistenceService;
    @Mock private AddressValidationClient addressValidationClient;
    @Mock private RequestContext requestContext;

    @InjectMocks
    private PropertyService propertyService;

    private Property sampleProperty;

    @BeforeEach
    void setUp() {
        sampleProperty = Property.builder()
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
                .version(0L)
                .build();
    }

    // -----------------------------------------------------------------------
    // findAll
    // -----------------------------------------------------------------------

    @Test
    void findAll_withNoFilters_delegatesToFindAll() {
        Pageable pageable = PageRequest.of(0, 20);
        given(persistenceService.findAll(pageable))
                .willReturn(new PageImpl<>(List.of(sampleProperty)));

        PagedResponse<PropertyResponse> result = propertyService.findAll(null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Riverside Apartments");
        verify(persistenceService).findAll(pageable);
        verify(persistenceService, never()).findByStatus(any(), any());
    }

    @Test
    void findAll_withStatusFilter_delegatesToFindByStatus() {
        Pageable pageable = PageRequest.of(0, 20);
        given(persistenceService.findByStatus(PropertyStatus.ACTIVE, pageable))
                .willReturn(new PageImpl<>(List.of(sampleProperty)));

        propertyService.findAll(PropertyStatus.ACTIVE, null, pageable);

        verify(persistenceService).findByStatus(PropertyStatus.ACTIVE, pageable);
    }

    @Test
    void findAll_withBothFilters_delegatesToFindByTypeAndStatus() {
        Pageable pageable = PageRequest.of(0, 20);
        given(persistenceService.findByTypeAndStatus(PropertyType.RESIDENTIAL, PropertyStatus.ACTIVE, pageable))
                .willReturn(new PageImpl<>(List.of(sampleProperty)));

        propertyService.findAll(PropertyStatus.ACTIVE, PropertyType.RESIDENTIAL, pageable);

        verify(persistenceService).findByTypeAndStatus(PropertyType.RESIDENTIAL, PropertyStatus.ACTIVE, pageable);
    }

    // -----------------------------------------------------------------------
    // findById
    // -----------------------------------------------------------------------

    @Test
    void findById_whenFound_returnsResponse() {
        given(persistenceService.findById(1L)).willReturn(Optional.of(sampleProperty));

        PropertyResponse response = propertyService.findById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Riverside Apartments");
    }

    @Test
    void findById_whenNotFound_throwsResourceNotFoundException() {
        given(persistenceService.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> propertyService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // -----------------------------------------------------------------------
    // create
    // -----------------------------------------------------------------------

    @Test
    void create_withValidAddress_savesAndReturnsResponse() {
        PropertyRequest request = buildPropertyRequest();
        given(addressValidationClient.validateAddress(any(AddressValidationRequest.class)))
                .willReturn(validAddressResponse());
        given(persistenceService.save(any(Property.class))).willReturn(sampleProperty);

        PropertyResponse response = propertyService.create(request);

        assertThat(response.getName()).isEqualTo("Riverside Apartments");
        verify(persistenceService).save(any(Property.class));
    }

    @Test
    void create_withInvalidAddress_throwsIllegalArgument() {
        PropertyRequest request = buildPropertyRequest();
        given(addressValidationClient.validateAddress(any(AddressValidationRequest.class)))
                .willReturn(AddressValidationResponse.builder().valid(false).build());

        assertThatThrownBy(() -> propertyService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("validation failed");

        verify(persistenceService, never()).save(any());
    }

    @Test
    void create_usesStandardizedAddressFromValidationService() {
        PropertyRequest request = buildPropertyRequest();
        AddressValidationResponse validation = AddressValidationResponse.builder()
                .valid(true)
                .standardizedStreetAddress("400 SW River Parkway")  // normalized form
                .city("Portland")
                .state("OR")
                .zipCode("97201")
                .deliverable(true)
                .build();

        given(addressValidationClient.validateAddress(any())).willReturn(validation);
        given(persistenceService.save(any(Property.class))).willAnswer(inv -> {
            Property p = inv.getArgument(0);
            p.setId(1L);
            p.setCreatedAt(Instant.now());
            p.setUpdatedAt(Instant.now());
            return p;
        });

        PropertyResponse response = propertyService.create(request);

        // The standardized address from the validation service should be used
        assertThat(response.getStreetAddress()).isEqualTo("400 SW River Parkway");
    }

    // -----------------------------------------------------------------------
    // createWithoutValidation (Resilience4j retry fallback)
    // -----------------------------------------------------------------------

    @Test
    void createWithoutValidation_fallback_stillSavesProperty() {
        PropertyRequest request = buildPropertyRequest();
        given(persistenceService.save(any(Property.class))).willReturn(sampleProperty);

        PropertyResponse response = propertyService.createWithoutValidation(request,
                new RuntimeException("service unavailable"));

        assertThat(response).isNotNull();
        verify(persistenceService).save(any(Property.class));
        verify(addressValidationClient, never()).validateAddress(any());
    }

    // -----------------------------------------------------------------------
    // update
    // -----------------------------------------------------------------------

    @Test
    void update_whenFound_updatesAllFields() {
        PropertyRequest request = buildPropertyRequest();
        request.setName("Updated Name");

        given(persistenceService.findById(1L)).willReturn(Optional.of(sampleProperty));
        given(persistenceService.save(any(Property.class))).willAnswer(inv -> inv.getArgument(0));

        PropertyResponse response = propertyService.update(1L, request);

        assertThat(response.getName()).isEqualTo("Updated Name");
    }

    @Test
    void update_whenNotFound_throwsResourceNotFoundException() {
        given(persistenceService.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> propertyService.update(99L, buildPropertyRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -----------------------------------------------------------------------
    // delete
    // -----------------------------------------------------------------------

    @Test
    void delete_delegatesToPersistenceService() {
        propertyService.delete(1L);
        verify(persistenceService).deleteById(1L);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private PropertyRequest buildPropertyRequest() {
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

    private AddressValidationResponse validAddressResponse() {
        return AddressValidationResponse.builder()
                .valid(true)
                .standardizedStreetAddress("400 SW River Pkwy")
                .city("Portland")
                .state("OR")
                .zipCode("97201")
                .deliverable(true)
                .build();
    }
}
