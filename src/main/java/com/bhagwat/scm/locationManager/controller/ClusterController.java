package com.bhagwat.scm.locationManager.controller;

import com.bhagwat.scm.locationManager.dto.*;
import com.bhagwat.scm.locationManager.service.ClusterService;
import com.bhagwat.scm.locationManager.service.DataCleanupService;
import com.bhagwat.scm.locationManager.service.PincodeClusteringService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ClusterController {

    private final ClusterService clusterService;
    private final DataCleanupService dataCleanupService;
    private final PincodeClusteringService pincodeClusteringService;

    /**
     * Lookup cluster info for a batch of pincodes.
     * POST /clusters/by-pincodes
     * Body: { "pincodes": ["560001", "400001"] }
     */
    @PostMapping("/clusters/by-pincodes")
    public List<ClusterResponse> getClustersByPincodes(@RequestBody PincodeRequest request) {
        return clusterService.getClustersByPincodes(request.getPincodes());
    }

    /**
     * Single pincode lookup — returns cluster, zone, state, place name.
     * GET /clusters/pincode/560001
     */
    @GetMapping("/clusters/pincode/{pincode}")
    public ResponseEntity<PincodeClusterResult> lookupPincode(@PathVariable String pincode) {
        return clusterService.lookupPincode(pincode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * All clusters in a state (case-insensitive).
     * GET /clusters/state/Karnataka
     */
    @GetMapping("/clusters/state/{state}")
    public List<ClusterResponse> getByState(@PathVariable String state) {
        return clusterService.getClustersByState(state);
    }

    /**
     * All clusters in a zone (case-insensitive).
     * GET /clusters/zone/Bangalore Rural
     */
    @GetMapping("/clusters/zone/{zone}")
    public List<ClusterResponse> getByZone(@PathVariable String zone) {
        return clusterService.getClustersByZone(zone);
    }

    /**
     * All distinct states in the dataset.
     * GET /clusters/states
     */
    @GetMapping("/clusters/states")
    public List<String> getAllStates() {
        return clusterService.getAllStates();
    }

    /**
     * All distinct zones in the dataset.
     * GET /clusters/zones
     */
    @GetMapping("/clusters/zones")
    public List<String> getAllZones() {
        return clusterService.getAllZones();
    }

    /**
     * Cluster demand pincodes by geography and rank supply pincodes closest → farthest
     * for each demand group.
     *
     * POST /clusters/group
     * Body:
     * {
     *   "demandPincodes": ["560001", "562109", "700001"],
     *   "supplyPincodes": ["560100", "562108", "400001", "600001"]
     * }
     *
     * Response: groups keyed by demand clusterName, each with supply pincodes
     * ranked SAME_CLUSTER → SAME_ZONE → SAME_STATE → DIFFERENT_STATE.
     */
    @PostMapping("/clusters/group")
    public PincodeClusteringResponse groupByCluster(@RequestBody PincodeClusteringRequest request) {
        return pincodeClusteringService.cluster(request);
    }

    /**
     * One-time data cleanup: strips \n chars from state, zone, and place names in MongoDB.
     * POST /admin/cleanup
     */
    @PostMapping("/admin/cleanup")
    public DataCleanupService.CleanupResult runCleanup() {
        return dataCleanupService.cleanupClusters();
    }
}
