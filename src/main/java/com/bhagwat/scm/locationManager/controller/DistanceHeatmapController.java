package com.bhagwat.scm.locationManager.controller;

import com.bhagwat.scm.locationManager.entity.ClusterCentroid;
import com.bhagwat.scm.locationManager.service.DistanceHeatmapService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Distance heatmap and cluster proximity APIs.
 *
 * Endpoints:
 *   GET  /distance?from=560&to=600              → distance between two clusters
 *   GET  /distance/within?cluster=560&radius=200 → clusters within 200km
 *   GET  /distance/nearest?cluster=560&limit=10  → 10 nearest clusters
 *   GET  /distance/heatmap?cluster=560           → distance tiers heatmap
 *   POST /distance/centroids                     → register centroid
 *   POST /distance/precompute                    → batch precompute all pairs
 *   GET  /distance/stats                         → coverage statistics
 */
@RestController
@RequestMapping("/distance")
public class DistanceHeatmapController {

    private final DistanceHeatmapService heatmapService;

    public DistanceHeatmapController(DistanceHeatmapService heatmapService) {
        this.heatmapService = heatmapService;
    }

    /**
     * Get distance between two clusters.
     * Uses precomputed if available, otherwise computes on-the-fly.
     *
     * GET /distance?from=560&to=600
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getDistance(
            @RequestParam String from, @RequestParam String to) {
        return ResponseEntity.ok(heatmapService.getDistance(from, to));
    }

    /**
     * Find all clusters within a radius (km) from a cluster.
     *
     * GET /distance/within?cluster=560&radius=200
     */
    @GetMapping("/within")
    public ResponseEntity<Map<String, Object>> findWithinRadius(
            @RequestParam String cluster,
            @RequestParam(defaultValue = "100") double radius) {
        List<Map<String, Object>> results = heatmapService.findClustersWithinRadius(cluster, radius);
        return ResponseEntity.ok(Map.of(
                "origin", cluster,
                "radiusKm", radius,
                "count", results.size(),
                "clusters", results
        ));
    }

    /**
     * Find N nearest clusters.
     *
     * GET /distance/nearest?cluster=560&limit=10
     */
    @GetMapping("/nearest")
    public ResponseEntity<Map<String, Object>> findNearest(
            @RequestParam String cluster,
            @RequestParam(defaultValue = "10") int limit) {
        List<Map<String, Object>> results = heatmapService.findNearestClusters(cluster, limit);
        return ResponseEntity.ok(Map.of(
                "origin", cluster,
                "limit", limit,
                "clusters", results
        ));
    }

    /**
     * Distance heatmap from a cluster — grouped by distance tiers.
     *
     * GET /distance/heatmap?cluster=560
     */
    @GetMapping("/heatmap")
    public ResponseEntity<Map<String, Object>> getHeatmap(@RequestParam String cluster) {
        return ResponseEntity.ok(heatmapService.getHeatmap(cluster));
    }

    /**
     * Register a cluster centroid with lat/lng.
     *
     * POST /distance/centroids
     * { "clusterName": "560", "state": "Karnataka", "zone": "Bangalore Urban",
     *   "city": "Bangalore", "latitude": 12.9716, "longitude": 77.5946, "pincodeCount": 100 }
     */
    @PostMapping("/centroids")
    public ResponseEntity<ClusterCentroid> registerCentroid(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(heatmapService.registerCentroid(
                (String) body.get("clusterName"),
                (String) body.get("state"),
                (String) body.get("zone"),
                (String) body.get("city"),
                ((Number) body.get("latitude")).doubleValue(),
                ((Number) body.get("longitude")).doubleValue(),
                body.get("pincodeCount") != null ? ((Number) body.get("pincodeCount")).intValue() : 0
        ));
    }

    /**
     * Batch register multiple centroids.
     *
     * POST /distance/centroids/batch
     * [ { "clusterName": "560", "city": "Bangalore", "latitude": 12.97, "longitude": 77.59, ... }, ... ]
     */
    @PostMapping("/centroids/batch")
    public ResponseEntity<Map<String, Object>> registerCentroidsBatch(@RequestBody List<Map<String, Object>> centroids) {
        int saved = 0;
        for (Map<String, Object> body : centroids) {
            try {
                heatmapService.registerCentroid(
                        (String) body.get("clusterName"),
                        (String) body.getOrDefault("state", ""),
                        (String) body.getOrDefault("zone", ""),
                        (String) body.getOrDefault("city", ""),
                        ((Number) body.get("latitude")).doubleValue(),
                        ((Number) body.get("longitude")).doubleValue(),
                        body.get("pincodeCount") != null ? ((Number) body.get("pincodeCount")).intValue() : 0
                );
                saved++;
            } catch (Exception e) {
                // skip invalid entries
            }
        }
        return ResponseEntity.ok(Map.of("saved", saved, "total", centroids.size()));
    }

    /**
     * Get all registered centroids.
     *
     * GET /distance/centroids
     */
    @GetMapping("/centroids")
    public ResponseEntity<List<ClusterCentroid>> getAllCentroids() {
        return ResponseEntity.ok(heatmapService.getAllCentroids());
    }

    /**
     * Precompute all-pairs distances between registered centroids.
     * ~1000 centroids → ~500K pairs. Takes a few seconds.
     *
     * POST /distance/precompute
     */
    @PostMapping("/precompute")
    public ResponseEntity<Map<String, Object>> precompute() {
        return ResponseEntity.ok(heatmapService.precomputeAllDistances());
    }

    /**
     * Get statistics about precomputed data coverage.
     *
     * GET /distance/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(heatmapService.getStats());
    }
}
