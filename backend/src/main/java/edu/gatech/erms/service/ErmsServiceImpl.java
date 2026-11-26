package edu.gatech.erms.service;

import edu.gatech.erms.exception.BadRequestException;
import org.apache.commons.lang3.ArrayUtils;
import edu.gatech.erms.exception.ErmsRecordNotFoundException;
import edu.gatech.erms.gateway.SqlConnection;
import edu.gatech.erms.model.*;
import edu.gatech.erms.util.ErmsUtil;
import edu.gatech.erms.util.SqlConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.sql.*;
import java.util.HashMap;

/**
 * @author Manoj.Mohanan Nair
 * @Date 7/17/18
 */
@Service
public class ErmsServiceImpl implements ErmsService{

    @Autowired
    SqlConstants sqlConstants;

    @Autowired
    ErmsUtil util;

    @Autowired
    SqlConnection sqlConnection;

    @Autowired
    Declaration declaration;

    @Autowired
    Report report;

    HashMap<Integer,String> ESFs = new HashMap<>();

    Connection conn = null;

    public Connection getDBConnection(){
        System.out.println("ENTERING  getDBConnection()");
        try{
            Class.forName("com.mysql.cj.jdbc.Driver").newInstance();
            conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/cs6400_su18_team023?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC&useSSL=false&user=gatechUser&password=gatech123");
            System.out.println("Connection Established");
        }catch(ClassNotFoundException e){
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println("EXITING  getDBConnection()");
        return conn;
    }

    public boolean verifyUser(String username, String pwd) {
        System.out.println("ENTERING  verifyUser()");
        boolean result = false;
        try{
            String loginQuery = sqlConstants.LOGIN_QUERY;
            System.out.println("loginQuery: "+loginQuery);

            conn = getDBConnection();
            PreparedStatement ps = conn.prepareStatement(loginQuery);
            ps.setString(1, username);
            ps.setString(2, pwd);

            ResultSet rs = ps.executeQuery();
            while (rs.next())
            {
                result = true;
                String userData= rs.getString("username");
                String name = rs.getString("name");
                String password = rs.getString("password");
                String user_type = rs.getString("user_type");
                System.out.format("%s, %s, %s, %s\n", userData, name, password, user_type);
            }
            ps.close();
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println("EXITING  verifyUser()");
        return result;
    }

    public User getUserDetails(String username) {
        System.out.println("ENTERING  getUserDetails()");
        User userDetails=new User();
        String getUserQuery = sqlConstants.GET_USER;
        conn = getDBConnection();
        try {

            PreparedStatement ps = conn.prepareStatement(getUserQuery);
            ps.setString(1,username);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
            {
                System.out.println(rs.getString("username") + "  "+ rs.getString("name")+ "  "+ rs.getString("user_type"));
                userDetails.setUserName(rs.getString("username"));
                userDetails.setName(rs.getString("name"));
                userDetails.setUser_Type(rs.getString("user_type"));
            }
            ps.close();
            if(null!=userDetails.getUserName() && !userDetails.getUserName().isEmpty()){
                System.out.println("Obtained User Type is: "+ userDetails.getUser_Type());
                switch(userDetails.getUser_Type()){
                    case "Individual":         util.getIndividualDetails(userDetails);
                    break;
                    case "Municipality":       util.getMuncipalityDetails(userDetails);
                    break;
                    case "GovernmentAgency" :  util.getGovermentDetails(userDetails);
                    break;
                    case "Company" :           util.getCompanyDetails(userDetails);
                    break;
                }
            } else {
                throw new ErmsRecordNotFoundException();
            }
            ps.close();
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println("EXITING  getUserDetails()");
        return userDetails;
    }

    public HashMap<Integer,String> getEsfsDetails(){
        System.out.println("ENTERING  getEsfsDetails()");
        HashMap<Integer,String> output = new HashMap<>();
        String esfsQuery = sqlConstants.GET_ESFS_QUERY;
        Connection connEsfs = getDBConnection();
        Statement st = null;
        try {
            st = connEsfs.createStatement();
            ResultSet rs = st.executeQuery(esfsQuery);
            while (rs.next()) {
                output.put(rs.getInt("esf_number"),rs.getString("esf_description"));
                ESFs.put(rs.getInt("esf_number"),rs.getString("esf_description"));
            }
            st.close();
            connEsfs.close();
        }catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println("ENTERING  getEsfsDetails()");
        return output;
    }

    private String getESF(int esf_number){
        System.out.println("ENTERING  getESF()");
        String output = "";
        String esfsQuery = sqlConstants.GET_ESF;
        try{
            Connection connEsf = getDBConnection();
            PreparedStatement ps = connEsf.prepareStatement(esfsQuery);
            ps.setInt(1, esf_number);

            ResultSet rs = ps.executeQuery();
            while (rs.next())
            {
                output = rs.getString("esf_description");
            }
            ps.close();
            connEsf.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println("ENTERING  getESF()");
        return output;
    }

    public HashMap<String,String> getCostTypes(){
        System.out.println("ENTERING  getCostTypes()");

        HashMap<String,String> output = new HashMap<>();
        String costTypesQuery = sqlConstants.GET_COST_TYPES_QUERY;
        Connection connCostTypes = getDBConnection();
        Statement st = null;

        try {
            st = connCostTypes.createStatement();
            ResultSet rs = st.executeQuery(costTypesQuery);
            while (rs.next()) {
                output.put(rs.getString("unit"),rs.getString("unit"));
            }
            st.close();
            connCostTypes.close();
        }catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println("EXITING  getCostTypes()");
        return output;
    }

    public String addResource(Resource resource){
        System.out.println("ENTERING addResource()");
        String output = null;
        Integer capabilityReport = -1;
        Integer AdditionalEsfReport = -1;
        int newResourceId = sqlConnection.insertResource(resource);
        if(newResourceId>0){
            capabilityReport = sqlConnection.insertResourceCapability(resource,newResourceId);
            AdditionalEsfReport = sqlConnection.insertAdditionalEsfs(resource,newResourceId);
        }
        if(capabilityReport.intValue() == resource.capabilities.size()
                && AdditionalEsfReport.intValue() == resource.additional_esf.size()){
            output = "New resource added successfully ";
        }

        System.out.println("EXITING addResource()");
        return output;
    }

    public HashMap<String,String> getAllDeclarations(){
        System.out.println("ENTERING  getAllDeclarations()");
        HashMap<String,String> output = new HashMap<>();
        String esfsQuery = sqlConstants.GET_DECLARATIONS_QUERY;
        conn = getDBConnection();
        Statement st = null;
        try {
            st = conn.createStatement();
            ResultSet rs = st.executeQuery(esfsQuery);
            while (rs.next()) {
                output.put(rs.getString("declaration_abbreviation"),rs.getString("declaration_name"));
            }
            st.close();
            conn.close();
        }catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println("EXITING  getAllDeclarations()");
        return output;
    }

    public int getIncidentTypeCount(String abbr){
        System.out.println("ENTERING  getAllDecalarations()");

        int response = 0;
        String sqlQuery = sqlConstants.INCIDENT_TYPE_COUNT_QUERY;
        conn = getDBConnection();

        try {
            PreparedStatement ps = conn.prepareStatement(sqlQuery);
            ps.setString(1, abbr);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                response = rs.getInt(1);
            }
            ps.close();
            conn.close();
        }catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println("EXITING  getAllDecalarations()");
        return response;
    }

    public String addIncident(Incident incident){
        return sqlConnection.InsertIncident(incident);
    }

    public  HashMap<String,String> getAllIncident(String username){
        HashMap<String,String> output = new HashMap<>();
        Statement st = null;
        conn = getDBConnection();

        try {
            PreparedStatement ps = conn.prepareStatement(sqlConstants.GET_INCIDENTS);
            ps.setString(1,username );
            ResultSet rs = ps.executeQuery();

            while(rs.next()){
                output.put(rs.getString("incident_id"),rs.getString("description"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return output;
    }

    @Override
    public ArrayList<SearchResource> getAllResource(String keyword, int esf,int searchDistance,String incidentId) {
        System.out.println("ENTERING  getAllResource()");

        ArrayList<SearchResource> FinalOutput = new ArrayList<>();
        ArrayList<Integer> resourceIds = new ArrayList<>();

        // if incidentId and location alongwith with either of keyword or esf or both is searched
        if(null!=incidentId && !incidentId.isEmpty() && searchDistance > 0 &&
                ((null!=keyword && !keyword.isEmpty()) || esf>0)){

            ArrayList<SearchResource> incidentOutput = new ArrayList<>();
            ArrayList<Integer> incidentRscList = new ArrayList<>();

            incidentOutput = sqlConnection.getAllResources(keyword, esf);
            //getting all the resourceId for keyword/esf search and pushing it to a list
            incidentOutput.forEach((rsc)->{
                incidentRscList.add(rsc.getResource_id());
            });

            Integer[] integers = incidentRscList.toArray(new Integer[incidentRscList.size()]);
            int[] incidentRscArray = ArrayUtils.toPrimitive(integers);
            //use that list of resourceId to filter the incidentId/location query
            FinalOutput = sqlConnection.getResourceDetails(incidentId,searchDistance,incidentRscArray);
        }

        if(!(null!=incidentId && !incidentId.isEmpty() && searchDistance >0)){
            //call the method for search based on keyword and esf
            FinalOutput = sqlConnection.getAllResources(keyword, esf);

        } else if(!(null!=keyword && !keyword.isEmpty() && esf >0)){
            //call the method based on location and incident
            FinalOutput = sqlConnection.getResBasedOnLocationIncident(incidentId,searchDistance);
        }
        return FinalOutput;
    }

    public ArrayList<Report> getReport(String username){
        System.out.println("ENTERING  getAllResource()");

        ArrayList<Report> output = new ArrayList<>();
        Statement st = null;
        conn = getDBConnection();

        try {
            PreparedStatement ps = conn.prepareStatement(sqlConstants.GET_RESOURCE_REPORT);
            ps.setString(1,username );
            ResultSet rs = ps.executeQuery();

            while(rs.next()){

                report = new Report();

                int esf_key = rs.getInt("esf_number");
                report.setPrimary_esf(esf_key);
                report.setTotal_resources(rs.getInt("CO"));
                report.setResources_in_use(rs.getInt("AV"));
                report.setEsf_description(getESF(esf_key));

                output.add(report);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        System.out.println("EXITING  getAllResource()");
        return output;
    }

    public int getTotalESF(String username) {
        System.out.println("ENTERING  getAllResource()");

        int output = 0;
        conn = getDBConnection();
        try {
            PreparedStatement ps = conn.prepareStatement(sqlConstants.GET_TOTAL_ESFS);
            ps.setString(1,username );
            ResultSet rs = ps.executeQuery();

            while(rs.next()){
                output = rs.getInt("COUNT(resource_id)");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println("EXITING  getAllResource()");
        return output;
    }

    public int getTotalAvailable(String username) {
        System.out.println("ENTERING  getAllResource()");

        int output = 0;
        conn = getDBConnection();
        try {
            PreparedStatement ps = conn.prepareStatement(sqlConstants.GET_TOTAL_AVAILABLE);
            ps.setString(1,username );
            ResultSet rs = ps.executeQuery();

            while(rs.next()){

                output = rs.getInt("COUNT(is_available)");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println("EXITING getAllResource()");
        return output;
    }

    public Boolean actionRequest(ResourceAction rscAction){
        System.out.println("The Action generated by the user in actionRequest() is : "+rscAction.getAction());

        int success = -1;
        String output = null;
        String action = rscAction.getAction();

        if(null!=rscAction.getUsername() && !rscAction.getUsername().isEmpty()
                && null!=rscAction.getRequestedBy_username() && !rscAction.getRequestedBy_username().isEmpty()
                && null!= rscAction.getAction() && !rscAction.getAction().isEmpty()
                && "deploy".equalsIgnoreCase(action)){

            int RequestSuccess = sqlConnection.insertRequestAction(rscAction);
            if(RequestSuccess<0){
                System.out.println("Request for this resource has already been initiated by another user.Only deploy will be initiated.");
            } else {
                System.out.println("Request and Deploy will be initiated for this resource by the owner");
            }
            success = sqlConnection.actionDeployRequest(rscAction);

        } else if(null!=action && !action.isEmpty() && "request".equalsIgnoreCase(action)){
            success = sqlConnection.insertRequestAction(rscAction);
        } /*else if(null!=action && !action.isEmpty() && "deploy".equalsIgnoreCase(action)){
            success = sqlConnection.actionDeployRequest(rscAction);
        }*/ else if(null!=action && !action.isEmpty() && "return".equalsIgnoreCase(action)){
            success = sqlConnection.actionReturnRequest(rscAction);
        } else if(null!=action && !action.isEmpty() && "cancel".equalsIgnoreCase(action)){
            success = sqlConnection.actionCancelRequest(rscAction);
        } else if(null!=action && !action.isEmpty() && "reject".equalsIgnoreCase(action)){
            success = sqlConnection.actionRejectRequest(rscAction);
        }

        System.out.println("EXITING  getAllResource()");
        if (success > 0)

            return true;
        else
            return false;
    }

    public ArrayList<ResourceAction> getResDetailsByUser(ResourceAction resourceaction){
        System.out.println("ENTERING  getResDetailsByUser()");

        String resDetByOwner = sqlConstants.GET_OWNER_RESOURCE_IN_USE;
        String resDetByRequestor = sqlConstants.GET_CURRENT_USER_PENDING_REQUEST;
        Connection connResource = getDBConnection();
        ArrayList<ResourceAction> output = new ArrayList<>();
        if(null!=resourceaction.getUsername() && !resourceaction.getUsername().isEmpty()
                && !(null!=resourceaction.getRequestedBy_username()
                && !resourceaction.getRequestedBy_username().isEmpty())){

            System.out.println("Query details: "+resDetByOwner);
            try {
                ResourceAction response = null;
                PreparedStatement psRes = connResource.prepareStatement(resDetByOwner);
                psRes.setString(1, resourceaction.getUsername());
                psRes.setInt(2, 0);
                ResultSet res = psRes.executeQuery();

                while(res.next()){
                    response = new ResourceAction();
                    response.setResource_id(res.getInt("resource_id"));
                    response.setResource_name(res.getString("resource_name"));
                    response.setUsername(res.getString("username"));
                    response.setIncident_id(res.getString("incident_id"));
                    response.setDescription(res.getString("description"));
                    response.setRequestedBy_username(res.getString("requested_user"));
                    response.setRequest_date(res.getDate("request_date"));
                    response.setDeploy_date(res.getDate("deploy_date"));
                    response.setEstimated_return_date(res.getDate("estimated_return_date"));
                    output.add(response);
                }
                psRes.close();
                connResource.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }

        } else if(null!=resourceaction.getRequestedBy_username() && !resourceaction.getRequestedBy_username().isEmpty()){

            System.out.println("Query Details: "+resDetByRequestor);
            try {
                ResourceAction resp = null;
                //check if is_available ==> 1 also needs to be put later
                PreparedStatement psg = connResource.prepareStatement(resDetByRequestor);
                psg.setString(1, resourceaction.getRequestedBy_username());
                ResultSet rsg = psg.executeQuery();

                while(rsg.next()){
                    resp = new ResourceAction();
                    resp.setResource_id(rsg.getInt("resource_id"));
                    resp.setResource_name(rsg.getString("resource_name"));
                    resp.setIncident_id(rsg.getString("incident_id"));
                    resp.setDescription(rsg.getString("description"));
                    resp.setRequestedBy_username(rsg.getString("username"));
                    resp.setEstimated_return_date(rsg.getDate("estimated_return_date"));
                    output.add(resp);
                }
                psg.close();
                connResource.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        System.out.println("EXITING  getResDetailsByUser()");
        return output;
    }


    public ArrayList<ResourceAction> getreceivedRequestByUser(String username){
        System.out.println("ENTERING  getResDetailsByUser()");
        ArrayList<ResourceAction> output = new ArrayList<>();
        Connection conn = getDBConnection();

        try {
            ResourceAction response = null;
            PreparedStatement ps = conn.prepareStatement(sqlConstants.GET_RECEIVED_REQUESTS);
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();

            while(rs.next()){
                response = new ResourceAction();
                response.setResource_id(rs.getInt("resource_id"));
                response.setIncident_id(rs.getString("incident_id"));
                response.setResource_name(rs.getString("resource_name"));
                response.setDescription(rs.getString("description"));
                response.setUsername(rs.getString("username"));
                response.setIs_available(rs.getInt("is_available"));
                response.setRequestedBy_username(rs.getString("requested_user"));
                response.setEstimated_return_date(rs.getDate("estimated_return_date"));
                output.add(response);
            }
            ps.close();
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        System.out.println("ENTERING  getResDetailsByUser()");
        return output;
    }

}
