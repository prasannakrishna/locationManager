package com.bhagwat.scm.locationManager.controller;

import com.bhagwat.scm.locationManager.dto.CustomerCommunityRequest;
import com.bhagwat.scm.locationManager.entity.Cluster;
import com.bhagwat.scm.locationManager.entity.CommunityClusterMapping;
import com.bhagwat.scm.locationManager.service.CommunityClusterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/community-clusters")
@RequiredArgsConstructor
public class CommunityClusterController {

    private final CommunityClusterService communityClusterService;

    /**
     * Add a customer to their community's cluster mapping based on pincode.
     * POST /community-clusters/add-customer
     * Body: { "communityId": "...", "customerId": "...", "pincode": "560001" }
     */
    @PostMapping("/add-customer")
    public CommunityClusterMapping addCustomer(@RequestBody CustomerCommunityRequest request) {
        return communityClusterService.addCustomerToCommunityCluster(request);
    }

    /**
     * Get all cluster mappings a community spans.
     * GET /community-clusters/{communityId}/mappings
     */
    @GetMapping("/{communityId}/mappings")
    public List<CommunityClusterMapping> getMappings(@PathVariable String communityId) {
        return communityClusterService.getMappingsForCommunity(communityId);
    }

    /**
     * Get full cluster details for every cluster a community spans.
     * GET /community-clusters/{communityId}/clusters
     */
    @GetMapping("/{communityId}/clusters")
    public List<Cluster> getClusters(@PathVariable String communityId) {
        return communityClusterService.getClustersForCommunity(communityId);
    }
}
