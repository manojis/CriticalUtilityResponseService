package edu.gatech.erms.service;

import static edu.gatech.erms.domain.RequestStatus.CANCELLED;
import static edu.gatech.erms.domain.RequestStatus.DEPLOYED;
import static edu.gatech.erms.domain.RequestStatus.PENDING;
import static edu.gatech.erms.domain.RequestStatus.REJECTED;
import static edu.gatech.erms.domain.RequestStatus.RETURNED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import edu.gatech.erms.support.MutableClock;
import edu.gatech.erms.support.TestData;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

class ResourceRequestServiceTest {

  private static final String REQUESTER = "alice";
  private static final String OWNER = "acme";
  private static final String STRANGER = "mallory";

  private final ResourceRequestRepository requests = mock(ResourceRequestRepository.class);
  private final ResourceRepository resources = mock(ResourceRepository.class);
  private final IncidentService incidentService = mock(IncidentService.class);
  private final MutableClock clock = MutableClock.utc("2026-11-25T12:00:00Z");
  private final ResourceRequestService service =
      new ResourceRequestService(requests, resources, incidentService, clock);

  @BeforeEach
  void setUp() {
    when(incidentService.getOwned(REQUESTER, "MD-1"))
        .thenReturn(TestData.incident("MD-1", REQUESTER));
    when(incidentService.getOwned(STRANGER, "MD-1"))
        .thenThrow(NotFoundException.of("Incident", "MD-1"));
    when(resources.findOwnerUsername(1001L)).thenReturn(Optional.of(OWNER));
    when(requests.insert(any())).thenReturn(7L);
    when(requests.updateStatus(anyLong(), any(), any(), any(), any())).thenReturn(1);
  }

  private void stored(long id, RequestStatus status) {
    when(requests.findById(id))
        .thenReturn(Optional.of(TestData.request(id, status, REQUESTER, OWNER)));
  }

  private static CreateResourceRequestCommand create(LocalDate expectedReturn, boolean deployNow) {
    return new CreateResourceRequestCommand(1001L, "MD-1", expectedReturn, deployNow);
  }

  // ---- create ----------------------------------------------------------------------------------

  @Test
  void createIsPendingWithTodayAsRequestDate() {
    stored(7L, PENDING);

    service.create(REQUESTER, create(TestData.TODAY.plusDays(5), false));

    ArgumentCaptor<NewResourceRequest> saved = ArgumentCaptor.forClass(NewResourceRequest.class);
    verify(requests).insert(saved.capture());
    assertThat(saved.getValue().status()).isEqualTo(PENDING);
    assertThat(saved.getValue().requestDate()).isEqualTo(TestData.TODAY);
    assertThat(saved.getValue().deployDate()).isNull();
    assertThat(saved.getValue().requestedBy()).isEqualTo(REQUESTER);
  }

  @Test
  void requestDateFollowsTheClockNotStartupTime_R3() {
    stored(7L, PENDING);
    service.create(REQUESTER, create(TestData.TODAY.plusDays(5), false));
    clock.advance(Duration.ofDays(2));
    service.create(REQUESTER, create(TestData.TODAY.plusDays(5), false));

    ArgumentCaptor<NewResourceRequest> saved = ArgumentCaptor.forClass(NewResourceRequest.class);
    verify(requests, org.mockito.Mockito.times(2)).insert(saved.capture());
    assertThat(saved.getAllValues())
        .extracting(NewResourceRequest::requestDate)
        .containsExactly(TestData.TODAY, TestData.TODAY.plusDays(2));
  }

  @Test
  void ownerCanDeployImmediately() {
    when(incidentService.getOwned(OWNER, "MD-1")).thenReturn(TestData.incident("MD-1", OWNER));
    stored(7L, DEPLOYED);

    service.create(OWNER, create(TestData.TODAY, true));

    ArgumentCaptor<NewResourceRequest> saved = ArgumentCaptor.forClass(NewResourceRequest.class);
    verify(requests).insert(saved.capture());
    assertThat(saved.getValue().status()).isEqualTo(DEPLOYED);
    assertThat(saved.getValue().deployDate()).isEqualTo(TestData.TODAY);
  }

  @Test
  void deployImmediatelyForSomeoneElsesResourceIsForbidden() {
    assertThatThrownBy(() -> service.create(REQUESTER, create(TestData.TODAY, true)))
        .isInstanceOf(ForbiddenException.class);
    verify(requests, never()).insert(any());
  }

  @Test
  void deployImmediatelyWhileDeployedElsewhereConflicts() {
    when(incidentService.getOwned(OWNER, "MD-1")).thenReturn(TestData.incident("MD-1", OWNER));
    when(requests.isResourceDeployed(1001L)).thenReturn(true);
    assertThatThrownBy(() -> service.create(OWNER, create(TestData.TODAY, true)))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("currently deployed");
  }

  @Test
  void expectedReturnBeforeTodayIsAFieldError() {
    assertThatThrownBy(() -> service.create(REQUESTER, create(TestData.TODAY.minusDays(1), false)))
        .isInstanceOfSatisfying(
            FieldValidationException.class,
            ex ->
                assertThat(ex.errors()).extracting("field").containsExactly("expectedReturnDate"));
  }

  @Test
  void incidentOfAnotherUserIsNotFound_S3() {
    assertThatThrownBy(() -> service.create(STRANGER, create(TestData.TODAY, false)))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void unknownResourceIsNotFound() {
    when(resources.findOwnerUsername(1001L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.create(REQUESTER, create(TestData.TODAY, false)))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("Resource 1001");
  }

  @Test
  void onlyActivePairsBlockNewRequests_F14() {
    when(requests.existsActive(1001L, "MD-1")).thenReturn(true);
    assertThatThrownBy(() -> service.create(REQUESTER, create(TestData.TODAY, false)))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("active request");
  }

  @Test
  void concurrentInsertRaceMapsToConflict() {
    when(requests.insert(any())).thenThrow(new DuplicateKeyException("uq_request_active_pair"));
    assertThatThrownBy(() -> service.create(REQUESTER, create(TestData.TODAY, false)))
        .isInstanceOf(ConflictException.class);
  }

  // ---- deploy
  // ------------------------------------------------------------------------------------

  @Test
  void ownerDeploysPendingWithTodaysDate_F13() {
    stored(7L, PENDING);
    service.deploy(OWNER, 7L);
    verify(requests).updateStatus(7L, PENDING, DEPLOYED, TestData.TODAY, null);
  }

  @Test
  void requesterCannotDeploy() {
    stored(7L, PENDING);
    assertThatThrownBy(() -> service.deploy(REQUESTER, 7L)).isInstanceOf(ForbiddenException.class);
  }

  @Test
  void strangerCannotSeeRequest_S3() {
    stored(7L, PENDING);
    assertThatThrownBy(() -> service.deploy(STRANGER, 7L)).isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.get(STRANGER, 7L)).isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.returnResource(STRANGER, 7L))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void deployWhileResourceDeployedElsewhereConflicts_F13() {
    stored(7L, PENDING);
    when(requests.isResourceDeployed(1001L)).thenReturn(true);
    assertThatThrownBy(() -> service.deploy(OWNER, 7L)).isInstanceOf(ConflictException.class);
    verify(requests, never()).updateStatus(anyLong(), any(), any(), any(), any());
  }

  @Test
  void deployRaceOnUniqueKeyConflicts_F13() {
    stored(7L, PENDING);
    when(requests.updateStatus(7L, PENDING, DEPLOYED, TestData.TODAY, null))
        .thenThrow(new DuplicateKeyException("uq_request_one_deployment"));
    assertThatThrownBy(() -> service.deploy(OWNER, 7L))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("currently deployed");
  }

  @Test
  void deployNonPendingConflicts() {
    stored(7L, REJECTED);
    assertThatThrownBy(() -> service.deploy(OWNER, 7L))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Request 7 is REJECTED; cannot deploy");
  }

  @Test
  void lostUpdateRaceIsAConflict() {
    when(requests.findById(7L))
        .thenReturn(Optional.of(TestData.request(7L, PENDING, REQUESTER, OWNER)))
        .thenReturn(Optional.of(TestData.request(7L, CANCELLED, REQUESTER, OWNER)));
    when(requests.updateStatus(7L, PENDING, REJECTED, null, null)).thenReturn(0);
    assertThatThrownBy(() -> service.reject(OWNER, 7L))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Request 7 is CANCELLED; cannot reject");
  }

  // ---- reject / cancel / return
  // -------------------------------------------------------------------

  @Test
  void ownerRejectsPendingStatusOnly_F15() {
    stored(7L, PENDING);
    service.reject(OWNER, 7L);
    verify(requests).updateStatus(7L, PENDING, REJECTED, null, null);
  }

  @Test
  void requesterCannotReject() {
    stored(7L, PENDING);
    assertThatThrownBy(() -> service.reject(REQUESTER, 7L)).isInstanceOf(ForbiddenException.class);
  }

  @Test
  void requesterCancelsPending_F16() {
    stored(7L, PENDING);
    service.cancel(REQUESTER, 7L);
    verify(requests).updateStatus(7L, PENDING, CANCELLED, null, null);
  }

  @Test
  void ownerCannotCancel_F16() {
    stored(7L, PENDING);
    assertThatThrownBy(() -> service.cancel(OWNER, 7L)).isInstanceOf(ForbiddenException.class);
  }

  @Test
  void cancelAfterDeployConflicts() {
    stored(7L, DEPLOYED);
    assertThatThrownBy(() -> service.cancel(REQUESTER, 7L))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Request 7 is DEPLOYED; cannot cancel");
  }

  @Test
  void requesterOrOwnerReturnsDeployed_F12() {
    stored(7L, DEPLOYED);
    service.returnResource(REQUESTER, 7L);
    service.returnResource(OWNER, 7L);
    verify(requests, org.mockito.Mockito.times(2))
        .updateStatus(eq(7L), eq(DEPLOYED), eq(RETURNED), isNull(), eq(TestData.TODAY));
  }

  @Test
  void returnOfPendingConflicts() {
    stored(7L, PENDING);
    assertThatThrownBy(() -> service.returnResource(REQUESTER, 7L))
        .isInstanceOf(ConflictException.class);
  }

  // ---- list
  // ----------------------------------------------------------------------------------------

  @Test
  void listDelegatesByRole() {
    when(requests.findByRequester(REQUESTER, List.of(PENDING))).thenReturn(List.of());
    ResourceRequest owned = TestData.request(1L, PENDING, REQUESTER, OWNER);
    when(requests.findByResourceOwner(OWNER, List.of())).thenReturn(List.of(owned));

    assertThat(service.list(REQUESTER, RequestRole.REQUESTER, List.of(PENDING))).isEmpty();
    assertThat(service.list(OWNER, RequestRole.OWNER, List.of())).containsExactly(owned);
  }
}
