package edu.gatech.erms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.gatech.erms.domain.EsfReportRow;
import edu.gatech.erms.repository.ReportRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportServiceTest {

  @Test
  void totalsSumAllRows_F11() {
    ReportRepository repository = mock(ReportRepository.class);
    when(repository.resourcesByEsf("alice"))
        .thenReturn(
            List.of(
                new EsfReportRow(1, "Transportation", 2, 1),
                new EsfReportRow(2, "Communications", 0, 0),
                new EsfReportRow(3, "Public Works and Engineering", 3, 2)));

    ReportService.EsfReport report = new ReportService(repository).resourcesByEsf("alice");

    assertThat(report.rows()).hasSize(3);
    assertThat(report.totalResources()).isEqualTo(5);
    assertThat(report.resourcesInUse()).isEqualTo(3);
  }
}
