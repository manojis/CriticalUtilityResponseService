package edu.gatech.erms.api.dto;

import edu.gatech.erms.domain.Esf;

/** An Emergency Support Function. */
public record EsfResponse(int esfNumber, String description) {

  public static EsfResponse from(Esf esf) {
    return new EsfResponse(esf.esfNumber(), esf.description());
  }
}
