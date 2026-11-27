package edu.gatech.erms.service;

import edu.gatech.erms.domain.Incident;
import edu.gatech.erms.exception.FieldValidationException;
import edu.gatech.erms.exception.NotFoundException;
import edu.gatech.erms.repository.IncidentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Declares and reads incidents. Incidents are visible to their owner only. */
@Service
public class IncidentService {

  static final int COORDINATE_SCALE = 6;

  private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

  private final IncidentRepository incidents;
  private final Clock clock;

  public IncidentService(IncidentRepository incidents, Clock clock) {
    this.incidents = incidents;
    this.clock = clock;
  }

  /**
   * Creates an incident with a server-generated id {@code <ABBR>-<n>} (legacy defect F8). The
   * sequence row lock and the insert share one transaction.
   *
   * @throws FieldValidationException if the date is in the future or the declaration is unknown
   */
  @Transactional
  public Incident create(String ownerUsername, CreateIncidentCommand command) {
    LocalDate today = LocalDate.now(clock);
    if (command.incidentDate().isAfter(today)) {
      throw FieldValidationException.of("incidentDate", "must not be after today (" + today + ")");
    }
    String incidentId =
        incidents
            .allocateIncidentId(command.declarationAbbreviation().trim())
            .orElseThrow(() ->
                FieldValidationException.of("declarationAbbreviation", "unknown declaration"));
    Incident incident =
        new Incident(
            incidentId,
            incidentId.substring(0, incidentId.indexOf('-')),
            command.incidentDate(),
            command.description(),
            scaleCoordinate(command.latitude()),
            scaleCoordinate(command.longitude()),
            ownerUsername);
    incidents.insert(incident);
    log.info("Incident {} created by {}", incidentId, ownerUsername);
    return incident;
  }

  /** The caller's incidents, newest first. */
  @Transactional(readOnly = true)
  public List<Incident> listOwned(String ownerUsername) {
    return incidents.findByOwner(ownerUsername);
  }

  /**
   * Returns an incident owned by the caller.
   *
   * @throws NotFoundException if it does not exist or belongs to someone else
   */
  @Transactional(readOnly = true)
  public Incident getOwned(String callerUsername, String incidentId) {
    return incidents
        .findById(incidentId)
        .filter(incident -> incident.ownerUsername().equals(callerUsername))
        .orElseThrow(() -> NotFoundException.of("Incident", incidentId));
  }

  static BigDecimal scaleCoordinate(BigDecimal value) {
    return value.setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
  }
}
