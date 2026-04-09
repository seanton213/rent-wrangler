package com.rentwrangler.dto.response;

import com.rentwrangler.domain.entity.Property;
import com.rentwrangler.domain.enums.PropertyStatus;
import com.rentwrangler.domain.enums.PropertyType;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class PropertyResponse {
    Long id;
    String name;
    String streetAddress;
    String city;
    String state;
    String zipCode;
    PropertyType propertyType;
    PropertyStatus status;
    Integer totalUnits;
    Integer yearBuilt;
    String notes;
    Instant createdAt;
    Instant updatedAt;

    public static PropertyResponse from(Property p) {
        return PropertyResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .streetAddress(p.getStreetAddress())
                .city(p.getCity())
                .state(p.getState())
                .zipCode(p.getZipCode())
                .propertyType(p.getPropertyType())
                .status(p.getStatus())
                .totalUnits(p.getTotalUnits())
                .yearBuilt(p.getYearBuilt())
                .notes(p.getNotes())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
