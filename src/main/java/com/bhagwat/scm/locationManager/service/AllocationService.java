package com.bhagwat.scm.locationManager.service;

import com.bhagwat.scm.locationManager.dto.AllocationRequest;
import com.bhagwat.scm.locationManager.dto.AllocationRequest.CustomerDeliveryInput;
import com.bhagwat.scm.locationManager.dto.AllocationRequest.ProductSourceInput;
import com.bhagwat.scm.locationManager.dto.AllocationRequest.SourceInput;
import com.bhagwat.scm.locationManager.dto.AllocationResponse;
import com.bhagwat.scm.locationManager.dto.AllocationResponse.AllocationPlanSummary;
import com.bhagwat.scm.locationManager.dto.AllocationResponse.CustomerAllocationResult;
import com.bhagwat.scm.locationManager.dto.AllocationResponse.ProductAllocationResult;
import com.bhagwat.scm.locationManager.dto.PincodeClusterResult;
import com.bhagwat.scm.locationManager.entity.OrderAllocationPlan;
import com.bhagwat.scm.locationManager.entity.OrderAllocationPlan.CustomerAllocation;
import com.bhagwat.scm.locationManager.entity.OrderAllocationPlan.PlanStatus;
import com.bhagwat.scm.locationManager.entity.OrderAllocationPlan.ProductAllocation;
import com.bhagwat.scm.locationManager.entity.OrderAllocationPlan.ProximityLevel;
import com.bhagwat.scm.locationManager.repository.AllocationPlanRepository;
import com.bhagwat.scm.locationManager.repository.ClusterRepository;
import com.bhagwat.scm.locationManager.entity.Cluster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AllocationService {

    private final ClusterService clusterService;
    private final ClusterRepository clusterRepository;
    private final AllocationPlanRepository allocationPlanRepository;

    private final ProximityScorer scorer = new ProximityScorer();

    /**
     * Main allocation method.
     *
     * Algorithm:
     * 1. Collect all unique pincodes (sources + deliveries) from the request.
     * 2. Group by 3-digit prefix → one batch query to MongoDB (findByClusterNameIn).
     * 3. Build a Map<pincode, PincodeClusterResult> from the results.
     * 4. For each product × customer, score every source and pick the nearest.
     * 5. Persist and return the allocation plan.
     *
     * Idempotent: if a plan already exists for this communityOrderId, return it.
     */
    public AllocationResponse allocate(AllocationRequest request) {
        // Idempotency check
        Optional<OrderAllocationPlan> existing =
                allocationPlanRepository.findByCommunityOrderId(request.getCommunityOrderId());
        if (existing.isPresent()) {
            log.info("Returning existing allocation plan for communityOrderId={}", request.getCommunityOrderId());
            return toResponse(existing.get());
        }

        // ── Step 1: Collect all unique pincodes ──────────────────────────────
        Set<String> allPincodes = new HashSet<>();
        for (ProductSourceInput product : request.getProducts()) {
            for (SourceInput source : product.getSources()) {
                allPincodes.add(source.getSourcePincode());
            }
        }
        for (CustomerDeliveryInput customer : request.getCustomers()) {
            allPincodes.add(customer.getDeliveryPincode());
        }

        // ── Step 2: Batch resolve — one MongoDB query per unique 3-digit prefix ──
        Map<String, PincodeClusterResult> resolvedMap = batchResolvePincodes(allPincodes);
        log.info("Resolved {}/{} pincodes from MongoDB", resolvedMap.size(), allPincodes.size());

        boolean hasUnresolved = resolvedMap.size() < allPincodes.size();

        // ── Step 3 & 4: Score and allocate ───────────────────────────────────
        List<ProductAllocation> productAllocations = new ArrayList<>();

        for (ProductSourceInput product : request.getProducts()) {
            List<CustomerAllocation> customerAllocations = new ArrayList<>();

            for (CustomerDeliveryInput customer : request.getCustomers()) {
                PincodeClusterResult delivery = resolvedMap.get(customer.getDeliveryPincode());

                CustomerAllocation allocation;

                if (delivery == null) {
                    // Unresolvable delivery pincode — assign first available source as fallback
                    log.warn("Cannot resolve delivery pincode {} for customer {} — using fallback source",
                            customer.getDeliveryPincode(), customer.getCustomerId());
                    SourceInput fallback = product.getSources().get(0);
                    PincodeClusterResult fallbackResolved = resolvedMap.get(fallback.getSourcePincode());
                    allocation = buildFallbackAllocation(customer, fallback, fallbackResolved);
                } else {
                    ProximityScorer.ScoredSource best =
                            scorer.pickBest(delivery, product.getSources(), resolvedMap, clusterService);

                    if (best == null) {
                        // No source was resolvable — use first source with no cluster info
                        SourceInput fallback = product.getSources().get(0);
                        allocation = buildFallbackAllocation(customer, fallback, null);
                    } else {
                        allocation = CustomerAllocation.builder()
                                .customerId(customer.getCustomerId())
                                .deliveryPincode(customer.getDeliveryPincode())
                                .deliveryClusterName(delivery.getClusterName())
                                .deliveryZone(clusterService.normalize(delivery.getZone()))
                                .deliveryState(clusterService.normalize(delivery.getState()))
                                .allocatedSourceId(best.source().getSourceId())
                                .allocatedSourceType(best.source().getSourceType().name())
                                .allocatedSourcePincode(best.source().getSourcePincode())
                                .allocatedSourceCluster(best.resolved().getClusterName())
                                .allocatedSourceZone(clusterService.normalize(best.resolved().getZone()))
                                .allocatedSourceState(clusterService.normalize(best.resolved().getState()))
                                .proximityLevel(best.level())
                                .proximityScore(best.proximityScore())
                                .build();
                    }
                }
                customerAllocations.add(allocation);
            }

            productAllocations.add(ProductAllocation.builder()
                    .productId(product.getProductId())
                    .customerAllocations(customerAllocations)
                    .build());
        }

        // ── Step 5: Persist ───────────────────────────────────────────────────
        PlanStatus status = hasUnresolved ? PlanStatus.PARTIAL : PlanStatus.COMPLETED;

        OrderAllocationPlan plan = OrderAllocationPlan.builder()
                .communityOrderId(request.getCommunityOrderId())
                .communityId(request.getCommunityId())
                .status(status)
                .createdAt(Instant.now())
                .productAllocations(productAllocations)
                .build();

        plan = allocationPlanRepository.save(plan);
        log.info("Saved allocation plan id={} status={} for communityOrderId={}",
                plan.getId(), status, request.getCommunityOrderId());

        return toResponse(plan);
    }

    public Optional<AllocationResponse> getAllocationByOrderId(String communityOrderId) {
        return allocationPlanRepository.findByCommunityOrderId(communityOrderId)
                .map(this::toResponse);
    }

    public List<AllocationPlanSummary> getAllocationsByCommunity(String communityId) {
        return allocationPlanRepository.findByCommunityId(communityId).stream()
                .map(plan -> AllocationPlanSummary.builder()
                        .allocationPlanId(plan.getId())
                        .communityOrderId(plan.getCommunityOrderId())
                        .communityId(plan.getCommunityId())
                        .createdAt(plan.getCreatedAt())
                        .status(plan.getStatus().name())
                        .totalProducts(plan.getProductAllocations() != null ? plan.getProductAllocations().size() : 0)
                        .totalCustomers(plan.getProductAllocations() != null && !plan.getProductAllocations().isEmpty()
                                ? plan.getProductAllocations().get(0).getCustomerAllocations().size() : 0)
                        .build())
                .collect(Collectors.toList());
    }

    // ── Batch pincode resolution — single MongoDB query per unique prefix ──────

    private Map<String, PincodeClusterResult> batchResolvePincodes(Set<String> pincodes) {
        // Group pincodes by 3-digit prefix
        Map<String, List<String>> byPrefix = pincodes.stream()
                .filter(p -> p != null && p.length() >= 3)
                .collect(Collectors.groupingBy(p -> p.substring(0, 3)));

        // One query to fetch all relevant clusters
        List<Cluster> clusters = clusterRepository.findByClusterNameIn(new ArrayList<>(byPrefix.keySet()));

        // Build pincode → PincodeClusterResult map from cluster documents
        Map<String, PincodeClusterResult> result = new HashMap<>();
        for (Cluster cluster : clusters) {
            List<String> toFind = byPrefix.getOrDefault(cluster.getClusterName(), List.of());
            for (String pincode : toFind) {
                if (cluster.getPincodes() != null && cluster.getPincodes().containsKey(pincode)) {
                    result.put(pincode, new PincodeClusterResult(
                            pincode,
                            clusterService.normalize(cluster.getPincodes().get(pincode)),
                            cluster.getClusterName(),
                            clusterService.normalize(cluster.getZone()),
                            clusterService.normalize(cluster.getState()),
                            cluster.getClusterId()
                    ));
                }
            }
        }
        return result;
    }

    // ── Fallback when pincode cannot be resolved ──────────────────────────────

    private CustomerAllocation buildFallbackAllocation(CustomerDeliveryInput customer,
                                                        SourceInput fallback,
                                                        PincodeClusterResult fallbackResolved) {
        return CustomerAllocation.builder()
                .customerId(customer.getCustomerId())
                .deliveryPincode(customer.getDeliveryPincode())
                .allocatedSourceId(fallback.getSourceId())
                .allocatedSourceType(fallback.getSourceType().name())
                .allocatedSourcePincode(fallback.getSourcePincode())
                .allocatedSourceCluster(fallbackResolved != null ? fallbackResolved.getClusterName() : null)
                .allocatedSourceZone(fallbackResolved != null ? clusterService.normalize(fallbackResolved.getZone()) : null)
                .allocatedSourceState(fallbackResolved != null ? clusterService.normalize(fallbackResolved.getState()) : null)
                .proximityLevel(ProximityLevel.DIFFERENT_STATE)
                .proximityScore(ProximityLevel.DIFFERENT_STATE.score)
                .build();
    }

    // ── Response mapper ───────────────────────────────────────────────────────

    private AllocationResponse toResponse(OrderAllocationPlan plan) {
        List<ProductAllocationResult> products = Optional.ofNullable(plan.getProductAllocations())
                .orElse(List.of()).stream()
                .map(pa -> ProductAllocationResult.builder()
                        .productId(pa.getProductId())
                        .customerAllocations(Optional.ofNullable(pa.getCustomerAllocations())
                                .orElse(List.of()).stream()
                                .map(ca -> CustomerAllocationResult.builder()
                                        .customerId(ca.getCustomerId())
                                        .deliveryPincode(ca.getDeliveryPincode())
                                        .deliveryCluster(ca.getDeliveryClusterName())
                                        .deliveryZone(ca.getDeliveryZone())
                                        .deliveryState(ca.getDeliveryState())
                                        .allocatedSourceId(ca.getAllocatedSourceId())
                                        .allocatedSourceType(ca.getAllocatedSourceType())
                                        .allocatedSourcePincode(ca.getAllocatedSourcePincode())
                                        .allocatedSourceCluster(ca.getAllocatedSourceCluster())
                                        .allocatedSourceZone(ca.getAllocatedSourceZone())
                                        .proximityLevel(ca.getProximityLevel() != null ? ca.getProximityLevel().name() : null)
                                        .proximityScore(ca.getProximityScore())
                                        .build())
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());

        return AllocationResponse.builder()
                .allocationPlanId(plan.getId())
                .communityOrderId(plan.getCommunityOrderId())
                .communityId(plan.getCommunityId())
                .status(plan.getStatus().name())
                .createdAt(plan.getCreatedAt())
                .productAllocations(products)
                .build();
    }
}
