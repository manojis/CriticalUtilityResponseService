package edu.gatech.erms.repository;

import edu.gatech.erms.domain.Incident;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Incidents and the per-declaration id sequence. */
@Repository
public class IncidentRepository {

  private static final String LOCK_SEQUENCE =
      """
      SELECT declaration_abbreviation, next_value
      FROM incident_sequence
      WHERE declaration_abbreviation = :abbreviation
      FOR UPDATE
      """;

  private static final String INCREMENT_SEQUENCE =
      """
      UPDATE incident_sequence
      SET next_value = next_value + 1
      WHERE declaration_abbreviation = :abbreviation
      """;

  private static final String INSERT =
      """
      INSERT INTO incident (incident_id, owner_username, declaration_abbreviation, incident_date,
                            description, latitude, longitude)
      VALUES (:incidentId, :ownerUsername, :declarationAbbreviation, :incidentDate,
              :description, :latitude, :longitude)
      """;

  private static final String SELECT =
      """
      SELECT incident_id, declaration_abbreviation, incident_date, description, latitude, longitude,
             owner_username
      FROM incident
      """;

  private static final String FIND_BY_ID = SELECT + " WHERE incident_id = :incidentId";

  private static final String FIND_BY_OWNER =
      SELECT
          + """
           WHERE owner_username = :ownerUsername
          ORDER BY incident_date DESC, created_at DESC, incident_id DESC
          """;

  private final NamedParameterJdbcTemplate jdbc;

  public IncidentRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Allocates the next incident id for a declaration ({@code ABBR-n}). Locks the declaration's
   * sequence row ({@code SELECT … FOR UPDATE}) so concurrent creates are serialised and never
   * produce duplicates (legacy defect F8). Must run inside the caller's transaction, which then
   * inserts the incident.
   *
   * @return the new id, or empty if the declaration does not exist
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<String> allocateIncidentId(String declarationAbbreviation) {
    Map<String, String> params = Map.of("abbreviation", declarationAbbreviation);
    Optional<String> id =
        jdbc
            .query(
                LOCK_SEQUENCE,
                params,
                (rs, i) ->
                    rs.getString("declaration_abbreviation") + "-" + rs.getLong("next_value"))
            .stream()
            .findFirst();
    id.ifPresent(ignored -> jdbc.update(INCREMENT_SEQUENCE, params));
    return id;
  }

  public void insert(Incident incident) {
    jdbc.update(
        INSERT,
        new MapSqlParameterSource()
            .addValue("incidentId", incident.incidentId())
            .addValue("ownerUsername", incident.ownerUsername())
            .addValue("declarationAbbreviation", incident.declarationAbbreviation())
            .addValue("incidentDate", incident.incidentDate())
            .addValue("description", incident.description())
            .addValue("latitude", incident.latitude())
            .addValue("longitude", incident.longitude()));
  }

  public Optional<Incident> findById(String incidentId) {
    return jdbc
        .query(FIND_BY_ID, Map.of("incidentId", incidentId), IncidentRepository::map)
        .stream()
        .findFirst();
  }

  public List<Incident> findByOwner(String ownerUsername) {
    return jdbc.query(
        FIND_BY_OWNER, Map.of("ownerUsername", ownerUsername), IncidentRepository::map);
  }

  private static Incident map(ResultSet rs, int rowNum) throws SQLException {
    return new Incident(
        rs.getString("incident_id"),
        rs.getString("declaration_abbreviation"),
        JdbcSupport.localDate(rs, "incident_date"),
        rs.getString("description"),
        rs.getBigDecimal("latitude"),
        rs.getBigDecimal("longitude"),
        rs.getString("owner_username"));
  }
}
