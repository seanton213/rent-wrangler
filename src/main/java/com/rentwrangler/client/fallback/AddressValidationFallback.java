package com.rentwrangler.client.fallback;

import com.rentwrangler.client.AddressValidationClient;
import com.rentwrangler.client.dto.AddressValidationRequest;
import com.rentwrangler.client.dto.AddressValidationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Resilience4j fallback for the address validation service.
 *
 * <p>When the circuit is open or the upstream service is unavailable,
 * we fail open: we return a response that marks the address as valid
 * but unverified. This allows property creation to proceed rather than
 * being blocked by a non-critical downstream dependency.
 */
@Slf4j
@Component
public class AddressValidationFallback implements FallbackFactory<AddressValidationClient> {

    @Override
    public AddressValidationClient create(Throwable cause) {
        return request -> {
            log.warn("Address validation service unavailable — failing open. Cause: {}", cause.getMessage());
            return AddressValidationResponse.builder()
                    .valid(true)                          // fail open
                    .standardizedStreetAddress(request.getStreetAddress())
                    .city(request.getCity())
                    .state(request.getState())
                    .zipCode(request.getZipCode())
                    .deliverable(false)                   // flag as unverified
                    .build();
        };
    }
}
