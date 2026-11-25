package edu.gatech.erms.util;

import edu.gatech.erms.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.*;

/**
 * @author Manoj.Mohanan Nair
 * @Date 7/18/18
 */
@Service
public class ErmsUtil {

    Connection conn = null;

    @Autowired
    SqlConstants sqlConstants;

    public Connection getDBConnection(){
        System.out.println("ENTERING  getDBConnection()");
        try{

            Class.forName("com.mysql.cj.jdbc.Driver").newInstance();
            conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/cs6400_su18_team023?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC&useSSL=false&user=gatechUser&password=gatech123");

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

    public User getIndividualDetails(User user){
        System.out.println("ENTERING  getIndividualDetails()");
        String getQuery = sqlConstants.GET_INDIVIDUALIUSER;
        conn = getDBConnection();
        try {
            PreparedStatement ps = conn.prepareStatement(getQuery);
            ps.setString(1,user.getUserName());
            ResultSet rs = ps.executeQuery();
            while (rs.next())
            {
                if(user.getUserName().equalsIgnoreCase(rs.getString("username"))){
                    user.setJob_title(rs.getString("job_title"));
                    user.setHire_date(rs.getDate("hire_date"));
                } else {
                    System.out.println("INVALID USER OUTPUT");
                    throw new SQLDataException();
                }
            }
            ps.close();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        System.out.println("EXITING  getIndividualDetails()");
        return user;
    }

    public User getMuncipalityDetails(User user){
        System.out.println("ENTERING  getMuncipalityDetails()");
        String getQuery = sqlConstants.GET_MUNCIPALITY_USER;
        conn = getDBConnection();
        try {
            PreparedStatement ps = conn.prepareStatement(getQuery);
            ps.setString(1,user.getUserName());
            ResultSet rs = ps.executeQuery();
            while (rs.next())
            {
                if(user.getUserName().equalsIgnoreCase(rs.getString("username"))){
                    user.setMunicipality_category(rs.getString("municipality_category"));
                } else {
                    System.out.println("INVALID USER OUTPUT");
                    throw new SQLDataException();
                }
            }
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println("EXITING  getMuncipalityDetails()");
        return user;
    }

    public User getGovermentDetails(User user){
        System.out.println("ENTERING  getGovermentDetails()");
        String getQuery = sqlConstants.GET_GOVERNMENT_USER;
        conn = getDBConnection();
        try {
            PreparedStatement ps = conn.prepareStatement(getQuery);
            ps.setString(1,user.getUserName());
            ResultSet rs = ps.executeQuery();
            while (rs.next())
            {
                if(user.getUserName().equalsIgnoreCase(rs.getString("username"))){
                    user.setAgency_name_local_office(rs.getString("agency_name_local_office"));
                } else {
                    System.out.println("INVALID USER OUTPUT");
                    throw new SQLDataException();
                }
            }
            ps.close();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        System.out.println("EXITING  getGovermentDetails()");
        return user;
    }

    public User getCompanyDetails(User user){
        System.out.println("ENTERING  getCompanyDetails()");
        String getQuery = sqlConstants.GET_COMPANY_USER;
        conn = getDBConnection();
        try {
            PreparedStatement ps = conn.prepareStatement(getQuery);
            ps.setString(1,user.getUserName());
            ResultSet rs = ps.executeQuery();
            while (rs.next())
            {
                if(user.getUserName().equalsIgnoreCase(rs.getString("username"))){
                    user.setHeadquarter(rs.getString("headquarter"));
                    user.setNum_of_employees(rs.getInt("num_of_employees"));
                } else {
                    System.out.println("INVALID USER OUTPUT");
                    throw new SQLDataException();
                }
            }
            ps.close();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        System.out.println("EXITING  getCompanyDetails()");
        return user;
    }

}
