package edu.gatech.erms.repository;

import edu.gatech.erms.domain.Declaration;
import edu.gatech.erms.domain.Esf;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read-only access to Flyway-managed reference data (ESFs, cost units, declarations). */
@Repository
public class ReferenceDataRepository {

  private static final String ALL_ESFS =
      "SELECT esf_number, description FROM esf ORDER BY esf_number";
  private static final String ALL_COST_UNITS = "SELECT unit FROM cost_unit ORDER BY unit";
  private static final String ALL_DECLARATIONS =
      "SELECT abbreviation, name FROM declaration ORDER BY name";
  private static final String EXISTING_ESF_NUMBERS =
      "SELECT esf_number FROM esf WHERE esf_number IN (:numbers)";
  private static final String FIND_COST_UNIT = "SELECT unit FROM cost_unit WHERE unit = :unit";

  private final NamedParameterJdbcTemplate jdbc;

  public ReferenceDataRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Esf> findAllEsfs() {
    return jdbc.query(
        ALL_ESFS, (rs, i) -> new Esf(rs.getInt("esf_number"), rs.getString("description")));
  }

  public List<String> findAllCostUnits() {
    return jdbc.queryForList(ALL_COST_UNITS, Map.of(), String.class);
  }

  public List<Declaration> findAllDeclarations() {
    return jdbc.query(
        ALL_DECLARATIONS,
        (rs, i) -> new Declaration(rs.getString("abbreviation"), rs.getString("name")));
  }

  /** Returns the subset of {@code numbers} that are valid ESF numbers. */
  public Set<Integer> findExistingEsfNumbers(Collection<Integer> numbers) {
    if (numbers.isEmpty()) {
      return Set.of();
    }
    return new HashSet<>(
        jdbc.queryForList(EXISTING_ESF_NUMBERS, Map.of("numbers", numbers), Integer.class));
  }

  /**
   * Looks up a cost unit case-insensitively and returns its canonical spelling (e.g. {@code "hour"}
   * → {@code "Hour"}).
   */
  public Optional<String> findCostUnit(String unit) {
    return jdbc.queryForList(FIND_COST_UNIT, Map.of("unit", unit), String.class).stream()
        .findFirst();
  }
}
