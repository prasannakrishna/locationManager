package com.bhagwat.scm.locationManager.controller;

import com.bhagwat.scm.locationManager.dto.AllocationRequest;
import com.bhagwat.scm.locationManager.dto.AllocationResponse;
import com.bhagwat.scm.locationManager.dto.AllocationResponse.AllocationPlanSummary;
import com.bhagwat.scm.locationManager.service.AllocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/order-allocations")
@RequiredArgsConstructor
public class OrderAllocationController {

    private final AllocationService allocationService;

    /**
     * Submit a community order for cluster-based allocation.
     * Idempotent — same communityOrderId returns the existing plan.
     *
     * POST /order-allocations
     * Body: {
     *   "communityId": "COMM-1",
     *   "communityOrderId": "ORD-2024-001",
     *   "products": [
     *     {
     *       "productId": "PROD-A",
     *       "sources": [
     *         { "sourceId": "SELLER-1", "sourcePincode": "560001", "sourceType": "SELLER" },
     *         { "sourceId": "WH-1",     "sourcePincode": "562108", "sourceType": "WAREHOUSE" }
     *       ]
     *     }
     *   ],
     *   "customers": [
     *     { "customerId": "C1", "deliveryPincode": "560050" },
     *     { "customerId": "C2", "deliveryPincode": "700001" }
     *   ]
     * }
     */
    @PostMapping
    public ResponseEntity<AllocationResponse> allocate(@RequestBody AllocationRequest request) {
        return ResponseEntity.ok(allocationService.allocate(request));
    }

    /**
     * Fetch a previously computed allocation plan by community order ID.
     * GET /order-allocations/{communityOrderId}
     */
    @GetMapping("/{communityOrderId}")
    public ResponseEntity<AllocationResponse> getAllocation(@PathVariable String communityOrderId) {
        return allocationService.getAllocationByOrderId(communityOrderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * List all allocation plans for a community (lightweight summary).
     * GET /order-allocations/community/{communityId}
     */
    @GetMapping("/community/{communityId}")
    public List<AllocationPlanSummary> getCommunityAllocations(@PathVariable String communityId) {
        return allocationService.getAllocationsByCommunity(communityId);
    }
}
