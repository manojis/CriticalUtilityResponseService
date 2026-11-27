package edu.gatech.erms.api.dto;

import edu.gatech.erms.domain.Declaration;

/** A declaration type. */
public record DeclarationResponse(String abbreviation, String name) {

  public static DeclarationResponse from(Declaration declaration) {
    return new DeclarationResponse(declaration.abbreviation(), declaration.name());
  }
}
