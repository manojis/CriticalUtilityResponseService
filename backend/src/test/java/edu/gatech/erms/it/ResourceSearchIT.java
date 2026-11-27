package edu.gatech.erms.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.gatech.erms.domain.UserType;
import edu.gatech.erms.support.AbstractIntegrationTest;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** Search semantics (docs/ARCHITECTURE.md section 3.8) and legacy defects F1–F6, S3, S9, F21. */
class ResourceSearchIT extends AbstractIntegrationTest {

  // Incident location (Atlanta) used as the distance origin.
  private static final double LAT = 33.749;
  private static final double LON = -84.388;

  @BeforeEach
  void users() {
    createUser("alice");
    createUser("acme", UserType.COMPANY);
  }

  private ResultActions search(String username, String query) throws Exception {
    return mvc.perform(as(username, get("/api/v1/resources?" + query))).andExpect(status().isOk());
  }

  private ResultActions keyword(String keyword) throws Exception {
    return mvc.perform(as("alice", get("/api/v1/resources").param("keyword", keyword)))
        .andExpect(status().isOk());
  }

  @Test
  void keywordFindsResourceWithoutCapabilitiesByName_F1() throws Exception {
    long id = createResource("acme", "Hydraulic Crane", 1, 1);

    search("alice", "keyword=crane")
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].resourceId").value(id));
  }

  @Test
  void keywordMatchesModelAndCapabilityWithoutDuplicates_F1() throws Exception {
    long byModel = createResource("acme", "Truck", "CRANE-X", 1, List.of(), List.of(), 1, 1, null);
    long byCaps =
        createResource(
            "acme",
            "Boat",
            null,
            1,
            List.of(),
            List.of("crane arm", "Crane lift", "other"),
            1,
            1,
            null);
    createResource("acme", "Generator", null, 1, List.of(), List.of("power"), 1, 1, null);

    search("alice", "keyword=Crane")
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(
            jsonPath("$.content[*].resourceId")
                .value(Matchers.containsInAnyOrder((int) byModel, (int) byCaps)));
  }

  @Test
  void keywordWildcardsAreLiteral_S9() throws Exception {
    createResource("acme", "Pump 100% duty", 1, 1);
    createResource("acme", "Pump 1000 duty", 1, 1);
    createResource("acme", "Hose_A", 1, 1);
    createResource("acme", "HoseBA", 1, 1);

    keyword("%").andExpect(jsonPath("$.totalElements").value(1));
    keyword("100%").andExpect(jsonPath("$.content[0].resourceName").value("Pump 100% duty"));
    keyword("e_A")
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].resourceName").value("Hose_A"));
    keyword("\\").andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void esfMatchesPrimaryEvenWithoutAdditionalEsfs_F2() throws Exception {
    long primaryOnly =
        createResource("acme", "Fire Engine", null, 4, List.of(), List.of(), 1, 1, null);
    long viaAdditional =
        createResource("acme", "Tanker", null, 1, List.of(4, 7), List.of(), 1, 1, null);
    createResource("acme", "Radio", null, 2, List.of(7), List.of(), 1, 1, null);

    search("alice", "esf=4")
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(
            jsonPath("$.content[*].resourceId")
                .value(Matchers.containsInAnyOrder((int) primaryOnly, (int) viaAdditional)));
  }

  @Test
  void keywordAndEsfCombineWithAnd_F3() throws Exception {
    long match = createResource("acme", "Fire Crane", null, 4, List.of(), List.of(), 1, 1, null);
    createResource("acme", "Fire Hose", null, 4, List.of(), List.of(), 1, 1, null);
    createResource("acme", "Crane", null, 3, List.of(), List.of(), 1, 1, null);

    search("alice", "keyword=crane&esf=4")
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].resourceId").value(match));
  }

  @Test
  void colocatedResourceHasZeroDistanceAndNullMaxDistanceIsUnlimited_F4() throws Exception {
    String incident = createIncident("alice", "MD", LAT, LON);
    long colocated =
        createResource("acme", "Colocated", null, 1, List.of(), List.of(), LAT, LON, 5.0);
    long farUnlimited =
        createResource(
            "acme", "Far Unlimited", null, 1, List.of(), List.of(), 40.7128, -74.006, null);
    createResource("acme", "Far Limited", null, 1, List.of(), List.of(), 40.7128, -74.006, 100.0);

    search("alice", "incidentId=" + incident)
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].resourceId").value(colocated))
        .andExpect(jsonPath("$.content[0].distanceKm").value(0.0))
        .andExpect(jsonPath("$.content[1].resourceId").value(farUnlimited))
        .andExpect(jsonPath("$.content[1].distanceKm").value(Matchers.closeTo(1200.0, 30.0)));
  }

  @Test
  void maxDistanceAndWithinAreInclusive_F4() throws Exception {
    String incident = createIncident("alice", "MD", 0, 0);
    // ~111.19 km per degree of latitude on the MySQL sphere (radius 6370986 m)
    createResource("acme", "Edge", null, 1, List.of(), List.of(), 1, 0, 111.20);
    createResource("acme", "Beyond", null, 1, List.of(), List.of(), 1, 0, 111.10);

    search("alice", "incidentId=" + incident)
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].resourceName").value("Edge"))
        .andExpect(jsonPath("$.content[0].distanceKm").value(111.2));
    search("alice", "incidentId=" + incident + "&distanceKm=111.2")
        .andExpect(jsonPath("$.totalElements").value(1));
    search("alice", "incidentId=" + incident + "&distanceKm=111")
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void keywordIsNotIgnoredWhenCombinedWithDistance_F5() throws Exception {
    String incident = createIncident("alice", "MD", LAT, LON);
    long crane =
        createResource("acme", "Near Crane", null, 3, List.of(), List.of(), LAT, LON + 0.01, null);
    createResource("acme", "Near Boat", null, 3, List.of(), List.of(), LAT, LON + 0.01, null);
    createResource("acme", "Far Crane", null, 3, List.of(), List.of(), LAT + 2, LON, null);

    search("alice", "keyword=crane&incidentId=" + incident + "&distanceKm=50")
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].resourceId").value(crane));
    search("alice", "esf=3&incidentId=" + incident + "&distanceKm=50")
        .andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  void subKilometreDifferencesAreOrderedCorrectly_F6() throws Exception {
    String incident = createIncident("alice", "MD", 0, 0);
    // 0.0090 deg ≈ 1.00 km, 0.0045 deg ≈ 0.50 km, 0.0081 deg ≈ 0.90 km
    long oneKm = createResource("acme", "A at 1.0", null, 1, List.of(), List.of(), 0.0090, 0, null);
    long halfKm =
        createResource("acme", "B at 0.5", null, 1, List.of(), List.of(), 0.0045, 0, null);
    long ninetyM =
        createResource("acme", "C at 0.9", null, 1, List.of(), List.of(), 0.0081, 0, null);

    search("alice", "incidentId=" + incident)
        .andExpect(jsonPath("$.content[0].resourceId").value(halfKm))
        .andExpect(jsonPath("$.content[1].resourceId").value(ninetyM))
        .andExpect(jsonPath("$.content[2].resourceId").value(oneKm))
        .andExpect(jsonPath("$.content[0].distanceKm").value(0.5))
        .andExpect(jsonPath("$.content[1].distanceKm").value(0.9))
        .andExpect(jsonPath("$.content[2].distanceKm").value(1.0));
  }

  @Test
  void anotherUsersIncidentCannotBeUsedAsOrigin_S3() throws Exception {
    createUser("bob");
    String bobs = createIncident("bob", "MD", LAT, LON);

    mvc.perform(as("alice", get("/api/v1/resources?incidentId=" + bobs)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("resource.not_found"));
  }

  @Test
  void noFiltersListsEverythingPaginatedByName_F21() throws Exception {
    for (String name : List.of("Echo", "Alpha", "Delta", "Charlie", "Bravo")) {
      createResource("acme", name, 1, 1);
    }

    search("alice", "size=2")
        .andExpect(jsonPath("$.totalElements").value(5))
        .andExpect(jsonPath("$.totalPages").value(3))
        .andExpect(jsonPath("$.content[0].resourceName").value("Alpha"))
        .andExpect(jsonPath("$.content[1].resourceName").value("Bravo"))
        .andExpect(jsonPath("$.content[0].distanceKm").doesNotExist());
    search("alice", "size=2&page=2")
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].resourceName").value("Echo"));
    search("alice", "size=2&page=9").andExpect(jsonPath("$.content.length()").value(0));
  }

  @Test
  void distanceWithoutIncidentIs400() throws Exception {
    mvc.perform(as("alice", get("/api/v1/resources?distanceKm=5")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("distanceKm"));
  }

  @Test
  void rowsShowAvailabilityAndOwner() throws Exception {
    String incident = createIncident("acme", "MD", LAT, LON);
    long id = createResource("acme", "Busy Crane", LAT, LON);
    requestResource("acme", id, incident, true);

    search("alice", "keyword=busy")
        .andExpect(jsonPath("$.content[0].available").value(false))
        .andExpect(jsonPath("$.content[0].nextAvailableDate").value(today().plusDays(7).toString()))
        .andExpect(jsonPath("$.content[0].ownerUsername").value("acme"))
        .andExpect(jsonPath("$.content[0].ownerName").value("Name of acme"))
        .andExpect(jsonPath("$.content[0].cost").value(10.0))
        .andExpect(jsonPath("$.content[0].costUnit").value("Hour"));
  }
}
