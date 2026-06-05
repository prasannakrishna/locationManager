package com.bhagwat.scm.locationManager.service;

import com.bhagwat.scm.locationManager.entity.Cluster;
import com.bhagwat.scm.locationManager.repository.ClusterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cleans up newline characters (\n) introduced during PDF-to-JSON extraction.
 * Affected fields: state, zone, and place names inside the pincodes map.
 * Run once via POST /admin/cleanup — safe to re-run (idempotent).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataCleanupService {

    private final ClusterRepository clusterRepository;
    private final ClusterService clusterService;

    public CleanupResult cleanupClusters() {
        List<Cluster> allClusters = clusterRepository.findAll();
        AtomicInteger updated = new AtomicInteger(0);

        for (Cluster cluster : allClusters) {
            boolean dirty = false;

            String cleanState = clusterService.normalize(cluster.getState());
            if (!cleanState.equals(cluster.getState())) {
                cluster.setState(cleanState);
                dirty = true;
            }

            String cleanZone = clusterService.normalize(cluster.getZone());
            if (!cleanZone.equals(cluster.getZone())) {
                cluster.setZone(cleanZone);
                dirty = true;
            }

            // Clean place names inside pincodes map
            if (cluster.getPincodes() != null) {
                Map<String, String> cleanPincodes = new LinkedHashMap<>();
                boolean pincodesChanged = false;
                for (Map.Entry<String, String> entry : cluster.getPincodes().entrySet()) {
                    String cleanPlace = clusterService.normalize(entry.getValue());
                    cleanPincodes.put(entry.getKey(), cleanPlace);
                    if (!cleanPlace.equals(entry.getValue())) {
                        pincodesChanged = true;
                    }
                }
                if (pincodesChanged) {
                    cluster.setPincodes(cleanPincodes);
                    dirty = true;
                }
            }

            if (dirty) {
                clusterRepository.save(cluster);
                updated.incrementAndGet();
            }
        }

        log.info("Cleanup complete: {} of {} clusters updated", updated.get(), allClusters.size());
        return new CleanupResult(allClusters.size(), updated.get());
    }

    public record CleanupResult(int totalClusters, int clustersUpdated) {}
}
