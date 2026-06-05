package com.bhagwat.scm.locationManager.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class AllocationResponse {

    private String allocationPlanId;
    private String communityOrderId;
    private String communityId;
    private String status;
    private Instant createdAt;

    private List<ProductAllocationResult> productAllocations;

    @Data
    @Builder
    public static class ProductAllocationResult {
        private String productId;
        private List<CustomerAllocationResult> customerAllocations;
    }

    @Data
    @Builder
    public static class CustomerAllocationResult {
        private String customerId;
        private String deliveryPincode;
        private String deliveryCluster;
        private String deliveryZone;
        private String deliveryState;

        private String allocatedSourceId;
        private String allocatedSourceType;
        private String allocatedSourcePincode;
        private String allocatedSourceCluster;
        private String allocatedSourceZone;

        private String proximityLevel;
        private int proximityScore;
    }

    @Data
    @Builder
    public static class AllocationPlanSummary {
        private String allocationPlanId;
        private String communityOrderId;
        private String communityId;
        private Instant createdAt;
        private String status;
        private int totalProducts;
        private int totalCustomers;
    }
}
