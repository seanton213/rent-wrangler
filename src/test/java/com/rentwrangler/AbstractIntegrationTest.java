package com.rentwrangler;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Base class for full-stack integration tests.
 *
 * <h3>Infrastructure</h3>
 * <ul>
 *   <li><strong>PostgreSQL</strong> via Testcontainers — a real database running in Docker,
 *       auto-wired through {@code @ServiceConnection}. Flyway migrations run on startup,
 *       giving every test class a clean, fully-migrated schema.</li>
 *   <li><strong>WireMock</strong> — stubs the external address validation service so tests
 *       are hermetic and do not require network access.</li>
 * </ul>
 *
 * <h3>Why Testcontainers?</h3>
 * Using a real database catches issues that an in-memory H2 database would hide:
 * PostgreSQL-specific SQL (e.g. {@code BIGSERIAL}, {@code JSONB}), Flyway migration
 * compatibility, Hibernate dialect behaviour, and index usage.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        stubAddressValidation();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("app.address-validation.base-url", wireMock::baseUrl);
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) wireMock.stop();
    }

    /**
     * Default stub: any address validation request returns a successful, standardized response.
     * Individual tests can override this with {@code wireMock.stubFor(...)}.
     */
    protected static void stubAddressValidation() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/addresses/validate"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "valid": true,
                                    "standardizedStreetAddress": "100 NW Test Ave",
                                    "city": "Portland",
                                    "state": "OR",
                                    "zipCode": "97201",
                                    "county": "Multnomah",
                                    "deliverable": true
                                }
                                """)));
    }

    /** Re-stubs the address validation service to simulate unavailability. */
    protected static void stubAddressValidationUnavailable() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/addresses/validate"))
                .willReturn(aResponse().withStatus(503)));
    }

    /** Resets all WireMock stubs to the defaults. */
    protected static void resetWireMockStubs() {
        wireMock.resetAll();
        stubAddressValidation();
    }
}
