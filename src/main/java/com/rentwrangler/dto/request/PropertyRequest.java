package com.rentwrangler.dto.request;

import com.rentwrangler.domain.enums.PropertyStatus;
import com.rentwrangler.domain.enums.PropertyType;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class PropertyRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotBlank
    @Size(max = 255)
    private String streetAddress;

    @NotBlank
    @Size(max = 100)
    private String city;

    @NotBlank
    @Size(min = 2, max = 2, message = "State must be a 2-letter code")
    private String state;

    @NotBlank
    @Pattern(regexp = "\\d{5}(-\\d{4})?", message = "Must be a valid ZIP code")
    private String zipCode;

    @NotNull
    private PropertyType propertyType;

    private PropertyStatus status = PropertyStatus.ACTIVE;

    @NotNull
    @Min(1)
    private Integer totalUnits;

    @Min(1800)
    @Max(2100)
    private Integer yearBuilt;

    private String notes;
}
