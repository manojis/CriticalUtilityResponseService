package edu.gatech.erms.gateway;

import edu.gatech.erms.model.Incident;
import edu.gatech.erms.model.Resource;
import edu.gatech.erms.model.ResourceAction;
import edu.gatech.erms.model.SearchResource;
import edu.gatech.erms.util.SqlConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.sql.Date;
import java.util.*;
import java.util.stream.IntStream;

/**
 * @author Manoj.Mohanan Nair
 * @Date 7/18/18
 */
@Service
public class SqlConnection {

    @Autowired
    SqlConstants sqlConstants;

    Connection conn = null;

    String connectionDriver = "com.mysql.cj.jdbc.Driver";
    String connUrl = "jdbc:mysql://localhost:3306/cs6400_su18_team023?useUnicode=true&useJDBCCompliantTimezoneShift=true" +
            "&useLegacyDatetimeCode=false&serverTimezone=UTC&useSSL=false&user=gatechUser&password=gatech123";
    // create a java calendar instance
    Calendar calendar = Calendar.getInstance();
    java.util.Date currentDate = calendar.getTime();
    java.sql.Date date = new java.sql.Date(currentDate.getTime());

    public Connection getDBConnection(){
        System.out.println("ENTERING getDBConnection()");
        try{

            Class.forName(connectionDriver).newInstance();
            conn = DriverManager.getConnection(connUrl);
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
        System.out.println("EXITING getDBConnection()");
        return conn;
    }

    public int insertResource(Resource resource){
        System.out.println("ENTERING getDBConnection()");
        int resourceId = -1;
        String addNewResource = sqlConstants.INSERT_NEW_RESOURCE;

        try {
            Connection connInsert = getDBConnection();
            PreparedStatement ps = connInsert.prepareStatement(addNewResource,Statement.RETURN_GENERATED_KEYS);
            ps.setString(1,resource.getUsername());
            ps.setString(2,resource.getResource_name());
            ps.setDate(3, (Date) resource.getNext_available());
            ps.setBoolean(4,resource.isIs_available());
            ps.setString(5,resource.getModel());
            ps.setInt(6,resource.getCost());
            ps.setString(7,resource.getUnit());
            ps.setFloat(8,resource.getLongitude());
            ps.setFloat(9,resource.getLatitude());
            ps.setFloat(10,resource.getMax_distance());
            ps.setFloat(11,resource.getPrimary_esf());
            ps.executeUpdate();
            ResultSet response = ps.getGeneratedKeys();
            if(response.next()){
                resourceId = response.getInt(1);
                System.out.println("Resource Id for the newly inserted data: "+resourceId);
            }
            ps.close();
            connInsert.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        System.out.println("ENTERING getDBConnection()");
        return resourceId;
    }

    public int insertResourceCapability(Resource resource, int resourceId){
        System.out.println("ENTERING insertResourceCapability()");
        int success = -1;
        String sqlQuery = sqlConstants.INSERT_RESOURCE_CAPABILITY;
        Connection connRes = getDBConnection();

        try {
            connRes.setAutoCommit(false);
            PreparedStatement ps = connRes.prepareStatement(sqlQuery);
            ps.setInt(1,resourceId);
            for(String capability : resource.capabilities){
                ps.setString(2,capability);
                ps.addBatch();
            }
            success = IntStream.of(ps.executeBatch()).sum();
            connRes.commit();
            ps.close();
            connRes.close();

        } catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println("EXITING insertResourceCapability()");
        return success;
    }

    public int insertAdditionalEsfs(Resource resource, int resourceId){
        System.out.println("ENTERING insertAdditionalEsfs()");
        int success = -1;
        String InsertQuery = sqlConstants.INSERT_ADDITIONAL_ESF;
        Connection connections = getDBConnection();

        try {
            connections.setAutoCommit(false);
            PreparedStatement ps = connections.prepareStatement(InsertQuery);
            ps.setInt(1,resourceId);
            for(int esfVal : resource.additional_esf){
                ps.setInt(2,esfVal);
                ps.addBatch();
            }
            success = IntStream.of(ps.executeBatch()).sum();
            connections.commit();
            ps.close();
            connections.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println("EXITING insertAdditionalEsfs()");
        return success;
    }

    public String InsertIncident(Incident incident){
        System.out.println("ENTERING addIncident()");

        String output = null;
        int success = -1;
        String sqlQuery = sqlConstants.INSERT_NEW_INCIDENT;
        conn = getDBConnection();

        try {
            PreparedStatement ps = conn.prepareStatement(sqlQuery);
            ps.setString(1,incident.getIncident_id());
            ps.setString(2,incident.getUsername());
            ps.setDate(3, incident.getIncident_date());
            ps.setString(4,incident.getDescription());
            ps.setFloat(5,incident.getLatitude());
            ps.setFloat(6,incident.getLongitude());
            ps.setString(7,incident.getDeclaration_abbreviation());
            success = ps.executeUpdate();

            if(success>0){
                output = "Incident Added successfully";
            }else{
                output = "Incident not added. Please Try again!!";
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        System.out.println("EXITING addIncident()");
        return output;
    }

    /**
     *
     * @param keyword
     * @param esf
     * Search based on the keyword criteria or ESF or full resource Search
     * @return
     */
    public ArrayList<SearchResource> getAllResources(String keyword, int esf){
        System.out.println("ENTERING getAllResources()");

        ArrayList<SearchResource> output = new ArrayList<>();
        Statement stmt = null;
        String sqlQuery = null;
        ResultSet rs = null;

        conn = getDBConnection();

        try {
            if (null != keyword && !keyword.isEmpty() && esf > 0) {
                System.out.println("getting all the resources based on keyword and esf");

                sqlQuery = sqlConstants.SEARCH_RESOURCE_BY_ESF_AND_KEYWORD;
                System.out.println("keyword QUERY: "+sqlQuery);

                PreparedStatement psg = conn.prepareStatement(sqlQuery);
                psg.setString(1,"%" + keyword + "%");
                psg.setString(2,"%" + keyword + "%");
                psg.setString(3,"%" + keyword + "%");
                psg.setInt(4,esf);
                psg.setInt(5,esf);
                rs = psg.executeQuery();

            } else if (null != keyword && !keyword.isEmpty()) {
                System.out.println("getting all the resources based on keyword");

                sqlQuery = sqlConstants.SEARCH_RESOURCE_BY_KEYWORD;
                System.out.println("keyword QUERY: "+sqlQuery);

                PreparedStatement psg = conn.prepareStatement(sqlQuery);
                psg.setString(1,"%" + keyword + "%");
                psg.setString(2,"%" + keyword + "%");
                psg.setString(3,"%" + keyword + "%");
                rs = psg.executeQuery();

            } else if (esf > 0) {
                System.out.println("getting all the resources based on esf");

                sqlQuery = sqlConstants.SEARCH_RESOURCE_BY_ESF;
                System.out.println("keyword QUERY: "+sqlQuery);

                PreparedStatement psg = conn.prepareStatement(sqlQuery);
                psg.setInt(1,esf);
                psg.setInt(2,esf);
                rs = psg.executeQuery();

            } else {
                System.out.println("getting all the resources");

                sqlQuery = sqlConstants.SEARCH_ALL_RESOURCE;
                System.out.println("keyword QUERY: "+sqlQuery);

                stmt = conn.createStatement();
                rs = stmt.executeQuery(sqlQuery);
            }

            while(rs.next()){
                SearchResource outputResource = new SearchResource();
                outputResource.setResource_id(rs.getInt("resource_id"));
                outputResource.setResource_name(rs.getString("resource_name"));
                outputResource.setUsername(rs.getString("username"));
                outputResource.setCost(rs.getInt("cost"));
                outputResource.setUnit(rs.getString("unit"));
                outputResource.setIs_available(rs.getBoolean("is_available"));
                outputResource.setNext_available(rs.getDate("next_available"));
                output.add(outputResource);
            }
            conn.close();
        }catch(SQLException e){
            e.printStackTrace();
        }
        System.out.println("EXITING getAllResources()");
        return output;
    }

    /**
     *
     * @param incidentId
     * @param SearchDistance
     * Searches based on the Incident and Distance provided in the search criteria
     * @return
     */
    public ArrayList<SearchResource> getResBasedOnLocationIncident(String incidentId, int SearchDistance){
        System.out.println("ENTERING getResBasedOnLocationIncident()");

        ArrayList<SearchResource> output = new ArrayList<>();
        String sqlQuery = sqlConstants.SEARCH_BY_LOCATION_INCIDENT;
        System.out.println("keyword QUERY: "+sqlQuery);
        SearchResource outputRes = null;
        conn = getDBConnection();
        try {
            PreparedStatement ps = conn.prepareStatement(sqlQuery);
            ps.setString(1,incidentId);
            ps.setFloat(2,SearchDistance);
            ResultSet rs = ps.executeQuery();

            while(rs.next()){
                outputRes = new SearchResource();
                outputRes.setResource_id(rs.getInt("resource_id"));
                outputRes.setResource_name(rs.getString("resource_name"));
                outputRes.setUsername(rs.getString("username"));
                outputRes.setCost(rs.getInt("cost"));
                outputRes.setUnit(rs.getString("unit"));
                outputRes.setIs_available(rs.getBoolean("is_available"));
                outputRes.setNext_available(rs.getDate("next_available"));
                outputRes.setDistance(rs.getFloat(9));
                output.add(outputRes);
            }
            rs.close();
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println("EXITING getResBasedOnLocationIncident()");
        return output;
    }

    public ArrayList<SearchResource> getResourceDetails(String incidentId,int searchDistance,int[] incidentRscArray){
        System.out.println("ENTERING getResourceDetails()");
        ArrayList<SearchResource> output = new ArrayList<>();

        String sqlQuery = sqlConstants.GET_RESOURCE_DETAILS;
        System.out.println("keyword QUERY: "+sqlQuery);
        SearchResource outputRsc = null;
        conn = getDBConnection();

        try {
            PreparedStatement ps = conn.prepareStatement(sqlQuery);

            for(int input: incidentRscArray){
                ps.setString(1,incidentId);
                ps.setInt(2,input);
                ps.setFloat(3, searchDistance);
                ResultSet rs = ps.executeQuery();

                while(rs.next()){
                    outputRsc = new SearchResource();
                    outputRsc.setResource_id(rs.getInt("resource_id"));
                    outputRsc.setResource_name(rs.getString("resource_name"));
                    outputRsc.setUsername(rs.getString("username"));
                    outputRsc.setCost(rs.getInt("cost"));
                    outputRsc.setUnit(rs.getString("unit"));
                    outputRsc.setIs_available(rs.getBoolean("is_available"));
                    outputRsc.setNext_available(rs.getDate("next_available"));
                    outputRsc.setDistance(rs.getFloat(9));
                    output.add(outputRsc);
                }
            }
            output.sort(new Comparator<SearchResource>() {
                @Override
                public int compare(SearchResource o1, SearchResource o2) {
                    return (int) (o1.getDistance() - o2.getDistance());
                }
            });
            ps.close();
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println("ENTERING getResourceDetails()");
        return output;
    }

    public int insertRequestAction(ResourceAction rscAction){
        System.out.println("ENTERING insertRequestAction");
        int response = -1;
        int success = -1;
        int finalresponse = -1;

        /**check for the status of the resource_incidentId.
        *if the count of the query is >0 i.e status is null or rejected, dont allow new insertion or request
         * if the row is null, then insert the new row
         */
        String addNewActionRequestQuery = sqlConstants.GET_RESOURCE_ENQUIRY;
        String insertNewRequestQuery = sqlConstants.INSERT_NEW_REQUEST;
        String updateResourceQuery = sqlConstants.UPDATE_RESOURCE_REQUEST;
        Connection connection = getDBConnection();

        try {
            PreparedStatement ps = connection.prepareStatement(addNewActionRequestQuery);
            ps.setInt(1, rscAction.getResource_id());
            ps.setString(2, rscAction.getIncident_id());
            ResultSet rs = ps.executeQuery();

            while(rs.next()){
                response = 1;
                System.out.format("%d, %s, %s\n", rs.getInt("resource_id"),
                        rs.getString("incident_id"), rs.getString("resource_status"));
            }
            ps.close();

            if(response>0){
                finalresponse = -1;
            }else {
                PreparedStatement psc = connection.prepareStatement(insertNewRequestQuery);
                psc.setInt(1, rscAction.getResource_id());
                psc.setString(2, rscAction.getIncident_id());
                psc.setString(3, rscAction.getRequestedBy_username());
                psc.setDate(4, date);
                psc.setDate(5, (Date) rscAction.getNext_available());
                success = psc.executeUpdate();
                psc.close();

                PreparedStatement psg = connection.prepareStatement(updateResourceQuery);
                psg.setDate(1, (Date) rscAction.getNext_available());
                psg.setInt(2, rscAction.getResource_id());
                finalresponse = psg.executeUpdate();
                psg.close();
            }
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println("EXITING insertRequestAction");
        return finalresponse;
    }

    public int actionDeployRequest(ResourceAction rscAction){
        System.out.println("ENTERING insertRequestAction");

        int response =-1;
        String deployRequestedByQuery = sqlConstants.UPDATE_REQUESTEDBY_DEPLOY;
        String deployResourceQuery =sqlConstants.UPDATE_RESOURCE_DEPLOY;

        System.out.println("current deploy date: "+ date.toString());
        Connection connections = getDBConnection();

        try {
            PreparedStatement ps = connections.prepareStatement(deployRequestedByQuery);
            ps.setDate(1, (Date)date);

            //should be Accepted here as it is deploy Action
            ps.setString(2,rscAction.getActionStatus());
            ps.setInt(3, rscAction.getResource_id());
            ps.setString(4, rscAction.getIncident_id());
            ps.setString(5, rscAction.getRequestedBy_username());
            int tempResponse = ps.executeUpdate();
            ps.close();
            if(tempResponse>0){
                PreparedStatement psg = connections.prepareStatement(deployResourceQuery);
                psg.setInt(1, rscAction.getResource_id());
                response = psg.executeUpdate();
                psg.close();
            }
            connections.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println("EXITING insertRequestAction");
        return response;
    }

    public int actionReturnRequest(ResourceAction action){
        System.out.println("ENTERING actionReturnRequest()");
        int response =-1;

        Connection connection = getDBConnection();
        try {
            PreparedStatement psg = connection.prepareStatement(sqlConstants.RETURN_RESOURCEID_RESOURCE);
            psg.setInt(1, action.getResource_id());
            int tempResponse = psg.executeUpdate();
            psg.close();
            if(tempResponse >0){
                PreparedStatement ps = connection.prepareStatement(sqlConstants.RETURN_RESOURCEID_REQUESTEDBY);
                ps.setDate(1, date);
                ps.setInt(2, action.getResource_id());
                response = ps.executeUpdate();
                ps.close();
            }
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        System.out.println("EXITING actionReturnRequest()");
        return response;
    }

    public int actionCancelRequest(ResourceAction action){
        System.out.println("ENTERING actionCancelRequest()");

        Connection connect = getDBConnection();
        int response = -1;

        try {
            PreparedStatement ps = connect.prepareStatement(sqlConstants.CANCEL_RESOURCE_REQUEST);
            ps.setInt(1, action.getResource_id());
            ps.setString(2,action.getIncident_id());
            response = ps.executeUpdate();
            ps.close();
            connect.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println("EXITING actionReturnRequest()");
        return response;
    }

    public int actionRejectRequest(ResourceAction action){
        System.out.println("ENTERING actionRejectRequest()");

        Connection conn = getDBConnection();
        int response = -1;

        try {
            PreparedStatement psg = conn.prepareStatement(sqlConstants.REJECT_RESOURCE_REQUEST);
            psg.setInt(1, action.getResource_id());
            int tempResponse = psg.executeUpdate();
            psg.close();

            if(tempResponse>0){
                PreparedStatement ps = conn.prepareStatement(sqlConstants.REJECT_REQUESTEDBY_REQUEST);
                ps.setString(1,"Reject");
                ps.setInt(2, action.getResource_id());
                ps.setString(3,action.getIncident_id());
                response = ps.executeUpdate();
                ps.close();
            }
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println("EXITING actionRejectRequest()");
        return response;
    }
}
