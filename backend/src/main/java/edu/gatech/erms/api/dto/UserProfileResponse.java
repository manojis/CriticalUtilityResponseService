package edu.gatech.erms.api.dto;

import edu.gatech.erms.domain.UserProfile;
import edu.gatech.erms.domain.UserType;
import java.time.LocalDate;

/**
 * {@code GET /api/v1/me}. Only the fields of the caller's {@code userType} are present; the others
 * are null and therefore omitted from the JSON.
 */
public record UserProfileResponse(
    String username,
    String name,
    UserType userType,
    String jobTitle,
    LocalDate hireDate,
    String municipalityCategory,
    String agencyNameLocalOffice,
    String headquarters,
    Long numberOfEmployees) {

  public static UserProfileResponse from(UserProfile p) {
    return switch (p.userType()) {
      case INDIVIDUAL ->
          new UserProfileResponse(
              p.username(),
              p.name(),
              p.userType(),
              p.jobTitle(),
              p.hireDate(),
              null,
              null,
              null,
              null);
      case MUNICIPALITY ->
          new UserProfileResponse(
              p.username(),
              p.name(),
              p.userType(),
              null,
              null,
              p.municipalityCategory(),
              null,
              null,
              null);
      case GOVERNMENT_AGENCY ->
          new UserProfileResponse(
              p.username(),
              p.name(),
              p.userType(),
              null,
              null,
              null,
              p.agencyNameLocalOffice(),
              null,
              null);
      case COMPANY ->
          new UserProfileResponse(
              p.username(),
              p.name(),
              p.userType(),
              null,
              null,
              null,
              null,
              p.headquarters(),
              p.numberOfEmployees());
    };
  }
}
