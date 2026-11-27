package edu.gatech.erms.repository;

import edu.gatech.erms.domain.NewResourceRequest;
import edu.gatech.erms.domain.RequestStatus;
import edu.gatech.erms.domain.ResourceRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Resource requests. Rows are never deleted (audit trail). State changes use optimistic {@code
 * UPDATE … WHERE status = :expected} and report the affected row count.
 */
@Repository
public class ResourceRequestRepository {

  private static final String INSERT =
      """
      INSERT INTO resource_request (resource_id, incident_id, requested_by, status, request_date,
                                    expected_return_date, deploy_date)
      VALUES (:resourceId, :incidentId, :requestedBy, :status, :requestDate,
              :expectedReturnDate, :deployDate)
      """;

  private static final String SELECT =
      """
      SELECT rr.request_id, rr.resource_id, r.resource_name, r.owner_username,
             rr.incident_id, i.description AS incident_description, rr.requested_by, rr.status,
             rr.request_date, rr.expected_return_date, rr.deploy_date, rr.actual_return_date,
             NOT EXISTS (SELECT 1 FROM resource_request d
                         WHERE d.resource_id = rr.resource_id AND d.status = 'DEPLOYED')
               AS resource_available
      FROM resource_request rr
      JOIN resource r ON r.resource_id = rr.resource_id
      JOIN incident i ON i.incident_id = rr.incident_id
      """;

  private static final String FIND_BY_ID = SELECT + " WHERE rr.request_id = :requestId";
  private static final String BY_REQUESTER = SELECT + " WHERE rr.requested_by = :username";
  private static final String BY_OWNER = SELECT + " WHERE r.owner_username = :username";
  private static final String STATUS_FILTER = " AND rr.status IN (:statuses)";
  private static final String LIST_ORDER = " ORDER BY rr.request_date DESC, rr.request_id DESC";

  private static final String UPDATE_STATUS =
      """
      UPDATE resource_request
      SET status = :target,
          deploy_date = COALESCE(:deployDate, deploy_date),
          actual_return_date = COALESCE(:actualReturnDate, actual_return_date)
      WHERE request_id = :requestId AND status = :expected
      """;

  private static final String COUNT_ACTIVE_PAIR =
      """
      SELECT COUNT(*) FROM resource_request
      WHERE resource_id = :resourceId AND incident_id = :incidentId
        AND status IN ('PENDING', 'DEPLOYED')
      """;

  private static final String COUNT_DEPLOYED =
      """
      SELECT COUNT(*) FROM resource_request
      WHERE resource_id = :resourceId AND status = 'DEPLOYED'
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public ResourceRequestRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public long insert(NewResourceRequest request) {
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(
        INSERT,
        new MapSqlParameterSource()
            .addValue("resourceId", request.resourceId())
            .addValue("incidentId", request.incidentId())
            .addValue("requestedBy", request.requestedBy())
            .addValue("status", request.status().name())
            .addValue("requestDate", request.requestDate())
            .addValue("expectedReturnDate", request.expectedReturnDate())
            .addValue("deployDate", request.deployDate()),
        keys,
        new String[] {"request_id"});
    return Objects.requireNonNull(keys.getKey(), "generated request_id").longValue();
  }

  public Optional<ResourceRequest> findById(long requestId) {
    return jdbc
        .query(FIND_BY_ID, Map.of("requestId", requestId), ResourceRequestRepository::map)
        .stream()
        .findFirst();
  }

  /** Requests made by {@code username}, optionally limited to some statuses. */
  public List<ResourceRequest> findByRequester(
      String username, Collection<RequestStatus> statuses) {
    return list(BY_REQUESTER, username, statuses);
  }

  /** Requests for resources owned by {@code username}, optionally limited to some statuses. */
  public List<ResourceRequest> findByResourceOwner(
      String username, Collection<RequestStatus> statuses) {
    return list(BY_OWNER, username, statuses);
  }

  /**
   * Moves one request from {@code expected} to {@code target}, optionally stamping the deploy or
   * actual-return date. Only the target row is touched (legacy defect F12).
   *
   * @return the number of rows updated (0 when the request was not in {@code expected})
   */
  public int updateStatus(
      long requestId,
      RequestStatus expected,
      RequestStatus target,
      @Nullable LocalDate deployDate,
      @Nullable LocalDate actualReturnDate) {
    return jdbc.update(
        UPDATE_STATUS,
        new MapSqlParameterSource()
            .addValue("requestId", requestId)
            .addValue("expected", expected.name())
            .addValue("target", target.name())
            .addValue("deployDate", deployDate)
            .addValue("actualReturnDate", actualReturnDate));
  }

  /** Whether a PENDING or DEPLOYED request exists for the resource + incident pair. */
  public boolean existsActive(long resourceId, String incidentId) {
    Long count =
        jdbc.queryForObject(
            COUNT_ACTIVE_PAIR,
            Map.of("resourceId", resourceId, "incidentId", incidentId),
            Long.class);
    return count != null && count > 0;
  }

  /** Whether the resource currently has a DEPLOYED request. */
  public boolean isResourceDeployed(long resourceId) {
    Long count = jdbc.queryForObject(COUNT_DEPLOYED, Map.of("resourceId", resourceId), Long.class);
    return count != null && count > 0;
  }

  private List<ResourceRequest> list(
      String baseSql, String username, Collection<RequestStatus> statuses) {
    MapSqlParameterSource params = new MapSqlParameterSource("username", username);
    String sql = baseSql;
    if (!statuses.isEmpty()) {
      sql += STATUS_FILTER;
      params.addValue("statuses", statuses.stream().map(Enum::name).toList());
    }
    return jdbc.query(sql + LIST_ORDER, params, ResourceRequestRepository::map);
  }

  private static ResourceRequest map(ResultSet rs, int rowNum) throws SQLException {
    return new ResourceRequest(
        rs.getLong("request_id"),
        rs.getLong("resource_id"),
        rs.getString("resource_name"),
        rs.getString("owner_username"),
        rs.getString("incident_id"),
        rs.getString("incident_description"),
        rs.getString("requested_by"),
        RequestStatus.valueOf(rs.getString("status")),
        JdbcSupport.localDate(rs, "request_date"),
        JdbcSupport.localDate(rs, "expected_return_date"),
        JdbcSupport.localDate(rs, "deploy_date"),
        JdbcSupport.localDate(rs, "actual_return_date"),
        rs.getBoolean("resource_available"));
  }
}
