package edu.gatech.erms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.gatech.erms.domain.Incident;
import edu.gatech.erms.exception.FieldValidationException;
import edu.gatech.erms.exception.NotFoundException;
import edu.gatech.erms.repository.IncidentRepository;
import edu.gatech.erms.support.MutableClock;
import edu.gatech.erms.support.TestData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IncidentServiceTest {

  private final IncidentRepository incidents = mock(IncidentRepository.class);
  private final MutableClock clock = MutableClock.utc("2026-11-25T10:00:00Z");
  private final IncidentService service = new IncidentService(incidents, clock);

  private static CreateIncidentCommand command(String abbr, LocalDate date) {
    return new CreateIncidentCommand(
        abbr, date, "Flooding on I-75", new BigDecimal("33.7490"), new BigDecimal("-84.3880"));
  }

  @Test
  void createsIncidentWithServerIdAndCorrectCoordinates_F7_F8() {
    when(incidents.allocateIncidentId("MD")).thenReturn(Optional.of("MD-3"));

    Incident created = service.create("alice", command("MD", TestData.TODAY));

    ArgumentCaptor<Incident> saved = ArgumentCaptor.forClass(Incident.class);
    verify(incidents).insert(saved.capture());
    assertThat(saved.getValue()).isEqualTo(created);
    assertThat(created.incidentId()).isEqualTo("MD-3");
    assertThat(created.declarationAbbreviation()).isEqualTo("MD");
    assertThat(created.ownerUsername()).isEqualTo("alice");
    assertThat(created.latitude()).isEqualByComparingTo("33.749");
    assertThat(created.longitude()).isEqualByComparingTo("-84.388");
    assertThat(created.latitude().scale()).isEqualTo(6);
  }

  @Test
  void canonicalAbbreviationComesFromTheSequence() {
    when(incidents.allocateIncidentId("md")).thenReturn(Optional.of("MD-1"));
    assertThat(service.create("alice", command(" md ", TestData.TODAY)).declarationAbbreviation())
        .isEqualTo("MD");
  }

  @Test
  void futureDateIsRejectedUsingInjectedClock_R3() {
    assertThatThrownBy(() -> service.create("alice", command("MD", TestData.TODAY.plusDays(1))))
        .isInstanceOfSatisfying(
            FieldValidationException.class,
            ex -> assertThat(ex.errors()).extracting("field").containsExactly("incidentDate"));
    verify(incidents, never()).insert(any());
  }

  @Test
  void unknownDeclarationIsAFieldError() {
    when(incidents.allocateIncidentId("ZZ")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.create("alice", command("ZZ", TestData.TODAY)))
        .isInstanceOfSatisfying(
            FieldValidationException.class,
            ex ->
                assertThat(ex.errors())
                    .extracting("field")
                    .containsExactly("declarationAbbreviation"));
  }

  @Test
  void getOwnedHidesOtherUsersIncidents_S3() {
    when(incidents.findById("MD-1")).thenReturn(Optional.of(TestData.incident("MD-1", "bob")));
    assertThatThrownBy(() -> service.getOwned("alice", "MD-1"))
        .isInstanceOf(NotFoundException.class);
    assertThat(service.getOwned("bob", "MD-1").incidentId()).isEqualTo("MD-1");
  }

  @Test
  void listsOwnIncidents() {
    when(incidents.findByOwner("alice")).thenReturn(List.of(TestData.incident("MD-1", "alice")));
    assertThat(service.listOwned("alice")).hasSize(1);
  }
}
