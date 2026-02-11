package com.bhagwat.scm.locationManager.dto;
import java.util.Map;

public class ClusterResponse {
    private Integer clusterId;
    private String state;
    private String clusterName;
    private Map<String, String> pincodes;
    private String zone;

    // Getters and Setters
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
