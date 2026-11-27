package edu.gatech.erms.support;

import edu.gatech.erms.domain.Esf;
import edu.gatech.erms.domain.Incident;
import edu.gatech.erms.domain.RequestStatus;
import edu.gatech.erms.domain.ResourceDetail;
import edu.gatech.erms.domain.ResourceRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Fixtures for unit and slice tests. */
public final class TestData {

  /** BCrypt (strength 10) of "password"; same hash as the demo seed (verified by a unit test). */
  public static final String PASSWORD_HASH =
      "$2a$10$N0jCfYz6SQM7VbJHv3CpeO4mFBrOhFj823qkahHdDRNHH7XkvEYC2";

  public static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

  private TestData() {}

  public static Incident incident(String id, String owner) {
    return new Incident(
        id,
        id.substring(0, 2),
        TODAY.minusDays(1),
        "Flooding on I-75",
        new BigDecimal("33.749000"),
        new BigDecimal("-84.388000"),
        owner);
  }

  public static ResourceDetail resource(long id, String owner) {
    return new ResourceDetail(
        id,
        "Hydraulic Crane",
        "XL-200",
        owner,
        "Owner Name",
        new Esf(3, "Public Works and Engineering"),
        List.of(new Esf(1, "Transportation")),
        List.of("Lift 20t"),
        new BigDecimal("150.00"),
        "Hour",
        new BigDecimal("33.770000"),
        new BigDecimal("-84.390000"),
        new BigDecimal("120.00"),
        null);
  }

  public static ResourceRequest request(
      long id, RequestStatus status, String requester, String owner) {
    return new ResourceRequest(
        id,
        1001L,
        "Hydraulic Crane",
        owner,
        "MD-1",
        "Flooding on I-75",
        requester,
        status,
        TODAY,
        TODAY.plusDays(10),
        status == RequestStatus.DEPLOYED || status == RequestStatus.RETURNED ? TODAY : null,
        status == RequestStatus.RETURNED ? TODAY : null,
        status != RequestStatus.DEPLOYED);
  }
}
