package edu.gatech.erms.service;

import edu.gatech.erms.domain.Declaration;
import edu.gatech.erms.domain.Esf;
import edu.gatech.erms.repository.ReferenceDataRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ordered reference data for dropdowns (legacy defect F23: arrays, not hash maps). */
@Service
@Transactional(readOnly = true)
public class ReferenceDataService {

  private final ReferenceDataRepository referenceData;

  public ReferenceDataService(ReferenceDataRepository referenceData) {
    this.referenceData = referenceData;
  }

  /** All ESFs ordered by number. */
  public List<Esf> esfs() {
    return referenceData.findAllEsfs();
  }

  /** All cost units, alphabetical. */
  public List<String> costUnits() {
    return referenceData.findAllCostUnits();
  }

  /** All declarations ordered by name. */
  public List<Declaration> declarations() {
    return referenceData.findAllDeclarations();
  }
}
