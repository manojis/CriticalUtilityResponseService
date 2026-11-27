-- Demo seed data. Loaded ONLY with the `demo` Spring profile (spring.flyway.locations += classpath:db/seed).
-- Idempotent: re-running (Flyway re-applies repeatable migrations when this file changes) upserts the same rows.
-- Password for every demo user: "password" (BCrypt, strength 10). NEVER enable in production.

INSERT INTO app_user (username, name, password_hash, user_type) VALUES
  ('alice',       'Alice Smith',    '$2a$10$N0jCfYz6SQM7VbJHv3CpeO4mFBrOhFj823qkahHdDRNHH7XkvEYC2', 'INDIVIDUAL'),
  ('metro_city',  'Metro City',     '$2a$10$N0jCfYz6SQM7VbJHv3CpeO4mFBrOhFj823qkahHdDRNHH7XkvEYC2', 'MUNICIPALITY'),
  ('fema_atl',    'FEMA Region IV', '$2a$10$N0jCfYz6SQM7VbJHv3CpeO4mFBrOhFj823qkahHdDRNHH7XkvEYC2', 'GOVERNMENT_AGENCY'),
  ('acme_rescue', 'Acme Rescue Co', '$2a$10$N0jCfYz6SQM7VbJHv3CpeO4mFBrOhFj823qkahHdDRNHH7XkvEYC2', 'COMPANY')
AS new
ON DUPLICATE KEY UPDATE name = new.name, password_hash = new.password_hash, user_type = new.user_type;

INSERT INTO individual (username, job_title, hire_date) VALUES
  ('alice', 'Emergency Coordinator', '2015-03-01')
AS new
ON DUPLICATE KEY UPDATE job_title = new.job_title, hire_date = new.hire_date;

INSERT INTO municipality (username, municipality_category) VALUES
  ('metro_city', 'City')
AS new
ON DUPLICATE KEY UPDATE municipality_category = new.municipality_category;

INSERT INTO government_agency (username, agency_name_local_office) VALUES
  ('fema_atl', 'FEMA Region IV - Atlanta')
AS new
ON DUPLICATE KEY UPDATE agency_name_local_office = new.agency_name_local_office;

INSERT INTO company (username, headquarters, num_employees) VALUES
  ('acme_rescue', 'Atlanta, GA', 250)
AS new
ON DUPLICATE KEY UPDATE headquarters = new.headquarters, num_employees = new.num_employees;

INSERT INTO resource (resource_id, owner_username, resource_name, model, primary_esf, cost, cost_unit,
                      latitude, longitude, max_distance_km) VALUES
  (1001, 'acme_rescue', 'Hydraulic Crane',        'XL-200',     3, 150.00, 'Hour', 33.770000, -84.390000, 120.00),
  (1002, 'acme_rescue', 'Rescue Boat',            'RB-12',      9, 400.00, 'Day',  33.755000, -84.410000, NULL),
  (1003, 'metro_city',  'Water Tanker Truck',     'WT-5000',    1, 300.00, 'Day',  33.720000, -84.360000, 80.00),
  (1004, 'fema_atl',    'Mobile Command Center',  NULL,         5, 900.00, 'Week', 33.800000, -84.330000, NULL),
  (1005, 'alice',       'Portable Generator',     'PG-7500',   12,  75.50, 'Day',  33.749000, -84.388000, 25.00),
  (1006, 'metro_city',  'Field Medical Tent',     'MT-20',      8,  50.00, 'Each', 34.050000, -84.200000, 50.00)
AS new
ON DUPLICATE KEY UPDATE owner_username = new.owner_username, resource_name = new.resource_name, model = new.model,
  primary_esf = new.primary_esf, cost = new.cost, cost_unit = new.cost_unit, latitude = new.latitude,
  longitude = new.longitude, max_distance_km = new.max_distance_km;

INSERT INTO resource_capability (resource_id, capability) VALUES
  (1001, 'Lift 20t'),
  (1001, 'Night ops'),
  (1002, 'Swift water rescue'),
  (1002, 'Carries 8 people'),
  (1003, '5000 gallons'),
  (1004, 'Satellite uplink'),
  (1005, '7.5 kW output'),
  (1006, 'Triage'),
  (1006, '20 beds')
AS new
ON DUPLICATE KEY UPDATE capability = new.capability;

INSERT INTO additional_esf (resource_id, esf_number) VALUES
  (1001, 1),
  (1001, 7),
  (1002, 13),
  (1003, 11),
  (1004, 2),
  (1006, 6)
AS new
ON DUPLICATE KEY UPDATE esf_number = new.esf_number;

INSERT INTO incident (incident_id, owner_username, declaration_abbreviation, incident_date, description,
                      latitude, longitude) VALUES
  ('MD-1', 'alice', 'MD', '2026-11-01', 'Flooding in downtown Atlanta', 33.749000, -84.388000)
AS new
ON DUPLICATE KEY UPDATE owner_username = new.owner_username, declaration_abbreviation = new.declaration_abbreviation,
  incident_date = new.incident_date, description = new.description, latitude = new.latitude,
  longitude = new.longitude;

UPDATE incident_sequence SET next_value = GREATEST(next_value, 2) WHERE declaration_abbreviation = 'MD';
