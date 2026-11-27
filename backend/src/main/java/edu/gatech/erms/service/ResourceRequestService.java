package edu.gatech.erms.service;

import edu.gatech.erms.domain.Incident;
import edu.gatech.erms.domain.NewResourceRequest;
import edu.gatech.erms.domain.RequestRole;
import edu.gatech.erms.domain.RequestStatus;
import edu.gatech.erms.domain.ResourceRequest;
import edu.gatech.erms.exception.ConflictException;
import edu.gatech.erms.exception.FieldValidationException;
import edu.gatech.erms.exception.ForbiddenException;
import edu.gatech.erms.exception.NotFoundException;
import edu.gatech.erms.repository.ResourceRepository;
import edu.gatech.erms.repository.ResourceRequestRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The request → deploy → return workflow (docs/ARCHITECTURE.md section 3.7). Every transition is
 * one transaction, guarded by an optimistic {@code UPDATE … WHERE status = :expected} and by the
 * database unique keys {@code uq_request_active_pair} / {@code uq_request_one_deployment}.
 *
 * <p>Visibility: a request is visible only to its requester and to the resource owner; anyone else
 * gets 404. Dates are always "today" from the injected {@link Clock} (legacy defect R3).
 */
@Service
public class ResourceRequestService {

  private static final Logger log = LoggerFactory.getLogger(ResourceRequestService.class);

  private final ResourceRequestRepository requests;
  private final ResourceRepository resources;
  private final IncidentService incidentService;
  private final Clock clock;

  public ResourceRequestService(
      ResourceRequestRepository requests,
      ResourceRepository resources,
      IncidentService incidentService,
      Clock clock) {
    this.requests = requests;
    this.resources = resources;
    this.incidentService = incidentService;
    this.clock = clock;
  }

  /**
   * Requests a resource for one of the caller's incidents, or deploys the caller's own resource
   * immediately.
   *
   * @throws NotFoundException if the incident is not the caller's or the resource does not exist
   * @throws FieldValidationException if the expected return date is before today
   * @throws ForbiddenException if {@code deployImmediately} is set for someone else's resource
   * @throws ConflictException if an active request already exists for this resource and incident,
   *     or the resource is already deployed (for {@code deployImmediately})
   */
  @Transactional
  public ResourceRequest create(String callerUsername, CreateResourceRequestCommand command) {
    Incident incident = incidentService.getOwned(callerUsername, command.incidentId().trim());
    String ownerUsername =
        resources
            .findOwnerUsername(command.resourceId())
            .orElseThrow(() -> NotFoundException.of("Resource", command.resourceId()));
    LocalDate today = LocalDate.now(clock);
    if (command.expectedReturnDate().isBefore(today)) {
      throw FieldValidationException.of(
          "expectedReturnDate", "must not be before today (" + today + ")");
    }
    if (command.deployImmediately() && !ownerUsername.equals(callerUsername)) {
      throw new ForbiddenException("Only the resource owner can deploy immediately");
    }
    // Legacy defect F14: only an *active* (PENDING/DEPLOYED) pair blocks a new request.
    if (requests.existsActive(command.resourceId(), incident.incidentId())) {
      throw new ConflictException(
          "An active request already exists for resource "
              + command.resourceId()
              + " and incident "
              + incident.incidentId());
    }
    if (command.deployImmediately() && requests.isResourceDeployed(command.resourceId())) {
      throw resourceDeployed(command.resourceId());
    }

    RequestStatus status =
        command.deployImmediately() ? RequestStatus.DEPLOYED : RequestStatus.PENDING;
    NewResourceRequest newRequest =
        new NewResourceRequest(
            command.resourceId(),
            incident.incidentId(),
            callerUsername,
            status,
            today,
            command.expectedReturnDate(),
            command.deployImmediately() ? today : null);
    long requestId;
    try {
      requestId = requests.insert(newRequest);
    } catch (DuplicateKeyException ex) {
      throw new ConflictException(
          "Resource "
              + command.resourceId()
              + " was requested or deployed concurrently; refresh and retry");
    }
    log.info(
        "Request {} created by {} for resource {} / incident {} as {}",
        requestId,
        callerUsername,
        command.resourceId(),
        incident.incidentId(),
        status);
    return load(requestId);
  }

  /** Lists requests from the caller's perspective, optionally filtered by status. */
  @Transactional(readOnly = true)
  public List<ResourceRequest> list(
      String callerUsername, RequestRole role, Collection<RequestStatus> statuses) {
    return switch (role) {
      case REQUESTER -> requests.findByRequester(callerUsername, statuses);
      case OWNER -> requests.findByResourceOwner(callerUsername, statuses);
    };
  }

  /** Returns one request visible to the caller (requester or resource owner). */
  @Transactional(readOnly = true)
  public ResourceRequest get(String callerUsername, long requestId) {
    return findVisible(callerUsername, requestId);
  }

  /**
   * Owner deploys a PENDING request (legacy defect F13: server-side state, resource must be free).
   */
  @Transactional
  public ResourceRequest deploy(String callerUsername, long requestId) {
    ResourceRequest request = findVisible(callerUsername, requestId);
    if (!request.isOwner(callerUsername)) {
      throw new ForbiddenException("Only the resource owner can deploy request " + requestId);
    }
    requireStatus(request, RequestStatus.PENDING, "deploy");
    if (requests.isResourceDeployed(request.resourceId())) {
      throw resourceDeployed(request.resourceId());
    }
    LocalDate today = LocalDate.now(clock);
    try {
      transition(request, RequestStatus.PENDING, RequestStatus.DEPLOYED, today, null, "deploy");
    } catch (DuplicateKeyException ex) {
      throw resourceDeployed(request.resourceId());
    }
    return load(requestId);
  }

  /** Owner rejects a PENDING request. Status only (legacy defect F15). */
  @Transactional
  public ResourceRequest reject(String callerUsername, long requestId) {
    ResourceRequest request = findVisible(callerUsername, requestId);
    if (!request.isOwner(callerUsername)) {
      throw new ForbiddenException("Only the resource owner can reject request " + requestId);
    }
    requireStatus(request, RequestStatus.PENDING, "reject");
    transition(request, RequestStatus.PENDING, RequestStatus.REJECTED, null, null, "reject");
    return load(requestId);
  }

  /** Requester cancels a PENDING request. The row is kept (legacy defect F16). */
  @Transactional
  public ResourceRequest cancel(String callerUsername, long requestId) {
    ResourceRequest request = findVisible(callerUsername, requestId);
    if (!request.isRequester(callerUsername)) {
      throw new ForbiddenException("Only the requester can cancel request " + requestId);
    }
    requireStatus(request, RequestStatus.PENDING, "cancel");
    transition(request, RequestStatus.PENDING, RequestStatus.CANCELLED, null, null, "cancel");
    return load(requestId);
  }

  /**
   * Requester or owner returns a DEPLOYED resource. Only this request changes (legacy defect F12).
   */
  @Transactional
  public ResourceRequest returnResource(String callerUsername, long requestId) {
    ResourceRequest request = findVisible(callerUsername, requestId);
    requireStatus(request, RequestStatus.DEPLOYED, "return");
    LocalDate today = LocalDate.now(clock);
    transition(request, RequestStatus.DEPLOYED, RequestStatus.RETURNED, null, today, "return");
    return load(requestId);
  }

  private ResourceRequest findVisible(String callerUsername, long requestId) {
    return requests
        .findById(requestId)
        .filter(r -> r.isRequester(callerUsername) || r.isOwner(callerUsername))
        .orElseThrow(() -> NotFoundException.of("Request", requestId));
  }

  private ResourceRequest load(long requestId) {
    return requests
        .findById(requestId)
        .orElseThrow(() -> NotFoundException.of("Request", requestId));
  }

  private static void requireStatus(
      ResourceRequest request, RequestStatus expected, String action) {
    if (request.status() != expected) {
      throw illegalTransition(request.requestId(), request.status(), action);
    }
  }

  private void transition(
      ResourceRequest request,
      RequestStatus expected,
      RequestStatus target,
      @Nullable LocalDate deployDate,
      @Nullable LocalDate actualReturnDate,
      String action) {
    int updated =
        requests.updateStatus(request.requestId(), expected, target, deployDate, actualReturnDate);
    if (updated == 0) {
      // Lost a race: someone changed the request between our read and the update.
      RequestStatus current =
          requests.findById(request.requestId()).map(ResourceRequest::status).orElse(expected);
      throw illegalTransition(request.requestId(), current, action);
    }
    log.info("Request {} {} -> {}", request.requestId(), expected, target);
  }

  private static ConflictException illegalTransition(
      long requestId, RequestStatus current, String action) {
    return new ConflictException("Request " + requestId + " is " + current + "; cannot " + action);
  }

  private static ConflictException resourceDeployed(long resourceId) {
    return new ConflictException("Resource " + resourceId + " is currently deployed");
  }
}
