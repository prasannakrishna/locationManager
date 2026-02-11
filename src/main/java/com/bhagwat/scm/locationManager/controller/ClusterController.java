package com.bhagwat.scm.locationManager.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/clusters")
public class ClusterController {
    private final ClusterService clusterService;

    public ClusterController(ClusterService clusterService) {
        this.clusterService = clusterService;
    }

    @PostMapping("/by-pincodes")
    public List<ClusterResponse> getClustersByPincodes(@RequestBody PincodeRequest request) {
        return clusterService.getClustersByPincodes(request.getPincodes());
    }
}
