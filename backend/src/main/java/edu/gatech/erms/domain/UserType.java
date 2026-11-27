package edu.gatech.erms.domain;

/** The kind of organisation behind a user account. All types have the same permissions. */
public enum UserType {
  INDIVIDUAL,
  MUNICIPALITY,
  GOVERNMENT_AGENCY,
  COMPANY
}
