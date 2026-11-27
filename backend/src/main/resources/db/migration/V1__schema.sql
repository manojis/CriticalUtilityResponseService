-- ERMS schema v1. Forward-only: never edit this file once applied; add V<n+1>__*.sql instead.
-- All tables: InnoDB, utf8mb4, utf8mb4_0900_ai_ci (case-insensitive comparisons and LIKE).

CREATE TABLE app_user (
  username      VARCHAR(50)  NOT NULL PRIMARY KEY,
  name          VARCHAR(100) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  user_type     ENUM('INDIVIDUAL','MUNICIPALITY','GOVERNMENT_AGENCY','COMPANY') NOT NULL,
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE individual (
  username  VARCHAR(50)  NOT NULL PRIMARY KEY,
  job_title VARCHAR(100) NOT NULL,
  hire_date DATE         NOT NULL,
  CONSTRAINT fk_individual_user FOREIGN KEY (username) REFERENCES app_user(username) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE municipality (
  username              VARCHAR(50)  NOT NULL PRIMARY KEY,
  municipality_category VARCHAR(100) NOT NULL,
  CONSTRAINT fk_municipality_user FOREIGN KEY (username) REFERENCES app_user(username) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE government_agency (
  username                 VARCHAR(50)  NOT NULL PRIMARY KEY,
  agency_name_local_office VARCHAR(150) NOT NULL,
  CONSTRAINT fk_government_agency_user FOREIGN KEY (username) REFERENCES app_user(username) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE company (
  username      VARCHAR(50)  NOT NULL PRIMARY KEY,
  headquarters  VARCHAR(150) NOT NULL,
  num_employees INT UNSIGNED NOT NULL,
  CONSTRAINT fk_company_user FOREIGN KEY (username) REFERENCES app_user(username) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE esf (
  esf_number  TINYINT UNSIGNED NOT NULL PRIMARY KEY,
  description VARCHAR(120)     NOT NULL,
  CONSTRAINT uq_esf_description UNIQUE (description)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE cost_unit (
  unit VARCHAR(20) NOT NULL PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE declaration (
  abbreviation CHAR(2)      NOT NULL PRIMARY KEY,
  name         VARCHAR(100) NOT NULL,
  CONSTRAINT uq_declaration_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE incident_sequence (
  declaration_abbreviation CHAR(2)      NOT NULL PRIMARY KEY,
  next_value               INT UNSIGNED NOT NULL DEFAULT 1,
  CONSTRAINT fk_incident_sequence_declaration FOREIGN KEY (declaration_abbreviation) REFERENCES declaration(abbreviation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE incident (
  incident_id              VARCHAR(16)   NOT NULL PRIMARY KEY,
  owner_username           VARCHAR(50)   NOT NULL,
  declaration_abbreviation CHAR(2)       NOT NULL,
  incident_date            DATE          NOT NULL,
  description              VARCHAR(500)  NOT NULL,
  latitude                 DECIMAL(9,6)  NOT NULL,
  longitude                DECIMAL(9,6)  NOT NULL,
  created_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_incident_owner       FOREIGN KEY (owner_username) REFERENCES app_user(username),
  CONSTRAINT fk_incident_declaration FOREIGN KEY (declaration_abbreviation) REFERENCES declaration(abbreviation),
  CONSTRAINT ck_incident_lat CHECK (latitude  BETWEEN -90  AND 90),
  CONSTRAINT ck_incident_lon CHECK (longitude BETWEEN -180 AND 180),
  INDEX ix_incident_owner (owner_username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE resource (
  resource_id     BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT PRIMARY KEY,
  owner_username  VARCHAR(50)      NOT NULL,
  resource_name   VARCHAR(100)     NOT NULL,
  model           VARCHAR(100)     NULL,
  primary_esf     TINYINT UNSIGNED NOT NULL,
  cost            DECIMAL(12,2)    NOT NULL,
  cost_unit       VARCHAR(20)      NOT NULL,
  latitude        DECIMAL(9,6)     NOT NULL,
  longitude       DECIMAL(9,6)     NOT NULL,
  max_distance_km DECIMAL(8,2)     NULL,
  created_at      TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_resource_owner FOREIGN KEY (owner_username) REFERENCES app_user(username),
  CONSTRAINT fk_resource_esf   FOREIGN KEY (primary_esf)    REFERENCES esf(esf_number),
  CONSTRAINT fk_resource_unit  FOREIGN KEY (cost_unit)      REFERENCES cost_unit(unit),
  CONSTRAINT ck_resource_cost  CHECK (cost >= 0),
  CONSTRAINT ck_resource_lat   CHECK (latitude  BETWEEN -90  AND 90),
  CONSTRAINT ck_resource_lon   CHECK (longitude BETWEEN -180 AND 180),
  CONSTRAINT ck_resource_dist  CHECK (max_distance_km IS NULL OR max_distance_km > 0),
  INDEX ix_resource_owner (owner_username),
  INDEX ix_resource_primary_esf (primary_esf),
  INDEX ix_resource_name (resource_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE resource_capability (
  resource_id BIGINT UNSIGNED NOT NULL,
  capability  VARCHAR(100)    NOT NULL,
  PRIMARY KEY (resource_id, capability),
  CONSTRAINT fk_capability_resource FOREIGN KEY (resource_id) REFERENCES resource(resource_id) ON DELETE CASCADE,
  INDEX ix_capability (capability)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE additional_esf (
  resource_id BIGINT UNSIGNED  NOT NULL,
  esf_number  TINYINT UNSIGNED NOT NULL,
  PRIMARY KEY (resource_id, esf_number),
  CONSTRAINT fk_additional_esf_resource FOREIGN KEY (resource_id) REFERENCES resource(resource_id) ON DELETE CASCADE,
  CONSTRAINT fk_additional_esf_esf      FOREIGN KEY (esf_number)  REFERENCES esf(esf_number),
  INDEX ix_additional_esf_esf (esf_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE resource_request (
  request_id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  resource_id          BIGINT UNSIGNED NOT NULL,
  incident_id          VARCHAR(16)     NOT NULL,
  requested_by         VARCHAR(50)     NOT NULL,
  status               ENUM('PENDING','DEPLOYED','REJECTED','CANCELLED','RETURNED') NOT NULL DEFAULT 'PENDING',
  request_date         DATE            NOT NULL,
  expected_return_date DATE            NOT NULL,
  deploy_date          DATE            NULL,
  actual_return_date   DATE            NULL,
  created_at           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  -- MySQL has no partial unique indexes: emulate them with stored generated columns (NULLs never collide).
  active_pair          VARCHAR(40) GENERATED ALWAYS AS
                         (CASE WHEN status IN ('PENDING','DEPLOYED') THEN CONCAT(resource_id, ':', incident_id) END) STORED,
  deployed_resource_id BIGINT UNSIGNED GENERATED ALWAYS AS
                         (CASE WHEN status = 'DEPLOYED' THEN resource_id END) STORED,
  -- FKs on base columns of stored generated columns must not CASCADE / SET NULL.
  CONSTRAINT fk_request_resource FOREIGN KEY (resource_id)  REFERENCES resource(resource_id),
  CONSTRAINT fk_request_incident FOREIGN KEY (incident_id)  REFERENCES incident(incident_id),
  CONSTRAINT fk_request_user     FOREIGN KEY (requested_by) REFERENCES app_user(username),
  CONSTRAINT ck_request_dates    CHECK (expected_return_date >= request_date),
  CONSTRAINT uq_request_active_pair    UNIQUE (active_pair),
  CONSTRAINT uq_request_one_deployment UNIQUE (deployed_resource_id),
  INDEX ix_request_requester_status (requested_by, status),
  INDEX ix_request_resource_status  (resource_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
