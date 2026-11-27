-- Reference data (FEMA Emergency Support Functions, cost units, declaration types).

INSERT INTO esf (esf_number, description) VALUES
  (1,  'Transportation'),
  (2,  'Communications'),
  (3,  'Public Works and Engineering'),
  (4,  'Firefighting'),
  (5,  'Information and Planning'),
  (6,  'Mass Care, Emergency Assistance, Temporary Housing, and Human Services'),
  (7,  'Logistics'),
  (8,  'Public Health and Medical Services'),
  (9,  'Search and Rescue'),
  (10, 'Oil and Hazardous Materials Response'),
  (11, 'Agriculture and Natural Resources'),
  (12, 'Energy'),
  (13, 'Public Safety and Security'),
  (14, 'Cross-Sector Business and Infrastructure'),
  (15, 'External Affairs');

INSERT INTO cost_unit (unit) VALUES ('Each'), ('Hour'), ('Day'), ('Week');

INSERT INTO declaration (abbreviation, name) VALUES
  ('MD', 'Major Disaster'),
  ('ED', 'Emergency'),
  ('FM', 'Fire Management Assistance'),
  ('FD', 'Federal Department');

INSERT INTO incident_sequence (declaration_abbreviation, next_value)
SELECT abbreviation, 1 FROM declaration;
