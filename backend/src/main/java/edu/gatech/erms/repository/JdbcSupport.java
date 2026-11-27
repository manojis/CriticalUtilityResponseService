package edu.gatech.erms.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** Small null-safe {@link ResultSet} helpers shared by the repositories. */
final class JdbcSupport {

  private JdbcSupport() {}

  static @Nullable LocalDate localDate(ResultSet rs, String column) throws SQLException {
    return rs.getObject(column, LocalDate.class);
  }

  static @Nullable Long nullableLong(ResultSet rs, String column) throws SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? null : value;
  }

  static @Nullable Double nullableDouble(ResultSet rs, String column) throws SQLException {
    double value = rs.getDouble(column);
    return rs.wasNull() ? null : value;
  }

  /**
   * Escapes LIKE wildcards so user input is matched literally (legacy defect S9) and wraps it in
   * {@code %…%} for a substring match. MySQL's default LIKE escape character is the backslash.
   */
  static String containsPattern(String input) {
    StringBuilder escaped = new StringBuilder(input.length() + 8).append('%');
    for (char c : input.toCharArray()) {
      if (c == '\\' || c == '%' || c == '_') {
        escaped.append('\\');
      }
      escaped.append(c);
    }
    return escaped.append('%').toString();
  }
}
