-- ============================================================
-- Rent Wrangler — Initial Schema
-- ============================================================

-- Properties: buildings or complexes under management
CREATE TABLE properties (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(255)        NOT NULL,
    street_address VARCHAR(255)       NOT NULL,
    city          VARCHAR(100)        NOT NULL,
    state         CHAR(2)             NOT NULL,
    zip_code      VARCHAR(10)         NOT NULL,
    property_type VARCHAR(30)         NOT NULL,  -- RESIDENTIAL, COMMERCIAL, MIXED_USE
    status        VARCHAR(20)         NOT NULL DEFAULT 'ACTIVE',
    total_units   INT                 NOT NULL DEFAULT 1,
    year_built    INT,
    notes         TEXT,
    created_at    TIMESTAMP           NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP           NOT NULL DEFAULT NOW(),
    version       BIGINT              NOT NULL DEFAULT 0
);

CREATE INDEX idx_properties_status       ON properties (status);
CREATE INDEX idx_properties_type         ON properties (property_type);
CREATE INDEX idx_properties_zip          ON properties (zip_code);
CREATE INDEX idx_properties_city_state   ON properties (city, state);

-- Units: individual rentable spaces within a property
CREATE TABLE units (
    id              BIGSERIAL PRIMARY KEY,
    property_id     BIGINT        NOT NULL REFERENCES properties (id),
    unit_number     VARCHAR(20)   NOT NULL,
    floor           INT,
    bedrooms        INT           NOT NULL DEFAULT 1,
    bathrooms       NUMERIC(3,1)  NOT NULL DEFAULT 1.0,
    square_footage  INT,
    status          VARCHAR(20)   NOT NULL DEFAULT 'VACANT',  -- VACANT, OCCUPIED, MAINTENANCE, OFFLINE
    monthly_rent    NUMERIC(10,2) NOT NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    version         BIGINT        NOT NULL DEFAULT 0,
    UNIQUE (property_id, unit_number)
);

CREATE INDEX idx_units_property_id ON units (property_id);
CREATE INDEX idx_units_status      ON units (status);
CREATE INDEX idx_units_rent        ON units (monthly_rent);

-- Tenants: individuals who hold or have held leases
CREATE TABLE tenants (
    id                    BIGSERIAL PRIMARY KEY,
    first_name            VARCHAR(100)  NOT NULL,
    last_name             VARCHAR(100)  NOT NULL,
    email                 VARCHAR(255)  NOT NULL UNIQUE,
    phone                 VARCHAR(20),
    date_of_birth         DATE,
    -- government_id stores AES-256-GCM encrypted SSN/DL — never stored in plaintext
    government_id_encrypted VARCHAR(512) NOT NULL,
    emergency_contact_name  VARCHAR(200),
    emergency_contact_phone VARCHAR(20),
    created_at            TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP     NOT NULL DEFAULT NOW(),
    version               BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_tenants_email     ON tenants (email);
CREATE INDEX idx_tenants_last_name ON tenants (last_name);

-- Leases: agreement binding a tenant to a unit for a period
CREATE TABLE leases (
    id               BIGSERIAL PRIMARY KEY,
    unit_id          BIGINT        NOT NULL REFERENCES units (id),
    tenant_id        BIGINT        NOT NULL REFERENCES tenants (id),
    start_date       DATE          NOT NULL,
    end_date         DATE          NOT NULL,
    monthly_rent     NUMERIC(10,2) NOT NULL,
    security_deposit NUMERIC(10,2) NOT NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING', -- PENDING, ACTIVE, EXPIRED, TERMINATED
    termination_date DATE,
    termination_reason TEXT,
    created_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    version          BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_leases_unit_id    ON leases (unit_id);
CREATE INDEX idx_leases_tenant_id  ON leases (tenant_id);
CREATE INDEX idx_leases_status     ON leases (status);
CREATE INDEX idx_leases_end_date   ON leases (end_date);

-- Maintenance requests: service tickets raised for units
CREATE TABLE maintenance_requests (
    id               BIGSERIAL PRIMARY KEY,
    unit_id          BIGINT        NOT NULL REFERENCES units (id),
    tenant_id        BIGINT        REFERENCES tenants (id),  -- null if raised by staff
    category         VARCHAR(30)   NOT NULL,  -- PLUMBING, ELECTRICAL, HVAC, GENERAL, PEST_CONTROL
    priority         VARCHAR(20)   NOT NULL DEFAULT 'NORMAL',  -- LOW, NORMAL, HIGH, EMERGENCY
    status           VARCHAR(20)   NOT NULL DEFAULT 'OPEN',    -- OPEN, ASSIGNED, IN_PROGRESS, COMPLETED, CANCELLED
    title            VARCHAR(255)  NOT NULL,
    description      TEXT          NOT NULL,
    vendor_name      VARCHAR(200),
    vendor_contact   VARCHAR(100),
    estimated_cost   NUMERIC(10,2),
    actual_cost      NUMERIC(10,2),
    sla_deadline     TIMESTAMP,
    assigned_at      TIMESTAMP,
    completed_at     TIMESTAMP,
    created_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    version          BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_maint_unit_id   ON maintenance_requests (unit_id);
CREATE INDEX idx_maint_tenant_id ON maintenance_requests (tenant_id);
CREATE INDEX idx_maint_status    ON maintenance_requests (status);
CREATE INDEX idx_maint_category  ON maintenance_requests (category);
CREATE INDEX idx_maint_priority  ON maintenance_requests (priority);
CREATE INDEX idx_maint_created   ON maintenance_requests (created_at DESC);
