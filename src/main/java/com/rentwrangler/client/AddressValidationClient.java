package com.rentwrangler.client;

import com.rentwrangler.client.dto.AddressValidationRequest;
import com.rentwrangler.client.dto.AddressValidationResponse;
import com.rentwrangler.client.fallback.AddressValidationFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * OpenFeign client for the external address validation service.
 *
 * <p>Transport: OkHttp with a pooled {@link okhttp3.ConnectionPool} (configured in
 * {@link com.rentwrangler.config.FeignConfig}).
 *
 * <p>Resilience: wrapped by Resilience4j via
 * {@code spring.cloud.openfeign.circuitbreaker.enabled=true} in {@code application.yml}.
 * The circuit breaker instance name matches the client {@code name} ("address-validation"),
 * which maps to the Resilience4j config block {@code resilience4j.circuitbreaker.instances.address-validation}.
 *
 * <p>Fallback: {@link AddressValidationFallback} provides a fail-open response when the
 * circuit is open or a non-retryable error occurs.
 */
@FeignClient(
        name = "address-validation",
        url = "${app.address-validation.base-url}",
        fallbackFactory = AddressValidationFallback.class
)
public interface AddressValidationClient {

    @PostMapping("/api/v1/addresses/validate")
    AddressValidationResponse validateAddress(@RequestBody AddressValidationRequest request);
}
