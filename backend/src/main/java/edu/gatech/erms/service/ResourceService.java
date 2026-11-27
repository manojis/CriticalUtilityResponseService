package edu.gatech.erms.service;

import edu.gatech.erms.domain.Incident;
import edu.gatech.erms.domain.NewResource;
import edu.gatech.erms.domain.PageResult;
import edu.gatech.erms.domain.ResourceDetail;
import edu.gatech.erms.domain.ResourceSearchCriteria;
import edu.gatech.erms.domain.ResourceSearchRow;
import edu.gatech.erms.exception.FieldValidationException;
import edu.gatech.erms.exception.FieldValidationException.FieldError;
import edu.gatech.erms.exception.NotFoundException;
import edu.gatech.erms.repository.ReferenceDataRepository;
import edu.gatech.erms.repository.ResourceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registers, reads and searches resources. */
@Service
public class ResourceService {

  static final int MAX_CAPABILITY_LENGTH = 100;

  private static final Logger log = LoggerFactory.getLogger(ResourceService.class);

  private final ResourceRepository resources;
  private final ReferenceDataRepository referenceData;
  private final IncidentService incidentService;

  public ResourceService(
      ResourceRepository resources,
      ReferenceDataRepository referenceData,
      IncidentService incidentService) {
    this.resources = resources;
    this.referenceData = referenceData;
    this.incidentService = incidentService;
  }

  /**
   * Registers a resource with its capabilities and additional ESFs atomically (legacy defect R6).
   *
   * @throws FieldValidationException if an ESF or the cost unit is unknown, additional ESFs are
   *     duplicated or contain the primary ESF, or a capability is blank or too long
   */
  @Transactional
  public ResourceDetail create(String ownerUsername, CreateResourceCommand command) {
    List<FieldError> errors = new ArrayList<>();
    int primaryEsf = command.primaryEsf();
    List<Integer> additionalEsfs = validateAdditionalEsfs(command, errors);
    validateEsfsExist(primaryEsf, additionalEsfs, errors);
    Optional<String> costUnit = referenceData.findCostUnit(command.costUnit().trim());
    if (costUnit.isEmpty()) {
      errors.add(new FieldError("costUnit", "unknown cost unit"));
    }
    List<String> capabilities = normaliseCapabilities(command.capabilities(), errors);
    if (!errors.isEmpty()) {
      throw new FieldValidationException(errors);
    }

    NewResource resource =
        new NewResource(
            ownerUsername,
            command.resourceName().trim(),
            blankToNull(command.model()),
            primaryEsf,
            additionalEsfs,
            capabilities,
            command.cost().setScale(2, RoundingMode.HALF_UP),
            costUnit.orElseThrow(),
            IncidentService.scaleCoordinate(command.latitude()),
            IncidentService.scaleCoordinate(command.longitude()),
            command.maxDistanceKm() == null
                ? null
                : command.maxDistanceKm().setScale(2, RoundingMode.HALF_UP));
    long resourceId = resources.insert(resource);
    resources.insertCapabilities(resourceId, capabilities);
    resources.insertAdditionalEsfs(resourceId, additionalEsfs);
    log.info("Resource {} created by {}", resourceId, LogSafe.of(ownerUsername));
    return resources
        .findDetail(resourceId)
        .orElseThrow(() -> new IllegalStateException("Resource vanished after insert"));
  }

  /**
   * Returns any resource (resources are visible to every authenticated user).
   *
   * @throws NotFoundException if it does not exist
   */
  @Transactional(readOnly = true)
  public ResourceDetail get(long resourceId) {
    return resources
        .findDetail(resourceId)
        .orElseThrow(() -> NotFoundException.of("Resource", resourceId));
  }

  /**
   * Searches all users' resources. All filters are optional and AND-combined.
   *
   * @throws FieldValidationException if {@code distanceKm} is given without {@code incidentId}
   * @throws NotFoundException if {@code incidentId} is not one of the caller's incidents
   */
  @Transactional(readOnly = true)
  public PageResult<ResourceSearchRow> search(
      String callerUsername,
      @Nullable String keyword,
      @Nullable Integer esf,
      @Nullable String incidentId,
      @Nullable BigDecimal distanceKm,
      int page,
      int size) {
    String normalisedIncidentId = blankToNull(incidentId);
    if (distanceKm != null && normalisedIncidentId == null) {
      throw FieldValidationException.of("distanceKm", "requires incidentId");
    }
    Incident origin =
        normalisedIncidentId == null
            ? null
            : incidentService.getOwned(callerUsername, normalisedIncidentId);
    return resources.search(
        new ResourceSearchCriteria(blankToNull(keyword), esf, origin, distanceKm, page, size));
  }

  private static List<Integer> validateAdditionalEsfs(
      CreateResourceCommand command, List<FieldError> errors) {
    List<Integer> requested =
        command.additionalEsfs() == null ? List.of() : command.additionalEsfs();
    Set<Integer> unique = new LinkedHashSet<>(requested);
    if (unique.size() != requested.size()) {
      errors.add(new FieldError("additionalEsfs", "must not contain duplicates"));
    }
    if (unique.contains(command.primaryEsf())) {
      errors.add(new FieldError("additionalEsfs", "must not contain the primary ESF"));
    }
    return List.copyOf(unique);
  }

  private void validateEsfsExist(
      int primaryEsf, List<Integer> additionalEsfs, List<FieldError> errors) {
    Set<Integer> requested = new HashSet<>(additionalEsfs);
    requested.add(primaryEsf);
    Set<Integer> existing = referenceData.findExistingEsfNumbers(requested);
    if (!existing.contains(primaryEsf)) {
      errors.add(new FieldError("primaryEsf", "unknown ESF " + primaryEsf));
    }
    List<Integer> unknown = additionalEsfs.stream().filter(n -> !existing.contains(n)).toList();
    if (!unknown.isEmpty()) {
      errors.add(new FieldError("additionalEsfs", "unknown ESF(s) " + unknown));
    }
  }

  /**
   * Trims capabilities and removes case- and accent-insensitive duplicates (matching the {@code
   * utf8mb4_0900_ai_ci} collation of the unique key), keeping the first spelling.
   */
  static List<String> normaliseCapabilities(
      @Nullable List<String> capabilities, List<FieldError> errors) {
    if (capabilities == null) {
      return List.of();
    }
    Collator collator = Collator.getInstance(Locale.ROOT);
    collator.setStrength(Collator.PRIMARY);
    Set<String> seen = new TreeSet<>(collator);
    List<String> result = new ArrayList<>();
    for (int i = 0; i < capabilities.size(); i++) {
      String raw = capabilities.get(i);
      String value = raw == null ? "" : raw.trim();
      if (value.isEmpty()) {
        errors.add(new FieldError("capabilities[" + i + "]", "must not be blank"));
      } else if (value.length() > MAX_CAPABILITY_LENGTH) {
        errors.add(
            new FieldError(
                "capabilities[" + i + "]", "size must be between 1 and " + MAX_CAPABILITY_LENGTH));
      } else if (seen.add(value)) {
        result.add(value);
      }
    }
    return result;
  }

  private static @Nullable String blankToNull(@Nullable String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
