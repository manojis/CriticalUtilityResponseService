package edu.gatech.erms.repository;

import edu.gatech.erms.domain.EsfReportRow;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Aggregates for the resources-by-ESF report (legacy defects F10/F11). */
@Repository
public class ReportRepository {

  private static final String RESOURCES_BY_ESF =
      """
      SELECT e.esf_number, e.description,
             COUNT(r.resource_id)  AS total_resources,
             COUNT(rr.request_id)  AS resources_in_use
      FROM esf e
      LEFT JOIN resource r          ON r.primary_esf = e.esf_number AND r.owner_username = :username
      LEFT JOIN resource_request rr ON rr.resource_id = r.resource_id AND rr.status = 'DEPLOYED'
      GROUP BY e.esf_number, e.description
      ORDER BY e.esf_number
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public ReportRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** One row per ESF (all 15, zeros included) for resources owned by {@code username}. */
  public List<EsfReportRow> resourcesByEsf(String username) {
    return jdbc.query(
        RESOURCES_BY_ESF,
        Map.of("username", username),
        (rs, i) ->
            new EsfReportRow(
                rs.getInt("esf_number"),
                rs.getString("description"),
                rs.getLong("total_resources"),
                rs.getLong("resources_in_use")));
  }
}
