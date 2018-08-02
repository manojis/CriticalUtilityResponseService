package edu.gatech.erms.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.stereotype.Repository;

import java.sql.Date;

/**
 * @author Manoj.Mohanan Nair
 * @Date 7/22/18
 */
@Repository
public class ResourceAction {

    public int resource_id;

    public String incident_id;

    public String description;
    //resource_name corresponds to username of the resource
    public String resource_name;
    public String username;

    public String requestedBy_username;

    //next_available date is the estimated return date
    @JsonFormat(pattern="yyyy-MM-dd")
    public Date next_available;

    public int is_available;

    //whether the requested action is for requesting a resource or deploying a resource
    public String action;

    //whether accepting/Rejecting the request/deploy
    public String actionStatus;

    @JsonFormat(pattern="yyyy-MM-dd")
    public Date request_date;

    @JsonFormat(pattern="yyyy-MM-dd")
    public Date deploy_date;

    @JsonFormat(pattern="yyyy-MM-dd")
    public Date estimated_return_date;

    @JsonFormat(pattern="yyyy-MM-dd")
    public Date actual_return_date;


    public int getResource_id() {
        return resource_id;
    }

    public void setResource_id(int resource_id) {
        this.resource_id = resource_id;
    }

    public String getIncident_id() {
        return incident_id;
    }

    public void setIncident_id(String incident_id) {
        this.incident_id = incident_id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public String getRequestedBy_username() {
        return requestedBy_username;
    }

    public void setRequestedBy_username(String requestedBy_username) {
        this.requestedBy_username = requestedBy_username;
    }

    public Date getNext_available() {
        return next_available;
    }

    public void setNext_available(Date next_available) {
        this.next_available = next_available;
    }

    public int getIs_available() {
        return is_available;
    }

    public void setIs_available(int is_available) {
        this.is_available = is_available;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getActionStatus() {
        return actionStatus;
    }

    public void setActionStatus(String actionStatus) {
        this.actionStatus = actionStatus;
    }

    public Date getRequest_date() {
        return request_date;
    }

    public void setRequest_date(Date request_date) {
        this.request_date = request_date;
    }

    public Date getDeploy_date() {
        return deploy_date;
    }

    public void setDeploy_date(Date deploy_date) {
        this.deploy_date = deploy_date;
    }

    public Date getEstimated_return_date() {
        return estimated_return_date;
    }

    public void setEstimated_return_date(Date estimated_return_date) {
        this.estimated_return_date = estimated_return_date;
    }

    public Date getActual_return_date() {
        return actual_return_date;
    }

    public void setActual_return_date(Date actual_return_date) {
        this.actual_return_date = actual_return_date;
    }
}
