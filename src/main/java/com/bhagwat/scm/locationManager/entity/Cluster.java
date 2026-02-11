package com.bhagwat.scm.locationManager.entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

@Document(collection = "clusters")
public class Cluster {

    @Id
    private String id; // MongoDB _id

    private Integer clusterId;     // clusterId field
    private String state;          // state name
    private String clusterName;    // first 3 digits of pincode
    private Map<String, String> pincodes; // key: full pincode, value: place name
    private String zone;           // zone name

    // Constructors
    public Cluster() {}

    public Cluster(Integer clusterId, String state, String clusterName, Map<String, String> pincodes, String zone) {
        this.clusterId = clusterId;
        this.state = state;
        this.clusterName = clusterName;
        this.pincodes = pincodes;
        this.zone = zone;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public Integer getClusterId() {
        return clusterId;
    }

    public void setClusterId(Integer clusterId) {
        this.clusterId = clusterId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public Map<String, String> getPincodes() {
        return pincodes;
    }

    public void setPincodes(Map<String, String> pincodes) {
        this.pincodes = pincodes;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }
}