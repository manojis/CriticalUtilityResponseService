package edu.gatech.erms.gateway;

import edu.gatech.erms.model.SearchResource;

import java.sql.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.sql.*;
import java.sql.Date;
import java.util.*;

/**
 * @author Manoj.Mohanan Nair
 * @Date 7/21/18
 */
public class TestingMain {

    static Connection connection = null;

    public static void main(String[] args){

        String GET_RESOURCE_DETAILS = "SELECT DISTINCT(res.resource_id),res.resource_name, res.username,res.cost,res.unit,res.is_available,res.next_available,res.max_distance," +
                " (6371 * Acos(Cos(Radians(inc.latitude)) * Cos(Radians(res.latitude)) * Cos(Radians(res.longitude) - Radians(inc.longitude)) + Sin(Radians(inc.latitude)) * Sin(Radians(res.latitude)))) AS DISTANCE " +
                " FROM Resource AS res,Incident AS inc WHERE inc.incident_id = ? AND res.resource_id = ? " +
                " HAVING DISTANCE<res.max_distance " +
                " AND DISTANCE < ? " +
                " ORDER BY DISTANCE";

        int[] inputRsc = {5,6,9,12};
        connection = getDBConnection();
        ArrayList<SearchResource> output = new ArrayList<>();

        try {
            PreparedStatement ps = connection.prepareStatement(GET_RESOURCE_DETAILS);
            for(int x: inputRsc){
                ps.setString(1,"MD-1");
                ps.setInt(2,x);
                ps.setFloat(3, 300);
                ResultSet rs = ps.executeQuery();

                while(rs.next()){
                    SearchResource outputRes = new SearchResource();

                    outputRes.setResource_id(rs.getInt("resource_id"));
                    outputRes.setResource_name(rs.getString("resource_name"));
                    outputRes.setUsername(rs.getString("username"));
                    outputRes.setCost(rs.getInt("cost"));
                    outputRes.setUnit(rs.getString("unit"));
                    outputRes.setIs_available(rs.getBoolean("is_available"));
                    outputRes.setNext_available(rs.getDate("next_available"));
                    outputRes.setDistance(rs.getFloat(8));
                    output.add(outputRes);
                }
            }

            ps.close();
            connection.close();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static Connection getDBConnection(){
        System.out.println("ENTERING  connection()");

       try{
            String conn = "jdbc:mysql://localhost:3306/cs6400_su18_team023?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC&useSSL=false&user=gatechUser&password=gatech123";
            Class.forName("com.mysql.cj.jdbc.Driver").newInstance();
            connection = DriverManager.getConnection(conn);

        }catch(ClassNotFoundException ex){
            ex.printStackTrace();
        } catch (IllegalAccessException ex) {
            ex.printStackTrace();
        } catch (InstantiationException ex) {
            ex.printStackTrace();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        System.out.println("EXITING  connection()");
        return connection;
    }
}
