package edu.gatech.erms.model;

import org.springframework.stereotype.Repository;

/**
 * Created by ale on 7/22/18.
 */
@Repository
public class Report {

    private int primary_esf;
    private String esf_description;
    private int total_resources;
    private int resources_in_use;


    public int getPrimary_esf() {
        return primary_esf;
    }

    public void setPrimary_esf(int primary_esf) {
        this.primary_esf = primary_esf;
    }

    public String getEsf_description() {
        return esf_description;
    }

    public void setEsf_description(String esf_description) {
        this.esf_description = esf_description;
    }

    public int getTotal_resources() {
        return total_resources;
    }

    public void setTotal_resources(int total_resources) {
        this.total_resources = total_resources;
    }

    public int getResources_in_use() {
        return resources_in_use;
    }

    public void setResources_in_use(int resources_in_use) {
        this.resources_in_use = resources_in_use;
    }

}
