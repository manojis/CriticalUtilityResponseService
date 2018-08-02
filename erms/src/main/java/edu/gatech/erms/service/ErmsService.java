package edu.gatech.erms.service;

import edu.gatech.erms.model.*;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author Manoj.Mohanan Nair
 * @Date 7/17/18
 */
public interface ErmsService {

    boolean verifyUser(String user, String pwd);
    User getUserDetails(String username);
    HashMap<Integer,String> getEsfsDetails();
    HashMap<String,String> getCostTypes();
    String addResource(Resource resource);
    int getIncidentTypeCount(String abbr);
    String addIncident(Incident incident);
    HashMap<String,String> getAllIncident(String username);
	HashMap<String,String> getAllDeclarations();
    ArrayList<SearchResource> getAllResource(String keyword, int esf,int distance,String incidentId);
    ArrayList<Report> getReport(String username);
    int getTotalESF(String username);
    int getTotalAvailable(String username);
    Boolean actionRequest(ResourceAction rscAction);
    ArrayList<ResourceAction> getResDetailsByUser(ResourceAction resourceaction);
    ArrayList<ResourceAction> getreceivedRequestByUser(String username);
}
