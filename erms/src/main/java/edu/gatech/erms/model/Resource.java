package edu.gatech.erms.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.List;

/**
 * @author Manoj.Mohanan Nair
 * @Date 7/19/18
 */
@Repository
public class Resource {

    public  int resource_id;
    public String username;
    public String resource_name;
    
    @JsonFormat(pattern="yyyy-MM-dd")
    public  Date next_available;

    public  boolean is_available;
    public  String model;
    public  int cost;
    public  String unit;
    public  float longitude;
    public  float latitude;
    public  float max_distance;
    public  int primary_esf;
    //Additional esf is represented by esf_number
    public  List<Integer> additional_esf;
    //Resource capability list is represented by capability
    public  List<String> capabilities;

    public  int getResource_id() {
        return resource_id;
    }

    public  void setResource_id(int resource_id) {
        this.resource_id = resource_id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getResource_name() {
        return resource_name;
    }

    public void setResource_name(String resource_name) {
        this.resource_name = resource_name;
    }

    public  Date getNext_available() {
        return next_available;
    }

    public  void setNext_available(Date next_available) {
        this.next_available = next_available;
    }

    public  boolean isIs_available() {
        return is_available;
    }

    public  void setIs_available(boolean is_available) {
        this.is_available = is_available;
    }

    public  String getModel() {
        return model;
    }

    public  void setModel(String model) {
        this.model = model;
    }

    public  int getCost() {
        return cost;
    }

    public  void setCost(int cost) {
        this.cost = cost;
    }

    public  String getUnit() {
        return unit;
    }

    public  void setUnit(String unit) {
        this.unit = unit;
    }

    public  float getLongitude() {
        return longitude;
    }

    public  void setLongitude(float longitude) {
        this.longitude = longitude;
    }

    public  float getLatitude() {
        return latitude;
    }

    public  void setLatitude(float latitude) {
        this.latitude = latitude;
    }

    public  float getMax_distance() {
        return max_distance;
    }

    public  void setMax_distance(float max_distance) {
        this.max_distance = max_distance;
    }

    public  int getPrimary_esf() {
        return primary_esf;
    }

    public  void setPrimary_esf(int primary_esf) {
        this.primary_esf = primary_esf;
    }
    public  List<Integer> getAdditional_esf() {
        return additional_esf;
    }

    public  void setAdditional_esf(List<Integer> additional_esf) {
        this.additional_esf = additional_esf;
    }

    public  List<String> getCapabilities() {
        return capabilities;
    }

    public  void setCapabilities(List<String> capabilities) {
        this.capabilities = capabilities;
    }

}