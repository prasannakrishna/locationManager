package com.bhagwat.scm.locationManager.controller;

@RestController
@RequestMapping("/community-clusters")
public class CommunityClusterController {
    private final CommunityClusterService communityClusterService;

    public CommunityClusterController(CommunityClusterService communityClusterService) {
        this.communityClusterService = communityClusterService;
    }

    @PostMapping("/add-customer")
    public CommunityClusterMapping addCustomer(@RequestBody CustomerCommunityRequest request) {
        return communityClusterService.addCustomerToCommunityCluster(request);
    }
}
