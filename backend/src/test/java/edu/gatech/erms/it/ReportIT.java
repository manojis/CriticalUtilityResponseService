package edu.gatech.erms.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.gatech.erms.domain.UserType;
import edu.gatech.erms.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** Legacy defects F10 (report SQL failed on MySQL 8) and F11 (in-use totals). */
class ReportIT extends AbstractIntegrationTest {

  private ResultActions report(String user) throws Exception {
    return mvc.perform(as(user, get("/api/v1/reports/resources-by-esf")))
        .andExpect(status().isOk());
  }

  @Test
  void listsAll15EsfsWithZeros_F10() throws Exception {
    createUser("alice");
    report("alice")
        .andExpect(jsonPath("$.rows.length()").value(15))
        .andExpect(jsonPath("$.rows[0].esfNumber").value(1))
        .andExpect(jsonPath("$.rows[0].esfDescription").value("Transportation"))
        .andExpect(jsonPath("$.rows[14].esfNumber").value(15))
        .andExpect(jsonPath("$.rows[14].totalResources").value(0))
        .andExpect(jsonPath("$.totals.totalResources").value(0))
        .andExpect(jsonPath("$.totals.resourcesInUse").value(0));
  }

  @Test
  void countsOwnResourcesByPrimaryEsfAndUpdatesAfterDeployAndReturn_F11() throws Exception {
    createUser("alice");
    createUser("acme", UserType.COMPANY);
    String incident = createIncident("alice", "MD", 1, 1);
    long crane = createResource("acme", "Crane", null, 3, List.of(1), List.of(), 1, 1, null);
    createResource("acme", "Excavator", null, 3, List.of(), List.of(), 1, 1, null);
    createResource("acme", "Bus", null, 1, List.of(), List.of(), 1, 1, null);
    createResource("alice", "Not acme's", null, 3, List.of(), List.of(), 1, 1, null);

    report("acme")
        .andExpect(jsonPath("$.rows[0].totalResources").value(1))
        .andExpect(jsonPath("$.rows[2].totalResources").value(2))
        .andExpect(jsonPath("$.rows[2].resourcesInUse").value(0))
        .andExpect(jsonPath("$.totals.totalResources").value(3));

    long req = requestResource("alice", crane, incident, false);
    action("acme", req, "deploy").andExpect(status().isOk());
    report("acme")
        .andExpect(jsonPath("$.rows[2].resourcesInUse").value(1))
        .andExpect(jsonPath("$.rows[0].resourcesInUse").value(0))
        .andExpect(jsonPath("$.totals.resourcesInUse").value(1));

    action("alice", req, "return").andExpect(status().isOk());
    report("acme").andExpect(jsonPath("$.totals.resourcesInUse").value(0));
  }
}
