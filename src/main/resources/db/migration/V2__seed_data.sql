-- ============================================================
-- Rent Wrangler — Seed Data
-- ============================================================

INSERT INTO properties (name, street_address, city, state, zip_code, property_type, status, total_units, year_built, notes)
VALUES
    ('Riverside Apartments', '400 SW River Pkwy', 'Portland', 'OR', '97201', 'RESIDENTIAL', 'ACTIVE', 24, 2005,  'Waterfront complex with covered parking'),
    ('Burnside Lofts',       '1212 E Burnside St', 'Portland', 'OR', '97214', 'MIXED_USE',   'ACTIVE', 12, 2018,  'Ground floor commercial, upper floors residential'),
    ('Pearl District Plaza', '1000 NW 11th Ave',   'Portland', 'OR', '97209', 'COMMERCIAL',  'ACTIVE',  6, 2010,  'Class A office space'),
    ('Hawthorne Commons',    '3300 SE Hawthorne Blvd', 'Portland', 'OR', '97214', 'RESIDENTIAL', 'ACTIVE', 18, 1998, 'Close to transit and retail');

INSERT INTO units (property_id, unit_number, floor, bedrooms, bathrooms, square_footage, status, monthly_rent)
VALUES
    -- Riverside Apartments (id=1)
    (1, '101', 1, 1, 1.0, 650,  'OCCUPIED',    1450.00),
    (1, '102', 1, 2, 1.0, 900,  'OCCUPIED',    1850.00),
    (1, '201', 2, 1, 1.0, 650,  'VACANT',      1500.00),
    (1, '202', 2, 2, 2.0, 1050, 'OCCUPIED',    2100.00),
    (1, '301', 3, 3, 2.0, 1350, 'MAINTENANCE', 2600.00),
    -- Burnside Lofts (id=2)
    (2, 'A',   1, 0, 1.0, 800,  'OCCUPIED',    2200.00),
    (2, 'B',   1, 0, 1.0, 750,  'VACANT',      2100.00),
    (2, '201', 2, 1, 1.0, 900,  'OCCUPIED',    2400.00),
    -- Pearl District Plaza (id=3)
    (3, 'Suite 100', 1, 0, 1.0, 1200, 'OCCUPIED',  3500.00),
    (3, 'Suite 200', 2, 0, 1.0, 1800, 'VACANT',    5000.00),
    -- Hawthorne Commons (id=4)
    (4, '1A', 1, 1, 1.0, 600,  'OCCUPIED',    1350.00),
    (4, '1B', 1, 2, 1.0, 850,  'OCCUPIED',    1750.00),
    (4, '2A', 2, 1, 1.0, 620,  'VACANT',      1400.00);

-- government_id values are placeholder encrypted blobs; real values injected at runtime
INSERT INTO tenants (first_name, last_name, email, phone, date_of_birth, government_id_encrypted, emergency_contact_name, emergency_contact_phone)
VALUES
    ('Jordan',  'Alvarez',  'jordan.alvarez@email.com',  '503-555-0101', '1988-03-14', 'SEED_PLACEHOLDER_1', 'Maria Alvarez',  '503-555-0110'),
    ('Casey',   'Nguyen',   'casey.nguyen@email.com',    '503-555-0202', '1995-07-22', 'SEED_PLACEHOLDER_2', 'Tam Nguyen',     '503-555-0211'),
    ('Morgan',  'Okafor',   'morgan.okafor@email.com',   '503-555-0303', '1990-11-05', 'SEED_PLACEHOLDER_3', 'Ade Okafor',     '503-555-0312'),
    ('Riley',   'Kim',      'riley.kim@email.com',       '503-555-0404', '1992-01-30', 'SEED_PLACEHOLDER_4', 'Soo Kim',        '503-555-0413'),
    ('Avery',   'Williams', 'avery.williams@email.com',  '503-555-0505', '1985-09-18', 'SEED_PLACEHOLDER_5', 'Pat Williams',   '503-555-0514'),
    ('Quinn',   'Patel',    'quinn.patel@email.com',     '503-555-0606', '1998-04-02', 'SEED_PLACEHOLDER_6', 'Priya Patel',    '503-555-0615');

INSERT INTO leases (unit_id, tenant_id, start_date, end_date, monthly_rent, security_deposit, status)
VALUES
    (1,  1, '2023-09-01', '2024-08-31', 1450.00, 1450.00, 'ACTIVE'),
    (2,  2, '2024-01-01', '2024-12-31', 1850.00, 1850.00, 'ACTIVE'),
    (4,  3, '2023-06-01', '2024-05-31', 2100.00, 2100.00, 'ACTIVE'),
    (6,  4, '2024-02-01', '2025-01-31', 2200.00, 4400.00, 'ACTIVE'),
    (8,  5, '2023-11-01', '2024-10-31', 2400.00, 2400.00, 'ACTIVE'),
    (11, 6, '2024-03-01', '2025-02-28', 1350.00, 1350.00, 'ACTIVE');

INSERT INTO maintenance_requests (unit_id, tenant_id, category, priority, status, title, description, vendor_name, estimated_cost, sla_deadline)
VALUES
    (1,  1, 'PLUMBING',     'HIGH',      'IN_PROGRESS', 'Kitchen faucet leak',      'Persistent drip from hot water handle, staining sink.',         'Pacific Plumbing Co.',    250.00, NOW() + INTERVAL '24 hours'),
    (4,  3, 'HVAC',         'NORMAL',    'OPEN',        'AC not cooling unit',      'Unit thermostat set to 72°F but temperature reaches 82°F.',     NULL,                      NULL,   NOW() + INTERVAL '48 hours'),
    (5,  NULL, 'ELECTRICAL', 'EMERGENCY', 'ASSIGNED',   'Exposed wiring in closet', 'Found during inspection — unit offline until resolved.',        'Volt Electric Services',  800.00, NOW() + INTERVAL '4 hours'),
    (2,  2, 'GENERAL',      'LOW',       'OPEN',        'Broken window screen',     'Screen on bedroom window torn; insects entering unit.',         NULL,                      NULL,   NOW() + INTERVAL '72 hours'),
    (11, 6, 'PEST_CONTROL', 'HIGH',      'OPEN',        'Cockroach infestation',    'Tenant reported multiple sightings in kitchen and bathroom.',    NULL,                      NULL,   NOW() + INTERVAL '24 hours');
