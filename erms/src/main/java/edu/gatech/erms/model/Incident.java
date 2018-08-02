package edu.gatech.erms.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.stereotype.Repository;

import java.sql.Date;

/**
 * @author Manoj.Mohanan Nair
 * @Date 7/20/18
 */
@Repository
public class Incident {

    public String incident_id;
    public String username;

    @JsonFormat(pattern="MM-dd-yyyy")
    public Date incident_date;

    public String description;
    public float longitude;
    public float latitude;
    public String declaration_abbreviation;

    public String getIncident_id() {
        return incident_id;
    }

    public void setIncident_id(String incident_id) {
        this.incident_id = incident_id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Date getIncident_date() {
        return incident_date;
    }

    public void setIncident_date(Date incident_date) {
        this.incident_date = incident_date;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public float getLongitude() {
        return longitude;
    }

    public void setLongitude(float longitude) {
        this.longitude = longitude;
    }

    public float getLatitude() {
        return latitude;
    }

    public void setLatitude(float latitude) {
        this.latitude = latitude;
    }

    public String getDeclaration_abbreviation() {
        return declaration_abbreviation;
    }

    public void setDeclaration_abbreviation(String declaration_abbreviation) {
        this.declaration_abbreviation = declaration_abbreviation;
    }
}
