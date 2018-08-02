package edu.gatech.erms.util;

import org.springframework.stereotype.Service;

/**
 * @author Manoj.Mohanan Nair
 * @Date 7/18/18
 */
@Service
public class SqlConstants {

    public static String LOGIN_QUERY = "SELECT * FROM User where username = ? and password = ?";
    public static String GET_USER = "SELECT * FROM User where username= ?";
    public static String GET_INDIVIDUALIUSER = "SELECT * FROM Individual where username = ?";
    public static String GET_MUNCIPALITY_USER ="SELECT * FROM Municipality where username = ?";
    public static String GET_GOVERNMENT_USER ="SELECT * FROM GovernmentAgency where username = ?";
    public static String GET_COMPANY_USER ="SELECT * FROM Company where username = ?";
    public static String GET_ESFS_QUERY = "SELECT * FROM ESF";
    public static String GET_ESF = "SELECT esf_description FROM ESF WHERE esf_number = ?";
    public static String GET_COST_TYPES_QUERY = "SELECT * FROM CostUnit";

    public static String INSERT_NEW_RESOURCE = "INSERT INTO Resource (username,resource_name,next_available," +
            "is_available,model,cost,unit,longitude,latitude,max_distance,primary_esf) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?)";
    public static String INSERT_RESOURCE_CAPABILITY = "INSERT INTO ResourceCapability (resource_id,capability) " +
            "values (?,?)";
    public static String INSERT_ADDITIONAL_ESF = "INSERT INTO AdditionalESF (resource_id,esf_number) " +
            "values (?,?)";
    public static String GET_DECLARATIONS_QUERY = "SELECT * FROM Declaration";

    public static String INCIDENT_TYPE_COUNT_QUERY = "SELECT COUNT(incident_id) FROM Incident WHERE declaration_abbreviation = ? ";

    public static String INSERT_NEW_INCIDENT = "INSERT INTO Incident (incident_id,username,incident_date," +
            "description,longitude,latitude,declaration_abbreviation) VALUES (?,?,?,?,?,?,?)";

    public static String GET_INCIDENTS = "SELECT incident_id, description FROM Incident where username=?";

    public static String SEARCH_ALL_RESOURCE = "SELECT DISTINCT(resource_id),resource_name,username,cost,unit,is_available, next_available " +
            "FROM Resource";

    public static String SEARCH_RESOURCE_BY_KEYWORD = "SELECT DISTINCT(res.resource_id),res.resource_name,res.username,res.cost," +
            "res.unit,res.is_available, res.next_available FROM Resource AS res, ResourceCapability AS rsc " +
            "WHERE res.resource_id = rsc.resource_id AND rsc.capability LIKE ? OR res.resource_name LIKE ? OR res.model LIKE ?";

    public static String SEARCH_RESOURCE_BY_ESF = "SELECT DISTINCT(res.resource_id),res.resource_name,res.username,res.cost," +
            "res.unit,res.is_available, res.next_available FROM Resource AS res, AdditionalESF AS aef " +
            "WHERE (res.primary_esf = ? OR (aef.resource_id = res.resource_id  AND aef.esf_number = ? ))";

    public static String SEARCH_RESOURCE_BY_ESF_AND_KEYWORD = "SELECT DISTINCT(res.resource_id),res.resource_name,res.username,res.cost," +
            "res.unit,res.is_available, res.next_available FROM Resource AS res, ResourceCapability AS rsc, AdditionalESF AS aef " +
            "WHERE (res.resource_id = rsc.resource_id AND rsc.capability LIKE ? OR res.resource_name LIKE ? OR res.model LIKE ? ) " +
            "AND (res.primary_esf = ? OR (aef.resource_id = res.resource_id  AND aef.esf_number = ? ))";


    public static String SEARCH_BY_LOCATION_INCIDENT = "SELECT DISTINCT(res.resource_id),res.resource_name, res.username,res.cost,res.unit,res.is_available,res.next_available,res.max_distance," +
            " (6371 * Acos(Cos(Radians(inc.latitude)) * Cos(Radians(res.latitude)) * Cos(Radians(res.longitude) - Radians(inc.longitude)) + Sin(Radians(inc.latitude)) * Sin(Radians(res.latitude)))) AS DISTANCE " +
            " FROM Resource AS res,Incident AS inc WHERE inc.incident_id = ? " +
            " HAVING DISTANCE<res.max_distance " +
            " AND DISTANCE < ? " +
            " ORDER BY DISTANCE";

    public static String GET_RESOURCE_DETAILS = "SELECT DISTINCT(res.resource_id),res.resource_name, res.username,res.cost,res.unit,res.is_available,res.next_available,res.max_distance," +
            " (6371 * Acos(Cos(Radians(inc.latitude)) * Cos(Radians(res.latitude)) * Cos(Radians(res.longitude) - Radians(inc.longitude)) + Sin(Radians(inc.latitude)) * Sin(Radians(res.latitude)))) AS DISTANCE " +
            " FROM Resource AS res,Incident AS inc WHERE inc.incident_id = ? AND res.resource_id = ? " +
            " HAVING DISTANCE<res.max_distance " +
            " AND DISTANCE < ? " +
            " ORDER BY DISTANCE";

    public static String GET_RESOURCE_ENQUIRY ="SELECT resource_id,incident_id, resource_status" +
            " FROM RequestedBy WHERE resource_id = ? AND incident_id = ? ";
    //AND (resource_status IS NULL OR 'ACCEPTED' OR 'REJECTED'

    public static String INSERT_NEW_REQUEST = "INSERT INTO RequestedBy (resource_id, Incident_id,requested_user, request_date,estimated_return_date) " +
            "VALUES (?, ?, ?, ?, ?); ";

    public static String UPDATE_RESOURCE_REQUEST = "UPDATE Resource SET next_available = ? WHERE resource_id = ?";

    public static String UPDATE_REQUESTEDBY_DEPLOY = "UPDATE RequestedBy SET deploy_date= ?, resource_status= ? " +
            "WHERE resource_id = ? AND incident_id = ? AND requested_user = ? ";

    public static String UPDATE_RESOURCE_DEPLOY = "UPDATE Resource SET is_available = 0 WHERE resource_id =? ";

    public static String GET_OWNER_RESOURCE_IN_USE ="SELECT res.resource_id, res.resource_name,res.username,rbc.incident_id,inc.description, " +
            "rbc.requested_user, rbc.request_date, rbc.deploy_date,rbc.estimated_return_date " +
            "FROM Incident AS inc, Resource AS res, RequestedBy AS rbc where inc.username = ? " +
            "AND inc.incident_id = rbc.incident_id AND rbc.resource_id = res.resource_id AND res.is_available = ? AND rbc.deploy_date IS NOT NULL";

    public static String RETURN_RESOURCEID_RESOURCE = "UPDATE Resource SET is_available = 1, next_available = NULL WHERE resource_id =?";
    public static String RETURN_RESOURCEID_REQUESTEDBY = "UPDATE RequestedBy SET actual_return_date = ? WHERE resource_id = ? ";

    //check if is_available ==> 1 also needs to be put later
    public static String GET_CURRENT_USER_PENDING_REQUEST = "SELECT res.resource_id, res.resource_name, rbc.incident_id, inc.description, " +
            "res.username, rbc.estimated_return_date FROM Incident AS inc, Resource AS res, RequestedBy AS rbc " +
            "where inc.username = ? AND rbc.requested_user= inc.username AND inc.incident_id = rbc.incident_id " +
            "AND res.resource_id = rbc.resource_id AND rbc.request_date IS NOT NULL AND rbc.deploy_date IS NULL " +
            "AND rbc.estimated_return_date IS NOT NULL AND resource_status IS NULL";

    public static String CANCEL_RESOURCE_REQUEST = "DELETE FROM RequestedBy where resource_id =? AND incident_id =? ";

    public static String REJECT_RESOURCE_REQUEST = "UPDATE Resource SET next_available = NULL WHERE resource_id =? ";
    public static String REJECT_REQUESTEDBY_REQUEST = "UPDATE RequestedBy SET resource_status = ? WHERE resource_id = ? AND incident_id = ?";

    public static String GET_RESOURCE_REPORT = "SELECT E1.esf_number, sub.CO, sub.AV FROM ESF E1 LEFT JOIN " +
            "( SELECT T1.primary_ESF, COUNT(T1.primary_esf) AS CO, SUM(T2.is_available='false') AS AV " +
            "FROM Resource T1 INNER JOIN Resource T2 ON (T1.resource_id = T2.resource_id AND T1.username= ? ) " +
            "GROUP BY T1.primary_esf ASC) sub ON E1.esf_number = sub.primary_esf  ORDER BY E1.esf_number ASC;";

    public static String GET_TOTAL_ESFS = "SELECT COUNT(resource_id) FROM Resource WHERE username= ? ;";

    public static String GET_TOTAL_AVAILABLE = " SELECT COUNT(is_available) FROM Resource WHERE username= ? AND is_available='false'; ";

    public static String GET_RECEIVED_REQUESTS = "SELECT res.resource_id, rbc.incident_id,res.resource_name, inc.description, " +
            "res.username,res.is_available, rbc.requested_user,rbc.estimated_return_date FROM Resource as res INNER JOIN RequestedBy as rbc " +
            "ON res.resource_id = rbc.resource_id  INNER JOIN Incident as inc ON rbc.incident_id = inc.incident_id " +
            "WHERE res.username = ? AND rbc.deploy_date IS NULL";

}
