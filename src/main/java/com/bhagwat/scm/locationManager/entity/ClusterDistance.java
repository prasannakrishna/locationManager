package com.bhagwat.scm.locationManager.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Precomputed distance between two cluster centroids.
 *
 * 1051 clusters → ~550K pairs (only store one direction: fromCluster < toCluster).
 * Lookup: find distance between 560 and 600 → query fromCluster=560, toCluster=600
 *         OR fromCluster=600, toCluster=560 (service handles swap)
 *
 * Used for:
 *   - Delivery time estimation
 *   - Logistics cost calculation
 *   - Nearest supply cluster lookup
 *   - Heatmap visualization
 */
@Document(collection = "cluster_distances")
@CompoundIndexes({
        @CompoundIndex(name = "idx_from_to", def = "{'fromCluster': 1, 'toCluster': 1}", unique = true),
        @CompoundIndex(name = "idx_from_dist", def = "{'fromCluster': 1, 'distanceKm': 1}"),
        @CompoundIndex(name = "idx_to_dist", def = "{'toCluster': 1, 'distanceKm': 1}")
})
public class ClusterDistance {

    @Id
    private String id;

    private String fromCluster; // 3-digit prefix (always alphabetically smaller)
    private String toCluster;   // 3-digit prefix (always alphabetically larger)

    private String fromState;
    private String toState;
    private String fromZone;
    private String toZone;
    private String fromCity;
    private String toCity;

    /** Straight-line (Haversine) distance in km */
    private Double distanceKm;

    /** Estimated road distance (straight-line × 1.3 factor) */
    private Double estimatedRoadKm;

    /** Estimated transit hours (road distance / avg speed) */
    private Double estimatedTransitHours;

    /** Geographic relationship */
    private String proximityLevel; // SAME_CLUSTER, SAME_ZONE, SAME_STATE, DIFFERENT_STATE

    public ClusterDistance() {}

    // Getters and setters
    public String getId() { return id; }
    public String getFromCluster() { return fromCluster; }
    public void setFromCluster(String fromCluster) { this.fromCluster = fromCluster; }
    public String getToCluster() { return toCluster; }
    public void setToCluster(String toCluster) { this.toCluster = toCluster; }
    public String getFromState() { return fromState; }
    public void setFromState(String fromState) { this.fromState = fromState; }
    public String getToState() { return toState; }
    public void setToState(String toState) { this.toState = toState; }
    public String getFromZone() { return fromZone; }
    public void setFromZone(String fromZone) { this.fromZone = fromZone; }
    public String getToZone() { return toZone; }
    public void setToZone(String toZone) { this.toZone = toZone; }
    public String getFromCity() { return fromCity; }
    public void setFromCity(String fromCity) { this.fromCity = fromCity; }
    public String getToCity() { return toCity; }
    public void setToCity(String toCity) { this.toCity = toCity; }
    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }
    public Double getEstimatedRoadKm() { return estimatedRoadKm; }
    public void setEstimatedRoadKm(Double estimatedRoadKm) { this.estimatedRoadKm = estimatedRoadKm; }
    public Double getEstimatedTransitHours() { return estimatedTransitHours; }
    public void setEstimatedTransitHours(Double estimatedTransitHours) { this.estimatedTransitHours = estimatedTransitHours; }
    public String getProximityLevel() { return proximityLevel; }
    public void setProximityLevel(String proximityLevel) { this.proximityLevel = proximityLevel; }
}
