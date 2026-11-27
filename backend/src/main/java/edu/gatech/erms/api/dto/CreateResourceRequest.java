package edu.gatech.erms.api.dto;

import edu.gatech.erms.service.CreateResourceCommand;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * {@code POST /api/v1/resources} body. ESF / cost-unit existence, additional-ESF uniqueness and
 * capability normalisation are checked by the service.
 */
public record CreateResourceRequest(
    @NotBlank @Size(min = 3, max = 100) String resourceName,
    @Size(max = 100) String model,
    @NotNull @Positive Integer primaryEsf,
    @Size(max = 15) List<@NotNull @Positive Integer> additionalEsfs,
    @Size(max = 50) List<String> capabilities,
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal cost,
    @NotBlank @Size(max = 20) String costUnit,
    @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
    @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
    @DecimalMin(value = "0", inclusive = false) @Digits(integer = 6, fraction = 2)
        BigDecimal maxDistanceKm) {

  public CreateResourceCommand toCommand() {
    return new CreateResourceCommand(
        resourceName,
        model,
        primaryEsf,
        additionalEsfs,
        capabilities,
        cost,
        costUnit,
        latitude,
        longitude,
        maxDistanceKm);
  }
}
