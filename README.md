# Rent Wrangler

A production-grade **Spring Boot 4** service for managing residential and commercial property portfolios. Built to demonstrate enterprise Java patterns including transparent field encryption, strategy-based vendor routing, circuit-breaking external API calls, and Caffeine caching.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Getting Started](#getting-started)
- [API Reference](#api-reference)
- [Design Decisions](#design-decisions)
  - [Transparent Field Encryption](#1-transparent-field-encryption-aes-256-gcm)
  - [Maintenance Strategy Pattern](#2-maintenance-strategy-pattern)
  - [Persistence Layer Design](#3-persistence-layer-design)
  - [Caffeine Caching](#4-caffeine-caching)
  - [Resilience (Circuit Breaker + Retry)](#5-resilience-circuit-breaker--retry--connection-pooling)
  - [Request Context](#6-request-context)
  - [Flyway Migrations](#7-database-migrations-flyway)
- [Configuration Reference](#configuration-reference)
- [Security](#security)
- [Testing](#testing)
- [Project Structure](#project-structure)

---

## Overview

**Rent Wrangler** tracks a portfolio of properties, their units, tenants, leases, and maintenance requests. When a maintenance ticket is submitted, the service automatically selects a vendor assignment strategy based on the type of work (plumbing, electrical, HVAC, etc.) and calculates SLA deadlines and cost estimates without any caller-side logic.

Property addresses are validated against an external service before being persisted. Tenant government IDs (SSN/driver's license) are encrypted at rest using AES-256-GCM and are never stored in plaintext.

---

## Architecture

### Domain Model

```
Property (1) ──── (N) Unit (1) ──── (N) Lease (N) ──── (1) Tenant
                           │
                           └──── (N) MaintenanceRequest (N) ──── (0..1) Tenant
```

### Layer Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│  HTTP Layer   (@RestController, MockMvc / real requests)            │
├─────────────────────────────────────────────────────────────────────┤
│  Service Layer  (business logic, @Retry, request context logging)   │
│        │                                │                           │
│   Feign Client              Strategy Factory                        │
│  (address validation)      (MaintenanceStrategy × 5)               │
│  [OkHttp pool + CB + Retry]  [PLUMBING/ELECTRICAL/HVAC/GENERAL/PEST]│
├─────────────────────────────────────────────────────────────────────┤
│  Persistence Layer   (*PersistenceService  @PersistenceContext)     │
│        │                       +Caffeine cache annotations          │
│   JPA Repository                                                    │
│   (@Query JPQL / Criteria API)                                      │
├─────────────────────────────────────────────────────────────────────┤
│  Hibernate Event Listeners   (PreInsert/PreUpdate/PostLoad)         │
│  AES-256-GCM  ←→  @Encrypted fields                                │
├─────────────────────────────────────────────────────────────────────┤
│  PostgreSQL  (Flyway-managed schema, proper indexing)               │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Getting Started

### Prerequisites

| Tool | Version |
|------|---------|
| Java | 24+ |
| Docker + Docker Compose | 24+ |
| (optional) Gradle | 9+ — or use the included `gradlew` |

### Run with Docker Compose (fastest)

```bash
git clone <repo-url>
cd rent-wrangler
docker compose up --build
```

This starts three containers:

| Container | Port | Purpose |
|-----------|------|---------|
| `rent-wrangler-postgres` | 5432 | PostgreSQL 16 — Flyway runs migrations on app startup |
| `rent-wrangler-wiremock` | 9090 | WireMock — stubs the external address validation API |
| `rent-wrangler-app` | 8080 | Spring Boot application |

Once healthy:

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/api-docs
- **Actuator health**: http://localhost:8080/actuator/health
- **Circuit breaker state**: http://localhost:8080/actuator/health (includes `circuitBreakers`)

### Run Locally

```bash
# Start only the infrastructure
docker compose up postgres wiremock -d

# Run the app
./gradlew bootRun
```

Make sure `application.yml` datasource URL points to `localhost:5432`.

### Run Tests

```bash
# Unit tests only — no Docker required
./gradlew test

# Integration tests only — requires Docker
./gradlew integrationTest

# Everything
./gradlew test integrationTest
```

Integration tests spin up a real PostgreSQL container via Testcontainers and stub the address validation API with WireMock — no manual setup needed beyond having Docker running.

---

## API Reference

### Authentication

All `/api/v1/**` endpoints require **HTTP Basic authentication**.

| Username | Password | Roles |
|----------|----------|-------|
| `admin` | `admin123` | ADMIN, MANAGER, STAFF |
| `manager` | `manager123` | MANAGER, STAFF |
| `staff` | `staff123` | STAFF |

### Authorization Matrix

| Action | STAFF | MANAGER | ADMIN |
|--------|-------|---------|-------|
| GET any resource | ✅ | ✅ | ✅ |
| POST / PUT (create & update) | ❌ | ✅ | ✅ |
| Maintenance status updates | ✅ | ✅ | ✅ |
| DELETE | ❌ | ❌ | ✅ |

### Endpoints

#### Properties

```bash
# List all (sorted by name, paginated)
GET /api/v1/properties?status=ACTIVE&type=RESIDENTIAL&page=0&size=20&sortBy=name&sortDir=asc

# Properties with at least one vacant unit
GET /api/v1/properties/vacant

# Get by ID
GET /api/v1/properties/{id}

# Create (validates address against WireMock/external service)
POST /api/v1/properties
{
  "name": "Burnside Lofts",
  "streetAddress": "1212 E Burnside St",
  "city": "Portland",
  "state": "OR",
  "zipCode": "97214",
  "propertyType": "MIXED_USE",
  "totalUnits": 12
}

# Update / Delete
PUT /api/v1/properties/{id}
DELETE /api/v1/properties/{id}   # ADMIN only
```

#### Tenants

```bash
# List / search (government ID is always masked in responses)
GET /api/v1/tenants?search=alvarez&page=0&size=20
GET /api/v1/tenants/active
GET /api/v1/tenants/expiring-leases?days=30

# Create (government ID accepted as plaintext, encrypted before storage)
POST /api/v1/tenants
{
  "firstName": "Jordan",
  "lastName": "Alvarez",
  "email": "jordan@example.com",
  "phone": "503-555-0101",
  "governmentId": "123-45-6789",
  "emergencyContactName": "Maria Alvarez",
  "emergencyContactPhone": "503-555-0110"
}
```

#### Leases

```bash
GET  /api/v1/leases?sortBy=startDate&sortDir=desc
GET  /api/v1/leases/expiring?days=30
GET  /api/v1/leases/by-property/{propertyId}?status=ACTIVE
GET  /api/v1/leases/by-tenant/{tenantId}
POST /api/v1/leases           # creates lease, sets unit to OCCUPIED
PATCH /api/v1/leases/{id}/terminate?reason=Relocated   # sets unit back to VACANT
```

#### Maintenance

```bash
# Multi-criteria filter (all params optional)
GET /api/v1/maintenance?propertyId=1&status=OPEN&category=PLUMBING&priority=HIGH

# Submit ticket — vendor assignment happens automatically via Strategy pattern
POST /api/v1/maintenance
{
  "unitId": 3,
  "tenantId": 1,
  "category": "PLUMBING",
  "priority": "HIGH",
  "title": "Leak under kitchen sink",
  "description": "Water pooling under sink cabinet, possibly from supply line."
}

# Response includes assignment from strategy:
# {
#   "status": "ASSIGNED",
#   "vendorName": "Pacific Plumbing & Drain",
#   "vendorContact": "503-555-7100",
#   "estimatedCost": 275.00,
#   "slaDeadline": "2026-03-19T14:00:00Z",
#   ...
# }

# Update status (STAFF and above)
PATCH /api/v1/maintenance/{id}/status?status=COMPLETED&actualCost=210.00
```

---

## Design Decisions

### 1. Transparent Field Encryption (AES-256-GCM)

Tenant government IDs are classified data. Rather than encrypting at the application layer (which would require every caller to handle ciphertext), encryption is applied **transparently** through Hibernate event listeners.

**Algorithm**: AES-256-GCM (Java `javax.crypto` — no third-party library required)
- 256-bit key stored in `application.yml` as a base64 string; should be injected from a secrets manager in production
- 12-byte random IV per encryption — two saves of the same value produce different ciphertexts (non-deterministic)
- GCM authentication tag detects tampering without a separate HMAC

**How it works**:

```
Write path:   plaintext in entity
                    │
        PreInsertEventListener / PreUpdateEventListener
                    │
              encrypt(plaintext)  → AES-256-GCM
                    │
              state[] updated     → SQL uses ciphertext
                    │
         Entity object unchanged  → caller still sees plaintext

Read path:    ciphertext in result set
                    │
             PostLoadEventListener
                    │
              decrypt(ciphertext)  → AES-256-GCM
                    │
         Entity object updated    → caller sees plaintext
```

The `@Encrypted` annotation marks fields; reflection is used to find them at runtime. This keeps entity classes clean — adding encryption to a new field requires only adding `@Encrypted`.

> **Seed data caveat**: The `V2__seed_data.sql` migration uses placeholder values for `government_id_encrypted` (`SEED_PLACEHOLDER_*`). In a real deployment, seed data would be pre-encrypted using the configured key or inserted via an application bootstrap task.

---

### 2. Maintenance Strategy Pattern

When a maintenance ticket is submitted, the system must assign a vendor, calculate costs, and set an SLA deadline. The rules differ significantly by category:

| Category | Licensed? | SLA (Emergency) | Emergency Cost |
|----------|-----------|-----------------|----------------|
| PLUMBING | ✅ | 4 hours | $450 |
| ELECTRICAL | ✅ | 2 hours | $900 |
| HVAC | ✅ (EPA cert) | 4 hours | $600+ peak |
| GENERAL | ❌ | 8 hours | $200 |
| PEST_CONTROL | ✅ | 8 hours | $500+ spread |

Each category is an independent `MaintenanceStrategy` implementation. The `MaintenanceStrategyFactory` collects all `@Component` strategy beans at startup via constructor injection:

```java
public MaintenanceStrategyFactory(List<MaintenanceStrategy> strategyList) {
    this.strategies = strategyList.stream()
        .collect(toUnmodifiableMap(MaintenanceStrategy::getCategory, identity()));
}
```

Adding a new category requires implementing the interface and annotating with `@Component` — no changes to the factory or service.

---

### 3. Persistence Layer Design

The persistence layer has two tiers:

**Tier 1 — Spring Data JPA Repositories** handle simple queries:
```java
public interface LeaseRepository extends JpaRepository<Lease, Long> {
    Page<Lease> findByStatus(LeaseStatus status, Pageable pageable);
    List<Lease> findExpiringBetween(@Param("from") LocalDate from, ...);
}
```

**Tier 2 — Persistence Services** are the sole interface to the rest of the application. They delegate simple operations to the repository and use `@PersistenceContext EntityManager` for complex queries:

```java
@PersistenceContext
private EntityManager entityManager;

// Dynamic multi-criteria filter via Criteria API
public Page<MaintenanceRequest> findByFilters(Long propertyId, MaintenanceStatus status, ...)
```

Business logic in the service layer always calls `*PersistenceService`, never a repository directly. This isolates query strategy decisions (JPQL vs Criteria API) from business logic.

---

### 4. Caffeine Caching

[Caffeine](https://github.com/ben-manes/caffeine) is the default Spring Boot cache provider; it implements the Window TinyLFU eviction algorithm, giving near-optimal hit rates with low overhead.

| Cache name | What is cached | TTL | Max entries |
|------------|----------------|-----|-------------|
| `properties` | `Optional<Property>` by ID | 15 min | 500 |
| `tenants` | `Optional<Tenant>` by ID | 15 min | 500 |
| `unit-lease-status` | `boolean hasActiveLeaseForUnit(unitId)` | 5 min | 1,000 |
| `active-lease-for-unit` | `Optional<Lease>` by unit ID | 5 min | 1,000 |
| `vacant-properties` | Paged vacancy results | 5 min | 50 |

**Eviction strategy**: entity caches (`properties`, `tenants`) use `@CacheEvict` keyed by ID on every write. Lease-status caches use `allEntries = true` because accessing a lazy-loaded `unit.id` in a SpEL key expression after transaction close is unsafe.

**Why evict rather than `@CachePut`**: `findById` returns `Optional<T>` while `save` returns `T`. Sharing one cache with two different value types causes a `ClassCastException` on retrieval. Evicting on write forces the next read to populate the cache from a canonical read path.

Cache statistics are recorded (`recordStats()`) and exposed at `/actuator/metrics`.

---

### 5. Resilience (Circuit Breaker + Retry + Connection Pooling)

The address validation Feign client is protected at three levels:

```
Service.create()
    └── @Retry(name = "address-validation")          ← 3 attempts, 500ms backoff
            └── AddressValidationClient (Feign)
                    └── Circuit Breaker               ← opens after 50% failure rate
                            └── OkHttp                ← connection pool (20 conns, 5 min TTL)
```

**Circuit breaker** (`resilience4j.circuitbreaker.instances.address-validation`):
- Slides over the last 10 calls; opens when ≥ 50% fail
- Stays open 30 seconds, then allows 3 probe calls (half-open)

**Retry** (`@Retry` on `PropertyService.create()`):
- 3 attempts with 500ms wait between attempts
- `fallbackMethod = "createWithoutValidation"` — if all retries fail, property is created without address standardisation (fail-open strategy; acceptable since address validation is a non-critical enrichment)

**OkHttp connection pool** (`FeignConfig`):
- 20 keep-alive connections, 5-minute idle eviction
- Avoids repeated TCP and TLS handshake overhead for burst traffic

**Circuit breaker fallback** (`AddressValidationFallback`):
- Returns `valid = true, deliverable = false` when the circuit is open
- Allows the request to proceed with the unverified original address

---

### 6. Request Context

`RequestContext` is a `@RequestScope` Spring bean — a new instance is created per HTTP request and destroyed when the request completes. It is populated by `RequestContextInterceptor` (a `HandlerInterceptor`) after Spring Security has processed authentication:

```
Request arrives → Spring Security → RequestContextInterceptor.preHandle()
                                        sets: requestId, username, roles, timestamp, IP
                                        (honours X-Request-ID header for distributed tracing)
                                    → Controller → Service
                                        requestContext.getUsername() / .getRequestId()
                                    → X-Request-ID echoed in response header
```

Services use the context for structured logging (`[requestId] action by username`) and audit trails without passing user identity through every method signature.

---

### 7. Database Migrations (Flyway)

Flyway runs automatically on application startup. Migration scripts live in `src/main/resources/db/migration/` and follow the `V{version}__{description}.sql` naming convention.

| Script | Content |
|--------|---------|
| `V1__create_tables.sql` | Full schema: all tables with proper column types, NOT NULL constraints, UNIQUE constraints, and indexes |
| `V2__seed_data.sql` | Sample data for four properties, units, tenants, leases, and maintenance tickets |

All tables have:
- `BIGSERIAL` primary keys
- `version BIGINT` for optimistic locking (`@Version` in JPA)
- `created_at` / `updated_at` timestamps
- Indexes on every foreign key and common filter/sort columns

To add a new migration: create `V3__description.sql` in the same directory. Flyway will apply it on next startup and record the checksum in `flyway_schema_history`.

---

## Configuration Reference

All configuration lives in `src/main/resources/application.yml`. Key values overridable via environment variables (Docker Compose injects these):

| Property | Env Var | Default | Description |
|----------|---------|---------|-------------|
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | `localhost:5432` | JDBC URL |
| `app.encryption.key` | `APP_ENCRYPTION_KEY` | (test key) | Base64-encoded 32-byte AES key |
| `app.address-validation.base-url` | `APP_ADDRESS_VALIDATION_BASE_URL` | `localhost:9090` | WireMock/real address API |

> **Production**: The encryption key should be injected from AWS Secrets Manager, HashiCorp Vault, or a Kubernetes Secret — never committed to source control.

---

## Security

HTTP Basic authentication is used for simplicity. Users are defined in-memory in `SecurityConfig`; replace with a `JdbcUserDetailsManager` or OAuth2/OIDC in production.

**Stateless sessions** (`SessionCreationPolicy.STATELESS`) — no server-side session state.

**Method security** (`@EnableMethodSecurity`) — `@PreAuthorize` annotations on controller methods enforce role checks at the method level, in addition to the URL-level rules in `SecurityFilterChain`.

---

## Testing

### Testing Stack

| Library | Version | Role |
|---------|---------|------|
| **JUnit 5 (Jupiter)** | 5.11 | Test runner — `@Test`, `@BeforeEach`, `@Testcontainers` |
| **AssertJ** | 3.26 | Fluent assertions — `assertThat(...).isEqualTo(...)` |
| **Mockito** | 5.x | Mocking — `@Mock`, `@InjectMocks`, `given().willReturn()` |
| **Spring Security Test** | 7.x | `@WithMockUser`, `httpBasic()` |
| **Testcontainers** | 2.0 | Real PostgreSQL 16 container for integration tests |
| **WireMock** | 3.13 | Stubs the address validation API in integration tests |

All of the above (except Testcontainers and WireMock) are bundled with `spring-boot-starter-test`.

### Test Taxonomy

```
src/test/java/com/rentwrangler/
│
├── encryption/
│   └── AesEncryptionServiceTest           ← Unit: encrypt/decrypt round-trip, IV randomness,
│                                            tamper detection, wrong key size rejection
├── strategy/
│   └── MaintenanceStrategyFactoryTest     ← Unit: strategy routing, SLA escalation,
│                                            priority override rules per category
├── service/
│   ├── PropertyServiceTest                ← Unit (Mockito): address validation integration,
│   │                                        fallback behaviour, cache-eviction delegation
│   ├── TenantServiceTest                  ← Unit: search delegation, government ID masking,
│   │                                        duplicate email rejection, active/expiring queries
│   ├── LeaseServiceTest                   ← Unit: conflict detection, unit status transitions,
│   │                                        lease termination guards
│   └── MaintenanceServiceTest             ← Unit: strategy factory called with right category,
│                                            assignment data applied to entity, status updates
├── controller/
│   ├── PropertyControllerTest             ← @WebMvcTest: HTTP status codes, pagination params,
│   │                                        role-based access (403/401), validation errors (400)
│   ├── TenantControllerTest               ← @WebMvcTest: search param, masked government ID,
│   │                                        active/expiring-leases endpoints, role checks
│   ├── MaintenanceControllerTest          ← @WebMvcTest: filter params, submit/status-update,
│   │                                        role restrictions on DELETE
│   └── LeaseControllerTest                ← @WebMvcTest: conflict → 409, terminate, expiring list
│
└── integration/                           ← @SpringBootTest + Testcontainers + WireMock
    ├── AbstractIntegrationTest            ← Base: PostgreSQL @ServiceConnection, WireMock setup
    ├── EncryptionIntegrationTest          ← Verifies DB column is ciphertext via raw JDBC,
    │                                        non-deterministic encryption, post-load decryption
    ├── PropertyServiceIntegrationTest     ← Full round-trip: WireMock called, standardized
    │                                        address persisted, fallback on 503, cache eviction
    ├── TenantServiceIntegrationTest       ← DISTINCT JOIN active-tenant query, date-windowed
    │                                        expiring-lease query, case-insensitive search, cache
    └── LeasePersistenceIntegrationTest    ← JPQL queries, expiring-lease window, cache eviction
                                             verified by observing state before/after mutation
```

### Running a Specific Test Class

```bash
./gradlew test --tests "com.rentwrangler.integration.EncryptionIntegrationTest"
```

### Running Only Unit Tests (no Docker required)

```bash
./gradlew test
```

### Running Only Integration Tests (requires Docker)

```bash
./gradlew integrationTest
```

---

## Project Structure

```
src/main/java/com/rentwrangler/
├── RentWranglerApplication.java
├── annotation/
│   └── Encrypted.java               @Encrypted — marks fields for transparent AES encryption
├── config/
│   ├── CacheConfig.java             Caffeine cache manager (5 named caches)
│   ├── FeignConfig.java             OkHttp client with connection pool
│   ├── HibernateListenerConfig.java Registers EncryptionEventListener via @PostConstruct
│   ├── SecurityConfig.java          SecurityFilterChain, InMemoryUserDetailsManager
│   └── WebMvcConfig.java            Registers RequestContextInterceptor
├── context/
│   ├── RequestContext.java          @RequestScope bean: requestId, username, roles, IP
│   └── RequestContextInterceptor.java Populates RequestContext after auth
├── encryption/
│   ├── EncryptionService.java       Interface
│   └── AesEncryptionService.java    AES-256-GCM impl (javax.crypto, no dependencies)
├── event/
│   └── EncryptionEventListener.java PreInsert/PreUpdate/PostLoad Hibernate listener
├── domain/
│   ├── entity/                      Property, Unit, Tenant (@Encrypted SSN), Lease,
│   │                                 MaintenanceRequest
│   └── enums/                       PropertyType/Status, UnitStatus, LeaseStatus,
│                                     MaintenanceCategory/Status/Priority
├── repository/                      Spring Data JPA interfaces (basic CRUD + @Query)
├── persistence/
│   ├── *PersistenceService.java     Interfaces — the only way services touch the DB
│   └── impl/
│       └── *PersistenceServiceImpl  @PersistenceContext EntityManager + cache annotations
├── client/
│   ├── AddressValidationClient.java @FeignClient with fallback + circuit breaker
│   ├── dto/                         AddressValidationRequest/Response
│   └── fallback/                    AddressValidationFallback (FallbackFactory)
├── strategy/
│   ├── MaintenanceStrategy.java     Interface: assign(), estimateCost(), getSlaHours()
│   ├── impl/                        Plumbing, Electrical, HVAC, General, PestControl
│   ├── MaintenanceStrategyFactory   Map<Category, Strategy> built at startup
│   └── dto/MaintenanceAssignment    Immutable (@Value) result of strategy execution
├── service/                         Business logic; injects PersistenceService + RequestContext
├── dto/
│   ├── request/                     PropertyRequest, TenantRequest, LeaseRequest,
│   │                                 CreateMaintenanceRequest
│   └── response/                    *Response (static ::from mappers), PagedResponse<T>
├── controller/                      @RestController per aggregate; all GETs paginated
└── exception/
    ├── GlobalExceptionHandler.java  RFC 9457 ProblemDetail responses
    └── *Exception.java              ResourceNotFoundException, LeaseConflictException, etc.

src/main/resources/
├── application.yml
└── db/migration/
    ├── V1__create_tables.sql        Schema + indexes
    └── V2__seed_data.sql            Sample portfolio data

wiremock/mappings/
└── address-validation.json         Stub: valid address + INVALID-prefix rejection
```
