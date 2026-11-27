package edu.gatech.erms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.gatech.erms.domain.NewResource;
import edu.gatech.erms.domain.PageResult;
import edu.gatech.erms.domain.ResourceSearchCriteria;
import edu.gatech.erms.exception.FieldValidationException;
import edu.gatech.erms.exception.NotFoundException;
import edu.gatech.erms.repository.ReferenceDataRepository;
import edu.gatech.erms.repository.ResourceRepository;
import edu.gatech.erms.support.TestData;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ResourceServiceTest {

  private final ResourceRepository resources = mock(ResourceRepository.class);
  private final ReferenceDataRepository referenceData = mock(ReferenceDataRepository.class);
  private final IncidentService incidentService = mock(IncidentService.class);
  private final ResourceService service =
      new ResourceService(resources, referenceData, incidentService);

  @BeforeEach
  void setUp() {
    when(referenceData.findExistingEsfNumbers(anyCollection()))
        .thenAnswer(inv -> {
          Set<Integer> result = new java.util.HashSet<>();
          for (Object n : inv.<java.util.Collection<?>>getArgument(0)) {
            int value = (Integer) n;
            if (value >= 1 && value <= 15) {
              result.add(value);
            }
          }
          return result;
        });
    when(referenceData.findCostUnit("Hour")).thenReturn(Optional.of("Hour"));
    when(referenceData.findCostUnit("hour")).thenReturn(Optional.of("Hour"));
    when(resources.insert(any())).thenReturn(42L);
    when(resources.findDetail(42L)).thenReturn(Optional.of(TestData.resource(42L, "alice")));
  }

  private static CreateResourceCommand command(
      Integer primary, List<Integer> additional, List<String> capabilities, String unit) {
    return new CreateResourceCommand(
        "  Hydraulic Crane ",
        "  ",
        primary,
        additional,
        capabilities,
        new BigDecimal("150"),
        unit,
        new BigDecimal("33.77"),
        new BigDecimal("-84.39"),
        null);
  }

  @Test
  void nullListsAreTreatedAsEmpty_R7() {
    service.create("alice", command(3, null, null, "Hour"));

    ArgumentCaptor<NewResource> saved = ArgumentCaptor.forClass(NewResource.class);
    verify(resources).insert(saved.capture());
    NewResource resource = saved.getValue();
    assertThat(resource.additionalEsfs()).isEmpty();
    assertThat(resource.capabilities()).isEmpty();
    assertThat(resource.primaryEsf()).isEqualTo(3);
    assertThat(resource.resourceName()).isEqualTo("Hydraulic Crane");
    assertThat(resource.model()).isNull();
    assertThat(resource.cost()).isEqualByComparingTo("150.00");
    assertThat(resource.cost().scale()).isEqualTo(2);
    assertThat(resource.maxDistanceKm()).isNull();
    verify(resources).insertCapabilities(42L, List.of());
    verify(resources).insertAdditionalEsfs(42L, List.of());
  }

  @Test
  void capabilitiesAreTrimmedAndDeduplicatedCaseAndAccentInsensitively() {
    service.create(
        "alice",
        command(
            3,
            List.of(1, 7),
            List.of(" Lift 20t ", "lift 20T", "Café", "cafe", "Night ops"),
            "hour"));

    ArgumentCaptor<NewResource> saved = ArgumentCaptor.forClass(NewResource.class);
    verify(resources).insert(saved.capture());
    assertThat(saved.getValue().capabilities()).containsExactly("Lift 20t", "Café", "Night ops");
    assertThat(saved.getValue().costUnit()).isEqualTo("Hour");
    verify(resources).insertAdditionalEsfs(42L, List.of(1, 7));
  }

  @Test
  void invalidInputCollectsAllFieldErrors() {
    List<String> capabilities = new ArrayList<>(Arrays.asList(" ", "x".repeat(101), null));
    assertThatThrownBy(() ->
            service.create("alice", command(99, List.of(99, 99, 1), capabilities, "Fortnight")))
        .isInstanceOfSatisfying(
            FieldValidationException.class,
            ex ->
                assertThat(ex.errors())
                    .extracting("field")
                    .contains(
                        "primaryEsf",
                        "additionalEsfs",
                        "costUnit",
                        "capabilities[0]",
                        "capabilities[1]",
                        "capabilities[2]"));
    verify(resources, never()).insert(any());
  }

  @Test
  void additionalEsfsMustNotContainPrimary() {
    assertThatThrownBy(() -> service.create("alice", command(3, List.of(3), null, "Hour")))
        .isInstanceOfSatisfying(
            FieldValidationException.class,
            ex ->
                assertThat(ex.errors())
                    .extracting("message")
                    .containsExactly("must not contain the primary ESF"));
  }

  @Test
  void getUnknownResourceIsNotFound() {
    when(resources.findDetail(7L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.get(7L)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void searchWithoutFiltersPassesNulls_F21() {
    when(resources.search(any())).thenReturn(new PageResult<>(List.of(), 0, 20, 0));

    service.search("alice", "  ", null, "", null, 0, 20);

    ArgumentCaptor<ResourceSearchCriteria> criteria =
        ArgumentCaptor.forClass(ResourceSearchCriteria.class);
    verify(resources).search(criteria.capture());
    assertThat(criteria.getValue().keyword()).isNull();
    assertThat(criteria.getValue().origin()).isNull();
    assertThat(criteria.getValue().esf()).isNull();
  }

  @Test
  void distanceRequiresIncident() {
    assertThatThrownBy(() -> service.search("alice", null, null, null, BigDecimal.TEN, 0, 20))
        .isInstanceOfSatisfying(
            FieldValidationException.class,
            ex -> assertThat(ex.errors()).extracting("field").containsExactly("distanceKm"));
  }

  @Test
  void searchOriginMustBeCallersIncident_S3() {
    when(incidentService.getOwned("alice", "MD-9"))
        .thenThrow(NotFoundException.of("Incident", "MD-9"));
    assertThatThrownBy(() -> service.search("alice", null, null, "MD-9", null, 0, 20))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void searchUsesOwnedIncidentAsOrigin() {
    when(incidentService.getOwned("alice", "MD-1")).thenReturn(TestData.incident("MD-1", "alice"));
    when(resources.search(any())).thenReturn(new PageResult<>(List.of(), 1, 5, 0));

    service.search("alice", " crane ", 3, "MD-1", BigDecimal.ONE, 1, 5);

    ArgumentCaptor<ResourceSearchCriteria> criteria =
        ArgumentCaptor.forClass(ResourceSearchCriteria.class);
    verify(resources).search(criteria.capture());
    assertThat(criteria.getValue().keyword()).isEqualTo("crane");
    assertThat(criteria.getValue().origin().incidentId()).isEqualTo("MD-1");
    assertThat(criteria.getValue().offset()).isEqualTo(5);
  }
}
