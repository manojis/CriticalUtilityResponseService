package edu.gatech.erms.repository;

import edu.gatech.erms.domain.Esf;
import edu.gatech.erms.domain.NewResource;
import edu.gatech.erms.domain.PageResult;
import edu.gatech.erms.domain.ResourceDetail;
import edu.gatech.erms.domain.ResourceSearchCriteria;
import edu.gatech.erms.domain.ResourceSearchRow;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Resources, their capabilities and additional ESFs, plus the single-query search (docs/
 * ARCHITECTURE.md section 3.8). Search SQL is assembled only from the constant fragments below;
 * user input is always bound as a named parameter.
 */
@Repository
public class ResourceRepository {

  private static final String INSERT =
      """
      INSERT INTO resource (owner_username, resource_name, model, primary_esf, cost, cost_unit,
                            latitude, longitude, max_distance_km)
      VALUES (:ownerUsername, :resourceName, :model, :primaryEsf, :cost, :costUnit,
              :latitude, :longitude, :maxDistanceKm)
      """;

  private static final String INSERT_CAPABILITY =
      "INSERT INTO resource_capability (resource_id, capability) VALUES (:resourceId, :capability)";

  private static final String INSERT_ADDITIONAL_ESF =
      "INSERT INTO additional_esf (resource_id, esf_number) VALUES (:resourceId, :esfNumber)";

  private static final String FIND_DETAIL =
      """
      SELECT r.resource_id, r.resource_name, r.model, r.owner_username, u.name AS owner_name,
             r.primary_esf, e.description AS primary_esf_description,
             r.cost, r.cost_unit, r.latitude, r.longitude, r.max_distance_km,
             dep.expected_return_date AS next_available_date
      FROM resource r
      JOIN app_user u ON u.username = r.owner_username
      JOIN esf e      ON e.esf_number = r.primary_esf
      LEFT JOIN resource_request dep ON dep.resource_id = r.resource_id AND dep.status = 'DEPLOYED'
      WHERE r.resource_id = :resourceId
      """;

  private static final String FIND_CAPABILITIES =
      """
      SELECT capability FROM resource_capability
      WHERE resource_id = :resourceId
      ORDER BY capability
      """;

  private static final String FIND_ADDITIONAL_ESFS =
      """
      SELECT e.esf_number, e.description
      FROM additional_esf a
      JOIN esf e ON e.esf_number = a.esf_number
      WHERE a.resource_id = :resourceId
      ORDER BY e.esf_number
      """;

  private static final String FIND_OWNER =
      "SELECT owner_username FROM resource WHERE resource_id = :resourceId";

  // ---- search fragments ----------------------------------------------------------------------

  private static final String DISTANCE_EXPR =
      "(ST_Distance_Sphere(POINT(r.longitude, r.latitude), POINT(:originLon, :originLat)) / 1000)";

  private static final String SEARCH_SELECT =
      """
      SELECT r.resource_id, r.resource_name, r.owner_username, u.name AS owner_name,
             r.cost, r.cost_unit, dep.expected_return_date AS next_available_date
      """;

  private static final String SEARCH_DISTANCE_COLUMN = ", " + DISTANCE_EXPR + " AS distance_km";

  private static final String SEARCH_FROM =
      """
       FROM resource r
      JOIN app_user u ON u.username = r.owner_username
      LEFT JOIN resource_request dep ON dep.resource_id = r.resource_id AND dep.status = 'DEPLOYED'
      WHERE 1 = 1
      """;

  private static final String COUNT_FROM = "SELECT COUNT(*) FROM resource r WHERE 1 = 1";

  /** Legacy defect F1: name OR model OR any capability, without a cross join. */
  private static final String KEYWORD_FILTER =
      """
       AND (r.resource_name LIKE :keyword
            OR r.model LIKE :keyword
            OR EXISTS (SELECT 1 FROM resource_capability c
                       WHERE c.resource_id = r.resource_id AND c.capability LIKE :keyword))
      """;

  /** Legacy defect F2: primary ESF OR any additional ESF. */
  private static final String ESF_FILTER =
      """
       AND (r.primary_esf = :esf
            OR EXISTS (SELECT 1 FROM additional_esf a
                       WHERE a.resource_id = r.resource_id AND a.esf_number = :esf))
      """;

  /** Legacy defect F4: NULL max distance means unlimited; inclusive comparison. */
  private static final String MAX_DISTANCE_FILTER =
      " AND (r.max_distance_km IS NULL OR " + DISTANCE_EXPR + " <= r.max_distance_km)";

  private static final String WITHIN_FILTER = " AND " + DISTANCE_EXPR + " <= :distanceKm";

  private static final String ORDER_BY_DISTANCE =
      " ORDER BY distance_km ASC, r.resource_name ASC, r.resource_id ASC";
  private static final String ORDER_BY_NAME = " ORDER BY r.resource_name ASC, r.resource_id ASC";
  private static final String PAGINATION = " LIMIT :size OFFSET :offset";

  private final NamedParameterJdbcTemplate jdbc;

  public ResourceRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Inserts the resource row and returns the generated id. */
  public long insert(NewResource resource) {
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(
        INSERT,
        new MapSqlParameterSource()
            .addValue("ownerUsername", resource.ownerUsername())
            .addValue("resourceName", resource.resourceName())
            .addValue("model", resource.model())
            .addValue("primaryEsf", resource.primaryEsf())
            .addValue("cost", resource.cost())
            .addValue("costUnit", resource.costUnit())
            .addValue("latitude", resource.latitude())
            .addValue("longitude", resource.longitude())
            .addValue("maxDistanceKm", resource.maxDistanceKm()),
        keys,
        new String[] {"resource_id"});
    return Objects.requireNonNull(keys.getKey(), "generated resource_id").longValue();
  }

  public void insertCapabilities(long resourceId, List<String> capabilities) {
    if (capabilities.isEmpty()) {
      return;
    }
    MapSqlParameterSource[] batch =
        capabilities.stream()
            .map(c ->
                new MapSqlParameterSource()
                    .addValue("resourceId", resourceId)
                    .addValue("capability", c))
            .toArray(MapSqlParameterSource[]::new);
    jdbc.batchUpdate(INSERT_CAPABILITY, batch);
  }

  public void insertAdditionalEsfs(long resourceId, List<Integer> esfNumbers) {
    if (esfNumbers.isEmpty()) {
      return;
    }
    MapSqlParameterSource[] batch =
        esfNumbers.stream()
            .map(n ->
                new MapSqlParameterSource()
                    .addValue("resourceId", resourceId)
                    .addValue("esfNumber", n))
            .toArray(MapSqlParameterSource[]::new);
    jdbc.batchUpdate(INSERT_ADDITIONAL_ESF, batch);
  }

  public Optional<ResourceDetail> findDetail(long resourceId) {
    Map<String, Long> params = Map.of("resourceId", resourceId);
    List<String> capabilities = jdbc.queryForList(FIND_CAPABILITIES, params, String.class);
    List<Esf> additionalEsfs =
        jdbc.query(
            FIND_ADDITIONAL_ESFS,
            params,
            (rs, i) -> new Esf(rs.getInt("esf_number"), rs.getString("description")));
    return jdbc
        .query(
            FIND_DETAIL,
            params,
            (rs, i) ->
                new ResourceDetail(
                    rs.getLong("resource_id"),
                    rs.getString("resource_name"),
                    rs.getString("model"),
                    rs.getString("owner_username"),
                    rs.getString("owner_name"),
                    new Esf(rs.getInt("primary_esf"), rs.getString("primary_esf_description")),
                    additionalEsfs,
                    capabilities,
                    rs.getBigDecimal("cost"),
                    rs.getString("cost_unit"),
                    rs.getBigDecimal("latitude"),
                    rs.getBigDecimal("longitude"),
                    rs.getBigDecimal("max_distance_km"),
                    JdbcSupport.localDate(rs, "next_available_date")))
        .stream()
        .findFirst();
  }

  public Optional<String> findOwnerUsername(long resourceId) {
    return jdbc.queryForList(FIND_OWNER, Map.of("resourceId", resourceId), String.class).stream()
        .findFirst();
  }

  /**
   * Single AND-combined search query plus a matching count (legacy defects F1–F6). With an origin
   * incident, rows carry the great-circle distance and are ordered nearest first.
   */
  public PageResult<ResourceSearchRow> search(ResourceSearchCriteria criteria) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    StringBuilder filters = new StringBuilder();
    if (criteria.keyword() != null) {
      filters.append(KEYWORD_FILTER);
      params.addValue("keyword", JdbcSupport.containsPattern(criteria.keyword()));
    }
    if (criteria.esf() != null) {
      filters.append(ESF_FILTER);
      params.addValue("esf", criteria.esf());
    }
    boolean withDistance = criteria.origin() != null;
    if (withDistance) {
      params.addValue("originLat", criteria.origin().latitude());
      params.addValue("originLon", criteria.origin().longitude());
      filters.append(MAX_DISTANCE_FILTER);
      if (criteria.distanceKm() != null) {
        filters.append(WITHIN_FILTER);
        params.addValue("distanceKm", criteria.distanceKm());
      }
    }

    Long total = jdbc.queryForObject(COUNT_FROM + filters, params, Long.class);
    long totalElements = total == null ? 0 : total;
    if (totalElements == 0 || criteria.offset() >= totalElements) {
      return new PageResult<>(List.of(), criteria.page(), criteria.size(), totalElements);
    }

    String sql =
        SEARCH_SELECT
            + (withDistance ? SEARCH_DISTANCE_COLUMN : "")
            + SEARCH_FROM
            + filters
            + (withDistance ? ORDER_BY_DISTANCE : ORDER_BY_NAME)
            + PAGINATION;
    params.addValue("size", criteria.size()).addValue("offset", criteria.offset());
    List<ResourceSearchRow> rows =
        jdbc.query(
            sql,
            params,
            (rs, i) ->
                new ResourceSearchRow(
                    rs.getLong("resource_id"),
                    rs.getString("resource_name"),
                    rs.getString("owner_username"),
                    rs.getString("owner_name"),
                    rs.getBigDecimal("cost"),
                    rs.getString("cost_unit"),
                    JdbcSupport.localDate(rs, "next_available_date"),
                    withDistance ? roundToTenth(rs.getDouble("distance_km")) : null));
    return new PageResult<>(rows, criteria.page(), criteria.size(), totalElements);
  }

  /** Rounds to 0.1 km; never mis-orders sub-km differences because sorting happens in SQL (F6). */
  static double roundToTenth(double km) {
    return Math.round(km * 10.0) / 10.0;
  }
}
