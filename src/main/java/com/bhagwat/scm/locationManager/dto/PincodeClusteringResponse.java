package com.bhagwat.scm.locationManager.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PincodeClusteringResponse {

    /** One group per unique demand cluster found. */
    private List<DemandClusterGroup> groups;

    /** Demand pincodes that could not be resolved from cluster data. */
    private List<String> unresolvedDemandPincodes;

    /** Supply pincodes that could not be resolved from cluster data. */
    private List<String> unresolvedSupplyPincodes;

    @Data
    @Builder
    public static class DemandClusterGroup {
        /** 3-digit cluster prefix — the group key. */
        private String clusterName;
        private String zone;
        private String state;

        /** All demand pincodes that belong to this cluster. */
        private List<String> demandPincodes;

        /**
         * All supply pincodes ranked closest → farthest relative to this demand cluster.
         * Proximity order: SAME_CLUSTER → SAME_ZONE → SAME_STATE → DIFFERENT_STATE
         */
        private List<RankedSupplyPincode> rankedSupplyPincodes;
    }

    @Data
    @Builder
    public static class RankedSupplyPincode {
        private String pincode;
        private String clusterName;
        private String zone;
        private String state;
        private String proximity;   // SAME_CLUSTER | SAME_ZONE | SAME_STATE | DIFFERENT_STATE
        private int proximityScore; // 1 = closest … 4 = farthest
    }
}
