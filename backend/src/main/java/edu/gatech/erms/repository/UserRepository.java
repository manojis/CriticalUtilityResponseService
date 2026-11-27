package edu.gatech.erms.repository;

import edu.gatech.erms.domain.UserAccount;
import edu.gatech.erms.domain.UserProfile;
import edu.gatech.erms.domain.UserType;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads user accounts and their subtype profiles. Users are provisioned by Flyway. */
@Repository
public class UserRepository {

  private static final String FIND_ACCOUNT =
      """
      SELECT username, name, password_hash, user_type
      FROM app_user
      WHERE username = :username
      """;

  private static final String FIND_PROFILE =
      """
      SELECT u.username, u.name, u.user_type,
             i.job_title, i.hire_date,
             m.municipality_category,
             g.agency_name_local_office,
             c.headquarters, c.num_employees
      FROM app_user u
      LEFT JOIN individual        i ON i.username = u.username
      LEFT JOIN municipality      m ON m.username = u.username
      LEFT JOIN government_agency g ON g.username = u.username
      LEFT JOIN company           c ON c.username = u.username
      WHERE u.username = :username
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public UserRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<UserAccount> findAccount(String username) {
    return jdbc
        .query(
            FIND_ACCOUNT,
            Map.of("username", username),
            (rs, i) ->
                new UserAccount(
                    rs.getString("username"),
                    rs.getString("name"),
                    rs.getString("password_hash"),
                    UserType.valueOf(rs.getString("user_type"))))
        .stream()
        .findFirst();
  }

  public Optional<UserProfile> findProfile(String username) {
    return jdbc
        .query(
            FIND_PROFILE,
            Map.of("username", username),
            (rs, i) ->
                new UserProfile(
                    rs.getString("username"),
                    rs.getString("name"),
                    UserType.valueOf(rs.getString("user_type")),
                    rs.getString("job_title"),
                    JdbcSupport.localDate(rs, "hire_date"),
                    rs.getString("municipality_category"),
                    rs.getString("agency_name_local_office"),
                    rs.getString("headquarters"),
                    JdbcSupport.nullableLong(rs, "num_employees")))
        .stream()
        .findFirst();
  }
}
