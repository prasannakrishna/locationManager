package com.bhagwat.scm.locationManager.service;

import com.bhagwat.scm.locationManager.entity.ClusterCentroid;
import com.bhagwat.scm.locationManager.entity.ClusterDistance;
import com.bhagwat.scm.locationManager.repository.ClusterCentroidRepository;
import com.bhagwat.scm.locationManager.repository.ClusterDistanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Precomputes and queries distances between cluster centroids.
 *
 * Haversine formula gives straight-line distance.
 * Road distance estimated as straight-line × 1.3 (India road factor).
 * Transit hours = road distance / 50 km/h average speed.
 *
 * APIs:
 *   - Compute distance between two clusters
 *   - Find nearest clusters within radius
 *   - Precompute all-pairs distances (batch job)
 *   - Heatmap: for a given cluster, show distance tiers
 */
@Service
public class DistanceHeatmapService {

    private static final Logger log = LoggerFactory.getLogger(DistanceHeatmapService.class);

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double ROAD_FACTOR = 1.3; // straight-line to road distance multiplier
    private static final double AVG_SPEED_KMH = 50.0; // average road speed in India

    private final ClusterCentroidRepository centroidRepo;
    private final ClusterDistanceRepository distanceRepo;

    public DistanceHeatmapService(ClusterCentroidRepository centroidRepo,
                                   ClusterDistanceRepository distanceRepo) {
        this.centroidRepo = centroidRepo;
        this.distanceRepo = distanceRepo;
    }

    // ══════════════════════════════════════════════════════════════════════
    // HAVERSINE FORMULA
    // ══════════════════════════════════════════════════════════════════════

    public static double haversine(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    // ══════════════════════════════════════════════════════════════════════
    // SINGLE PAIR DISTANCE
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Get distance between two clusters (from precomputed or compute on-the-fly).
     */
    public Map<String, Object> getDistance(String cluster1, String cluster2) {
        if (cluster1.equals(cluster2)) {
            return Map.of("fromCluster", cluster1, "toCluster", cluster2,
                    "distanceKm", 0.0, "estimatedRoadKm", 0.0,
                    "estimatedTransitHours", 0.0, "proximityLevel", "SAME_CLUSTER");
        }

        // Normalize order
        String from = cluster1.compareTo(cluster2) < 0 ? cluster1 : cluster2;
        String to = cluster1.compareTo(cluster2) < 0 ? cluster2 : cluster1;

        // Try precomputed
        Optional<ClusterDistance> cached = distanceRepo.findByFromClusterAndToCluster(from, to);
        if (cached.isPresent()) {
            ClusterDistance d = cached.get();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fromCluster", d.getFromCluster());
            result.put("toCluster", d.getToCluster());
            result.put("fromCity", d.getFromCity());
            result.put("toCity", d.getToCity());
            result.put("distanceKm", Math.round(d.getDistanceKm() * 10) / 10.0);
            result.put("estimatedRoadKm", Math.round(d.getEstimatedRoadKm() * 10) / 10.0);
            result.put("estimatedTransitHours", Math.round(d.getEstimatedTransitHours() * 10) / 10.0);
            result.put("proximityLevel", d.getProximityLevel());
            result.put("source", "precomputed");
            return result;
        }

        // Compute on-the-fly
        Optional<ClusterCentroid> c1 = centroidRepo.findByClusterName(from);
        Optional<ClusterCentroid> c2 = centroidRepo.findByClusterName(to);

        if (c1.isEmpty() || c2.isEmpty()) {
            return Map.of("error", "Centroid not found for cluster " + (c1.isEmpty() ? from : to));
        }

        double distKm = haversine(c1.get().getLatitude(), c1.get().getLongitude(),
                c2.get().getLatitude(), c2.get().getLongitude());
        double roadKm = distKm * ROAD_FACTOR;
        double hours = roadKm / AVG_SPEED_KMH;
        String proximity = resolveProximity(c1.get(), c2.get());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fromCluster", from);
        result.put("toCluster", to);
        result.put("fromCity", c1.get().getCity());
        result.put("toCity", c2.get().getCity());
        result.put("distanceKm", Math.round(distKm * 10) / 10.0);
        result.put("estimatedRoadKm", Math.round(roadKm * 10) / 10.0);
        result.put("estimatedTransitHours", Math.round(hours * 10) / 10.0);
        result.put("proximityLevel", proximity);
        result.put("source", "computed");
        return result;
    }

    // ══════════════════════════════════════════════════════════════════════
    // NEAREST CLUSTERS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Find clusters within a given radius from a cluster.
     */
    public List<Map<String, Object>> findClustersWithinRadius(String clusterName, double radiusKm) {
        List<ClusterDistance> distances = distanceRepo.findWithinDistance(clusterName, radiusKm);

        return distances.stream().map(d -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            String other = d.getFromCluster().equals(clusterName) ? d.getToCluster() : d.getFromCluster();
            String otherCity = d.getFromCluster().equals(clusterName) ? d.getToCity() : d.getFromCity();
            String otherState = d.getFromCluster().equals(clusterName) ? d.getToState() : d.getFromState();
            entry.put("cluster", other);
            entry.put("city", otherCity);
            entry.put("state", otherState);
            entry.put("distanceKm", Math.round(d.getDistanceKm() * 10) / 10.0);
            entry.put("estimatedTransitHours", Math.round(d.getEstimatedTransitHours() * 10) / 10.0);
            return entry;
        }).sorted(Comparator.comparingDouble(m -> (double) m.get("distanceKm")))
                .collect(Collectors.toList());
    }

    /**
     * Find N nearest clusters to a given cluster.
     */
    public List<Map<String, Object>> findNearestClusters(String clusterName, int limit) {
        List<ClusterDistance> distances = distanceRepo.findNearestClusters(clusterName);

        return distances.stream()
                .limit(limit)
                .map(d -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    String other = d.getFromCluster().equals(clusterName) ? d.getToCluster() : d.getFromCluster();
                    String otherCity = d.getFromCluster().equals(clusterName) ? d.getToCity() : d.getFromCity();
                    entry.put("cluster", other);
                    entry.put("city", otherCity);
                    entry.put("distanceKm", Math.round(d.getDistanceKm() * 10) / 10.0);
                    entry.put("estimatedTransitHours", Math.round(d.getEstimatedTransitHours() * 10) / 10.0);
                    return entry;
                })
                .collect(Collectors.toList());
    }

    // ══════════════════════════════════════════════════════════════════════
    // HEATMAP — distance tiers from a cluster
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Generate a distance heatmap from a cluster.
     * Returns clusters grouped by distance tier.
     */
    public Map<String, Object> getHeatmap(String clusterName) {
        List<ClusterDistance> all = distanceRepo.findNearestClusters(clusterName);

        // Group by distance tiers
        List<Map<String, Object>> within50 = new ArrayList<>();
        List<Map<String, Object>> within100 = new ArrayList<>();
        List<Map<String, Object>> within200 = new ArrayList<>();
        List<Map<String, Object>> within500 = new ArrayList<>();
        List<Map<String, Object>> beyond500 = new ArrayList<>();

        for (ClusterDistance d : all) {
            String other = d.getFromCluster().equals(clusterName) ? d.getToCluster() : d.getFromCluster();
            String otherCity = d.getFromCluster().equals(clusterName) ? d.getToCity() : d.getFromCity();
            String otherState = d.getFromCluster().equals(clusterName) ? d.getToState() : d.getFromState();
            double km = d.getDistanceKm();

            Map<String, Object> entry = Map.of(
                    "cluster", other, "city", otherCity != null ? otherCity : "",
                    "state", otherState != null ? otherState : "",
                    "distanceKm", Math.round(km * 10) / 10.0);

            if (km <= 50) within50.add(entry);
            else if (km <= 100) within100.add(entry);
            else if (km <= 200) within200.add(entry);
            else if (km <= 500) within500.add(entry);
            else beyond500.add(entry);
        }

        Map<String, Object> heatmap = new LinkedHashMap<>();
        heatmap.put("origin", clusterName);
        heatmap.put("tiers", Map.of(
                "within50km", Map.of("count", within50.size(), "clusters", within50),
                "within100km", Map.of("count", within100.size(), "clusters", within100),
                "within200km", Map.of("count", within200.size(), "clusters", within200),
                "within500km", Map.of("count", within500.size(), "clusters", within500),
                "beyond500km", Map.of("count", beyond500.size(), "clusters", beyond500)
        ));
        heatmap.put("totalClusters", all.size());
        return heatmap;
    }

    // ══════════════════════════════════════════════════════════════════════
    // BATCH PRECOMPUTE
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Precompute all-pairs distances between clusters that have centroids.
     * Only computes pairs where fromCluster < toCluster (avoid duplicates).
     * ~1000 clusters → ~500K pairs.
     */
    public Map<String, Object> precomputeAllDistances() {
        List<ClusterCentroid> centroids = centroidRepo.findByLatitudeIsNotNull();
        log.info("Precomputing distances for {} centroids", centroids.size());

        int computed = 0;
        int skipped = 0;
        List<ClusterDistance> batch = new ArrayList<>();

        for (int i = 0; i < centroids.size(); i++) {
            for (int j = i + 1; j < centroids.size(); j++) {
                ClusterCentroid c1 = centroids.get(i);
                ClusterCentroid c2 = centroids.get(j);

                String from = c1.getClusterName().compareTo(c2.getClusterName()) < 0
                        ? c1.getClusterName() : c2.getClusterName();
                String to = c1.getClusterName().compareTo(c2.getClusterName()) < 0
                        ? c2.getClusterName() : c1.getClusterName();

                ClusterCentroid fromC = from.equals(c1.getClusterName()) ? c1 : c2;
                ClusterCentroid toC = from.equals(c1.getClusterName()) ? c2 : c1;

                double distKm = haversine(fromC.getLatitude(), fromC.getLongitude(),
                        toC.getLatitude(), toC.getLongitude());
                double roadKm = distKm * ROAD_FACTOR;
                double hours = roadKm / AVG_SPEED_KMH;

                ClusterDistance cd = new ClusterDistance();
                cd.setFromCluster(from);
                cd.setToCluster(to);
                cd.setFromState(fromC.getState());
                cd.setToState(toC.getState());
                cd.setFromZone(fromC.getZone());
                cd.setToZone(toC.getZone());
                cd.setFromCity(fromC.getCity());
                cd.setToCity(toC.getCity());
                cd.setDistanceKm(distKm);
                cd.setEstimatedRoadKm(roadKm);
                cd.setEstimatedTransitHours(hours);
                cd.setProximityLevel(resolveProximity(fromC, toC));

                batch.add(cd);
                computed++;

                // Batch save every 5000
                if (batch.size() >= 5000) {
                    distanceRepo.saveAll(batch);
                    log.info("Saved {} distances (total computed: {})", batch.size(), computed);
                    batch.clear();
                }
            }
        }

        // Save remaining
        if (!batch.isEmpty()) {
            distanceRepo.saveAll(batch);
        }

        log.info("Precomputation complete: {} distances computed", computed);
        return Map.of("totalCentroids", centroids.size(), "distancesComputed", computed);
    }

    /**
     * Register a cluster centroid (for seeding data).
     */
    public ClusterCentroid registerCentroid(String clusterName, String state, String zone,
                                             String city, double latitude, double longitude,
                                             int pincodeCount) {
        ClusterCentroid centroid = centroidRepo.findByClusterName(clusterName)
                .orElse(new ClusterCentroid());
        centroid.setClusterName(clusterName);
        centroid.setState(state);
        centroid.setZone(zone);
        centroid.setCity(city);
        centroid.setLatitude(latitude);
        centroid.setLongitude(longitude);
        centroid.setPincodeCount(pincodeCount);
        return centroidRepo.save(centroid);
    }

    /**
     * Get all registered centroids.
     */
    public List<ClusterCentroid> getAllCentroids() {
        return centroidRepo.findAll();
    }

    /**
     * Get stats about precomputed data.
     */
    public Map<String, Object> getStats() {
        long centroidCount = centroidRepo.count();
        long distanceCount = distanceRepo.count();
        long maxPairs = centroidCount * (centroidCount - 1) / 2;
        return Map.of(
                "totalCentroids", centroidCount,
                "centroidsWithCoordinates", centroidRepo.findByLatitudeIsNotNull().size(),
                "precomputedDistances", distanceCount,
                "maxPossiblePairs", maxPairs,
                "coveragePercent", maxPairs > 0 ? Math.round((double) distanceCount / maxPairs * 100) : 0
        );
    }

    // ── Internal ─────────────────────────────────────────────────────────

    private String resolveProximity(ClusterCentroid c1, ClusterCentroid c2) {
        if (c1.getClusterName().equals(c2.getClusterName())) return "SAME_CLUSTER";
        if (c1.getZone() != null && c1.getZone().equalsIgnoreCase(c2.getZone())) return "SAME_ZONE";
        if (c1.getState() != null && c1.getState().equalsIgnoreCase(c2.getState())) return "SAME_STATE";
        return "DIFFERENT_STATE";
    }
}
