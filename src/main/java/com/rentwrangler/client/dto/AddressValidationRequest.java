package com.rentwrangler.client.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AddressValidationRequest {
    String streetAddress;
    String city;
    String state;
    String zipCode;
}
