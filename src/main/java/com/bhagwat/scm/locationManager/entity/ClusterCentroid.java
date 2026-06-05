package com.bhagwat.scm.locationManager.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Centroid coordinates for each 3-digit pincode cluster.
 * Used for distance computation between clusters.
 *
 * Population: Can be seeded from known city coordinates or
 * computed from a pincode-lat/lng dataset.
 *
 * Example:
 *   clusterName: "560", city: "Bangalore", lat: 12.9716, lng: 77.5946
 *   clusterName: "600", city: "Chennai",   lat: 13.0827, lng: 80.2707
 */
@Document(collection = "cluster_centroids")
@CompoundIndex(name = "idx_cluster_name", def = "{'clusterName': 1}", unique = true)
public class ClusterCentroid {

    @Id
    private String id;

    @Indexed(unique = true)
    private String clusterName; // 3-digit prefix

    private String state;
    private String zone;
    private String city; // representative city

    private Double latitude;
    private Double longitude;

    /** Number of pincodes in this cluster */
    private Integer pincodeCount;

    public ClusterCentroid() {}

    public ClusterCentroid(String clusterName, String state, String zone, String city,
                            Double latitude, Double longitude, Integer pincodeCount) {
        this.clusterName = clusterName;
        this.state = state;
        this.zone = zone;
        this.city = city;
        this.latitude = latitude;
        this.longitude = longitude;
        this.pincodeCount = pincodeCount;
    }

    // Getters and setters
    public String getId() { return id; }
    public String getClusterName() { return clusterName; }
    public void setClusterName(String clusterName) { this.clusterName = clusterName; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Integer getPincodeCount() { return pincodeCount; }
    public void setPincodeCount(Integer pincodeCount) { this.pincodeCount = pincodeCount; }
}
