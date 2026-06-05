package com.bhagwat.scm.locationManager.service;

import com.bhagwat.scm.locationManager.dto.AllocationRequest.SourceInput;
import com.bhagwat.scm.locationManager.dto.AllocationRequest.SourceType;
import com.bhagwat.scm.locationManager.dto.PincodeClusterResult;
import com.bhagwat.scm.locationManager.entity.OrderAllocationPlan.ProximityLevel;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Stateless utility that scores geographic proximity between a customer's
 * delivery pincode and a product's source pincode using the cluster hierarchy:
 *
 *   SAME_CLUSTER    (score 1) — same 3-digit clusterName prefix → tightest match
 *   SAME_ZONE       (score 2) — same district, different cluster
 *   SAME_STATE      (score 3) — same state, different zone
 *   DIFFERENT_STATE (score 4) — no geographic overlap → fallback
 *
 * Tie-breaking order when proximity is equal: SELLER > STORE > WAREHOUSE,
 * then input list order.
 */
class ProximityScorer {

    // Source type preference for tie-breaking (lower index = preferred)
    private static final List<SourceType> SOURCE_PREFERENCE =
            List.of(SourceType.SELLER, SourceType.STORE, SourceType.WAREHOUSE);

    record ScoredSource(
            SourceInput source,
            PincodeClusterResult resolved,
            ProximityLevel level,
            int proximityScore,
            int typeRank
    ) {}

    /** Score a single (delivery, source) pair. */
    ProximityLevel score(PincodeClusterResult delivery, PincodeClusterResult source,
                         ClusterService clusterService) {
        String dc = delivery.getClusterName();
        String dz = clusterService.normalize(delivery.getZone());
        String ds = clusterService.normalize(delivery.getState());

        String sc = source.getClusterName();
        String sz = clusterService.normalize(source.getZone());
        String ss = clusterService.normalize(source.getState());

        if (Objects.equals(dc, sc))         return ProximityLevel.SAME_CLUSTER;
        if (Objects.equals(dz, sz))         return ProximityLevel.SAME_ZONE;
        if (Objects.equals(ds, ss))         return ProximityLevel.SAME_STATE;
        return ProximityLevel.DIFFERENT_STATE;
    }

    /**
     * Given a customer's resolved delivery info and all resolved sources for a product,
     * returns the best source — closest proximity, tie-broken by source type preference.
     */
    ScoredSource pickBest(PincodeClusterResult delivery,
                          List<SourceInput> sources,
                          Map<String, PincodeClusterResult> resolved,
                          ClusterService clusterService) {

        return sources.stream()
                .filter(s -> resolved.containsKey(s.getSourcePincode()))
                .map(s -> {
                    PincodeClusterResult r = resolved.get(s.getSourcePincode());
                    ProximityLevel level = score(delivery, r, clusterService);
                    int typeRank = SOURCE_PREFERENCE.indexOf(s.getSourceType());
                    if (typeRank < 0) typeRank = SOURCE_PREFERENCE.size();
                    return new ScoredSource(s, r, level, level.score, typeRank);
                })
                .min(Comparator
                        .comparingInt(ScoredSource::proximityScore)   // 1. closest first
                        .thenComparingInt(ScoredSource::typeRank))    // 2. SELLER > STORE > WAREHOUSE
                .orElse(null);
    }
}
