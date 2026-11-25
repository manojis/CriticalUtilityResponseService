package edu.gatech.erms.model;


import org.springframework.stereotype.Repository;

import java.util.Date;

/**
 * @author Manoj.Mohanan Nair
 * @Date 7/17/18
 */
@Repository
public class User {

    private static final long serialVersionUID = -7788619177798333712L;

    public String username;
    public String name;
    public String password;
    public String user_Type;
    public String job_title;
    public Date hire_date;
    public String municipality_category;
    public String agency_name_local_office;
    public String headquarter;
    public int num_of_employees;


    public String getUserName() {
        return username;
    }

    public void setUserName(String username) {
        this.username = username;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUser_Type() {
        return user_Type;
    }

    public void setUser_Type(String user_Type) {
        this.user_Type = user_Type;
    }

    public String getJob_title() {
        return job_title;
    }

    public void setJob_title(String job_title) {
        this.job_title = job_title;
    }

    public Date getHire_date() {
        return hire_date;
    }

    public void setHire_date(Date hire_date) {
        this.hire_date = hire_date;
    }

    public String getMunicipality_category() {
        return municipality_category;
    }

    public void setMunicipality_category(String municipality_category) {
        this.municipality_category = municipality_category;
    }

    public String getAgency_name_local_office() {
        return agency_name_local_office;
    }

    public void setAgency_name_local_office(String agency_name_local_office) {
        this.agency_name_local_office = agency_name_local_office;
    }

    public String getHeadquarter() {
        return headquarter;
    }

    public void setHeadquarter(String headquarter) {
        this.headquarter = headquarter;
    }

    public int getNum_of_employees() {
        return num_of_employees;
    }

    public void setNum_of_employees(int num_of_employees) {
        this.num_of_employees = num_of_employees;
    }
}
