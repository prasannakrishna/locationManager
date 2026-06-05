package com.bhagwat.scm.locationManager.service;

import com.bhagwat.scm.locationManager.dto.PincodeClusteringRequest;
import com.bhagwat.scm.locationManager.dto.PincodeClusteringResponse;
import com.bhagwat.scm.locationManager.dto.PincodeClusteringResponse.DemandClusterGroup;
import com.bhagwat.scm.locationManager.dto.PincodeClusteringResponse.RankedSupplyPincode;
import com.bhagwat.scm.locationManager.dto.PincodeClusterResult;
import com.bhagwat.scm.locationManager.entity.Cluster;
import com.bhagwat.scm.locationManager.repository.ClusterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generic pincode clustering service.
 *
 * Takes two flat lists — demand pincodes and supply pincodes — and returns
 * groups where:
 *   - demand pincodes are grouped by their 3-digit clusterName prefix
 *   - for each group, ALL supply pincodes are ranked closest → farthest
 *     using the hierarchy: SAME_CLUSTER → SAME_ZONE → SAME_STATE → DIFFERENT_STATE
 *
 * Uses a single batch MongoDB query (findByClusterNameIn) to resolve all
 * pincodes, then pure in-memory scoring — no per-pincode DB calls.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PincodeClusteringService {

    private final ClusterRepository clusterRepository;
    private final ClusterService clusterService;

    public PincodeClusteringResponse cluster(PincodeClusteringRequest request) {
        List<String> demandPins = request.getDemandPincodes();
        List<String> supplyPins = request.getSupplyPincodes();

        // ── Step 1: Batch-resolve all pincodes in one MongoDB round-trip ─────
        Set<String> allPins = new HashSet<>();
        allPins.addAll(demandPins);
        allPins.addAll(supplyPins);

        Map<String, PincodeClusterResult> resolved = batchResolve(allPins);
        log.info("Resolved {}/{} pincodes", resolved.size(), allPins.size());

        List<String> unresolvedDemand = demandPins.stream()
                .filter(p -> !resolved.containsKey(p)).collect(Collectors.toList());
        List<String> unresolvedSupply = supplyPins.stream()
                .filter(p -> !resolved.containsKey(p)).collect(Collectors.toList());

        if (!unresolvedDemand.isEmpty())
            log.warn("Unresolved demand pincodes: {}", unresolvedDemand);
        if (!unresolvedSupply.isEmpty())
            log.warn("Unresolved supply pincodes: {}", unresolvedSupply);

        // Resolved supply pincodes (preserve input order)
        List<PincodeClusterResult> resolvedSupply = supplyPins.stream()
                .filter(resolved::containsKey)
                .map(resolved::get)
                .collect(Collectors.toList());

        // ── Step 2: Group demand pincodes by clusterName ──────────────────────
        // Use LinkedHashMap to preserve the order groups are first encountered
        Map<String, List<PincodeClusterResult>> demandByCluster = new LinkedHashMap<>();

        for (String pin : demandPins) {
            PincodeClusterResult r = resolved.get(pin);
            if (r == null) continue;
            demandByCluster
                    .computeIfAbsent(r.getClusterName(), k -> new ArrayList<>())
                    .add(r);
        }

        // ── Step 3: For each demand cluster, rank supply pincodes ─────────────
        List<DemandClusterGroup> groups = new ArrayList<>();

        for (Map.Entry<String, List<PincodeClusterResult>> entry : demandByCluster.entrySet()) {
            String clusterName = entry.getKey();
            List<PincodeClusterResult> demandInCluster = entry.getValue();

            // Representative demand cluster info (all share same clusterName;
            // use the first resolved result for zone/state label)
            PincodeClusterResult representative = demandInCluster.get(0);

            // Rank all resolved supply pincodes relative to this demand cluster
            List<RankedSupplyPincode> ranked = resolvedSupply.stream()
                    .map(supply -> {
                        ProximityResult prox = proximity(representative, supply);
                        return RankedSupplyPincode.builder()
                                .pincode(supply.getPincode())
                                .clusterName(supply.getClusterName())
                                .zone(clusterService.normalize(supply.getZone()))
                                .state(clusterService.normalize(supply.getState()))
                                .proximity(prox.level())
                                .proximityScore(prox.score())
                                .build();
                    })
                    // Sort: closest first (score 1), farthest last (score 4)
                    // Stable sort — ties preserve original supply input order
                    .sorted(Comparator.comparingInt(RankedSupplyPincode::getProximityScore))
                    .collect(Collectors.toList());

            groups.add(DemandClusterGroup.builder()
                    .clusterName(clusterName)
                    .zone(clusterService.normalize(representative.getZone()))
                    .state(clusterService.normalize(representative.getState()))
                    .demandPincodes(demandInCluster.stream()
                            .map(PincodeClusterResult::getPincode)
                            .collect(Collectors.toList()))
                    .rankedSupplyPincodes(ranked)
                    .build());
        }

        return PincodeClusteringResponse.builder()
                .groups(groups)
                .unresolvedDemandPincodes(unresolvedDemand)
                .unresolvedSupplyPincodes(unresolvedSupply)
                .build();
    }

    // ── Proximity scoring ─────────────────────────────────────────────────────

    /**
     * Scores proximity between a demand cluster and a supply pincode.
     *
     * Hierarchy (first match wins):
     *   SAME_CLUSTER    (1) — demand.clusterName == supply.clusterName
     *   SAME_ZONE       (2) — demand.zone == supply.zone (district)
     *   SAME_STATE      (3) — demand.state == supply.state
     *   DIFFERENT_STATE (4) — no match (farthest / fallback)
     */
    private ProximityResult proximity(PincodeClusterResult demand, PincodeClusterResult supply) {
        String dCluster = demand.getClusterName();
        String dZone    = clusterService.normalize(demand.getZone());
        String dState   = clusterService.normalize(demand.getState());

        String sCluster = supply.getClusterName();
        String sZone    = clusterService.normalize(supply.getZone());
        String sState   = clusterService.normalize(supply.getState());

        if (Objects.equals(dCluster, sCluster)) return new ProximityResult("SAME_CLUSTER", 1);
        if (Objects.equals(dZone,    sZone))    return new ProximityResult("SAME_ZONE",    2);
        if (Objects.equals(dState,   sState))   return new ProximityResult("SAME_STATE",   3);
        return                                         new ProximityResult("DIFFERENT_STATE", 4);
    }

    record ProximityResult(String level, int score) {}

    // ── Batch resolve pincodes — one MongoDB query per unique 3-digit prefix ──

    private Map<String, PincodeClusterResult> batchResolve(Set<String> pincodes) {
        Map<String, List<String>> byPrefix = pincodes.stream()
                .filter(p -> p != null && p.length() >= 3)
                .collect(Collectors.groupingBy(p -> p.substring(0, 3)));

        List<Cluster> clusters = clusterRepository.findByClusterNameIn(new ArrayList<>(byPrefix.keySet()));

        Map<String, PincodeClusterResult> result = new HashMap<>();
        for (Cluster c : clusters) {
            for (String pin : byPrefix.getOrDefault(c.getClusterName(), List.of())) {
                if (c.getPincodes() != null && c.getPincodes().containsKey(pin)) {
                    result.put(pin, new PincodeClusterResult(
                            pin,
                            clusterService.normalize(c.getPincodes().get(pin)),
                            c.getClusterName(),
                            c.getZone(),
                            c.getState(),
                            c.getClusterId()
                    ));
                }
            }
        }
        return result;
    }
}
