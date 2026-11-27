package edu.gatech.erms.api.dto;

import edu.gatech.erms.service.CreateResourceRequestCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * {@code POST /api/v1/resource-requests} body. {@code deployImmediately} defaults to {@code false}.
 */
public record CreateResourceRequestRequest(
    @NotNull @Positive Long resourceId,
    @NotBlank @Size(max = 16) String incidentId,
    @NotNull LocalDate expectedReturnDate,
    Boolean deployImmediately) {

  public CreateResourceRequestCommand toCommand() {
    return new CreateResourceRequestCommand(
        resourceId, incidentId, expectedReturnDate, Boolean.TRUE.equals(deployImmediately));
  }
}
