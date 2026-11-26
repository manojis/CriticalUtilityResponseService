package edu.gatech.erms.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.stereotype.Repository;

import java.util.Date;

/**
 * @author Manoj.Mohanan Nair
 * @Date 7/21/18
 */
@Repository
public class SearchResource {

    public int resource_id;
    public String keyword;
    public int esf;
    public Float distance;
    public String incident_id;


    public String resource_name;
    public String username;
    public int cost;
    public String unit;
    @JsonFormat(pattern="yyyy-MM-dd")
    public Date next_available;
    public boolean is_available;

    public String getKeyword() {
        return keyword;
    }

    public int getResource_id() {
        return resource_id;
    }

    public void setResource_id(int resource_id) {
        this.resource_id = resource_id;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public int getEsf() {
        return esf;
    }

    public void setEsf(int esf) {
        this.esf = esf;
    }

    public Float getDistance() {
        return distance;
    }

    public void setDistance(Float distance) {
        this.distance = distance;
    }

    public String getIncident_id() {
        return incident_id;
    }

    public void setIncident_id(String incident_id) {
        this.incident_id = incident_id;
    }

    public String getResource_name() {
        return resource_name;
    }

    public void setResource_name(String resource_name) {
        this.resource_name = resource_name;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getCost() {
        return cost;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Date getNext_available() {
        return next_available;
    }

    public void setNext_available(Date next_available) {
        this.next_available = next_available;
    }

    public boolean isIs_available() {
        return is_available;
    }

    public void setIs_available(boolean is_available) {
        this.is_available = is_available;
    }

}
