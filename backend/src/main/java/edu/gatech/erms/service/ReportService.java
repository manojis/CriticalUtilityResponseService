package edu.gatech.erms.service;

import edu.gatech.erms.domain.EsfReportRow;
import edu.gatech.erms.repository.ReportRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resources-by-primary-ESF report for the caller's own resources. */
@Service
public class ReportService {

  private final ReportRepository reports;

  public ReportService(ReportRepository reports) {
    this.reports = reports;
  }

  @Transactional(readOnly = true)
  public EsfReport resourcesByEsf(String ownerUsername) {
    List<EsfReportRow> rows = reports.resourcesByEsf(ownerUsername);
    long total = rows.stream().mapToLong(EsfReportRow::totalResources).sum();
    long inUse = rows.stream().mapToLong(EsfReportRow::resourcesInUse).sum();
    return new EsfReport(rows, total, inUse);
  }

  /** All 15 ESF rows plus totals. */
  public record EsfReport(List<EsfReportRow> rows, long totalResources, long resourcesInUse) {

    public EsfReport {
      rows = List.copyOf(rows);
    }
  }
}
