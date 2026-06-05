package com.bhagwat.scm.locationManager.service;

import com.bhagwat.scm.locationManager.dto.ClusterResponse;
import com.bhagwat.scm.locationManager.dto.PincodeClusterResult;
import com.bhagwat.scm.locationManager.entity.Cluster;
import com.bhagwat.scm.locationManager.repository.ClusterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClusterService {

    private final ClusterRepository clusterRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * Lookup clusters for a list of pincodes.
     * Uses clusterName index (3-digit prefix) instead of scanning all 445 clusters.
     */
    public List<ClusterResponse> getClustersByPincodes(List<String> pincodes) {
        // Group pincodes by their 3-digit prefix to batch query per prefix
        Map<String, List<String>> byPrefix = pincodes.stream()
                .filter(p -> p != null && p.length() >= 3)
                .collect(Collectors.groupingBy(p -> p.substring(0, 3)));

        List<String> prefixes = new ArrayList<>(byPrefix.keySet());
        List<Cluster> clusters = clusterRepository.findByClusterNameIn(prefixes);

        List<ClusterResponse> responses = new ArrayList<>();
        for (Cluster cluster : clusters) {
            List<String> matchingPincodes = byPrefix.getOrDefault(cluster.getClusterName(), List.of());
            for (String pincode : matchingPincodes) {
                if (cluster.getPincodes() != null && cluster.getPincodes().containsKey(pincode)) {
                    responses.add(toResponse(cluster, Collections.singletonMap(
                            pincode, cluster.getPincodes().get(pincode))));
                }
            }
        }
        return responses;
    }

    /**
     * Single pincode lookup — returns the cluster and place name for one pincode.
     */
    public Optional<PincodeClusterResult> lookupPincode(String pincode) {
        if (pincode == null || pincode.length() < 3) return Optional.empty();
        String prefix = pincode.substring(0, 3);

        // Query MongoDB: clusterName = prefix AND pincodes.<pincode> exists
        Query query = new Query(
                Criteria.where("clusterName").is(prefix)
                        .and("pincodes." + pincode).exists(true)
        );
        Cluster cluster = mongoTemplate.findOne(query, Cluster.class);
        if (cluster == null) return Optional.empty();

        String placeName = normalize(cluster.getPincodes().get(pincode));
        return Optional.of(new PincodeClusterResult(
                pincode,
                placeName,
                cluster.getClusterName(),
                normalize(cluster.getZone()),
                normalize(cluster.getState()),
                cluster.getClusterId()
        ));
    }

    /**
     * Get all clusters in a state (case-insensitive, handles dirty \n data).
     */
    public List<ClusterResponse> getClustersByState(String state) {
        return clusterRepository.findByStateContaining(state).stream()
                .map(c -> toResponse(c, c.getPincodes()))
                .collect(Collectors.toList());
    }

    /**
     * Get all clusters in a zone (case-insensitive, handles dirty \n data).
     */
    public List<ClusterResponse> getClustersByZone(String zone) {
        return clusterRepository.findByZoneContaining(zone).stream()
                .map(c -> toResponse(c, c.getPincodes()))
                .collect(Collectors.toList());
    }

    /**
     * Get all distinct states (normalized — \n removed).
     */
    public List<String> getAllStates() {
        return mongoTemplate.findDistinct("state", Cluster.class, String.class)
                .stream()
                .map(this::normalize)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Get all distinct zones (normalized — \n removed).
     */
    public List<String> getAllZones() {
        return mongoTemplate.findDistinct("zone", Cluster.class, String.class)
                .stream()
                .map(this::normalize)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    // --- helpers ---

    private ClusterResponse toResponse(Cluster cluster, Map<String, String> pincodes) {
        ClusterResponse r = new ClusterResponse();
        r.setClusterId(cluster.getClusterId());
        r.setState(normalize(cluster.getState()));
        r.setClusterName(cluster.getClusterName());
        r.setZone(normalize(cluster.getZone()));
        // Normalize place names in pincodes map
        Map<String, String> cleanPincodes = new LinkedHashMap<>();
        if (pincodes != null) {
            pincodes.forEach((k, v) -> cleanPincodes.put(k, normalize(v)));
        }
        r.setPincodes(cleanPincodes);
        return r;
    }

    /** Strips newline characters inserted during PDF extraction. */
    public String normalize(String value) {
        if (value == null) return null;
        return value.replace("\n", " ").replace("\r", "").trim();
    }
}
