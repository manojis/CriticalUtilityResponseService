package edu.gatech.erms.domain;

import java.time.LocalDate;

/**
 * A user with the subtype-specific profile fields. Only the fields belonging to {@link #userType()}
 * are populated; the rest are {@code null}.
 */
public record UserProfile(
    String username,
    String name,
    UserType userType,
    String jobTitle,
    LocalDate hireDate,
    String municipalityCategory,
    String agencyNameLocalOffice,
    String headquarters,
    Long numberOfEmployees) {}
