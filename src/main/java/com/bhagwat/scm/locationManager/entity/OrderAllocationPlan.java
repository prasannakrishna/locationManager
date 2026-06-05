package com.bhagwat.scm.locationManager.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "orderAllocationPlans")
@CompoundIndexes({
        @CompoundIndex(name = "community_created_idx", def = "{'communityId': 1, 'createdAt': -1}")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderAllocationPlan {

    @Id
    private String id;

    @Indexed(unique = true)
    private String communityOrderId;

    private String communityId;

    private PlanStatus status;

    @Builder.Default
    private Instant createdAt = Instant.now();

    private List<ProductAllocation> productAllocations;

    public enum PlanStatus { PENDING, COMPLETED, PARTIAL }

    // ── Embedded: one per product in the order ────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProductAllocation {
        private String productId;
        private List<CustomerAllocation> customerAllocations;
    }

    // ── Embedded: one per customer per product ────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CustomerAllocation {
        private String customerId;
        private String deliveryPincode;
        private String deliveryClusterName;
        private String deliveryZone;
        private String deliveryState;

        private String allocatedSourceId;
        private String allocatedSourceType;   // SELLER | STORE | WAREHOUSE
        private String allocatedSourcePincode;
        private String allocatedSourceCluster;
        private String allocatedSourceZone;
        private String allocatedSourceState;

        private ProximityLevel proximityLevel;
        private int proximityScore;           // 1=SAME_CLUSTER … 4=DIFFERENT_STATE
    }

    public enum ProximityLevel {
        SAME_CLUSTER(1), SAME_ZONE(2), SAME_STATE(3), DIFFERENT_STATE(4);

        public final int score;
        ProximityLevel(int score) { this.score = score; }
    }
}
