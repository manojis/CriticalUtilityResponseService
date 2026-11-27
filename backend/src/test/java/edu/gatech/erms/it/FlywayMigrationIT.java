package edu.gatech.erms.it;

import static org.assertj.core.api.Assertions.assertThat;

import edu.gatech.erms.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class FlywayMigrationIT extends AbstractIntegrationTest {

  @Test
  void schemaAndReferenceDataAreMigrated() {
    List<String> tables =
        jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
            Map.of(),
            String.class);
    assertThat(tables)
        .contains(
            "app_user",
            "individual",
            "municipality",
            "government_agency",
            "company",
            "esf",
            "cost_unit",
            "declaration",
            "incident_sequence",
            "incident",
            "resource",
            "resource_capability",
            "additional_esf",
            "resource_request",
            "flyway_schema_history");

    assertThat(count("esf")).isEqualTo(15);
    assertThat(count("cost_unit")).isEqualTo(4);
    assertThat(count("declaration")).isEqualTo(4);
    assertThat(count("incident_sequence")).isEqualTo(4);
    assertThat(
            jdbc.queryForObject(
                "SELECT description FROM esf WHERE esf_number = 6", Map.of(), String.class))
        .isEqualTo("Mass Care, Emergency Assistance, Temporary Housing, and Human Services");
  }

  @Test
  void demoSeedIsNotAppliedWithoutDemoProfile() {
    Integer seedRuns =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE script LIKE '%demo_seed%'",
            Map.of(), Integer.class);
    assertThat(seedRuns).isZero();
  }

  @Test
  void checkConstraintsGuardCoordinates_F22() {
    createUser("alice");
    Assertions.assertThatThrownBy(() ->
            jdbc.update(
                """
                INSERT INTO incident (incident_id, owner_username, declaration_abbreviation,
                  incident_date, description, latitude, longitude)
                VALUES ('MD-99', 'alice', 'MD', '2026-01-01', 'Out of range', 91, 0)
                """,
                Map.of()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private int count(String table) {
    Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Map.of(), Integer.class);
    return n == null ? 0 : n;
  }
}
