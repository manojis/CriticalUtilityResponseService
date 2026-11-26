# ADR-0002: Java 21 + Spring Boot 4.1 with NamedParameterJdbcTemplate (no JPA)
Status: Accepted · 2026-11-25

## Context
The legacy code is Boot 2.0.2 / Java 8 (EOL, with CVEs) and uses hand-rolled JDBC that leaks connections. Its business logic is SQL-heavy: geo distance, report aggregation, EXISTS filters.

## Decision
Use Java 21 LTS and Spring Boot 4.1.3, HikariCP and `NamedParameterJdbcTemplate`, with SQL kept in repository classes as text blocks. Flyway owns the schema.

## Alternatives
- Spring Data JPA: adds an impedance mismatch for the geo and report queries and hides the SQL that most needs review.
- Spring Data JDBC: viable, but the aggregates are simple and the queries are custom anyway.

## Consequences
SQL is explicit and testable with Testcontainers. Row mapping must be written by hand (records + RowMapper).
