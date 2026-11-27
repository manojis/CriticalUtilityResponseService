package edu.gatech.erms.api.dto;

import edu.gatech.erms.domain.EsfReportRow;
import edu.gatech.erms.service.ReportService.EsfReport;
import java.util.List;

/** {@code GET /api/v1/reports/resources-by-esf}: all 15 ESFs plus totals. */
public record EsfReportResponse(List<Row> rows, Totals totals) {

  public static EsfReportResponse from(EsfReport report) {
    return new EsfReportResponse(
        report.rows().stream().map(Row::from).toList(),
        new Totals(report.totalResources(), report.resourcesInUse()));
  }

  /** One ESF row. */
  public record Row(
      int esfNumber, String esfDescription, long totalResources, long resourcesInUse) {

    static Row from(EsfReportRow row) {
      return new Row(
          row.esfNumber(), row.esfDescription(), row.totalResources(), row.resourcesInUse());
    }
  }

  /** Sums over all rows. */
  public record Totals(long totalResources, long resourcesInUse) {}
}
