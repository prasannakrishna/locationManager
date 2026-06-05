package com.bhagwat.scm.locationManager.service;

import com.bhagwat.scm.locationManager.dto.CustomerCommunityRequest;
import com.bhagwat.scm.locationManager.entity.Cluster;
import com.bhagwat.scm.locationManager.entity.CommunityClusterMapping;
import com.bhagwat.scm.locationManager.repository.ClusterRepository;
import com.bhagwat.scm.locationManager.repository.CommunityClusterMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommunityClusterService {

    private final ClusterRepository clusterRepository;
    private final CommunityClusterMappingRepository mappingRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * Adds a customer to the community-cluster mapping based on their pincode.
     * Finds the cluster for the pincode efficiently using clusterName index,
     * then upserts the mapping.
     */
    public CommunityClusterMapping addCustomerToCommunityCluster(CustomerCommunityRequest request) {
        String pincode = request.getPincode();
        String clusterPrefix = pincode.substring(0, 3);

        // Efficient lookup: clusterName index + pincode key existence check
        Query query = new Query(
                Criteria.where("clusterName").is(clusterPrefix)
                        .and("pincodes." + pincode).exists(true)
        );
        Cluster cluster = mongoTemplate.findOne(query, Cluster.class);
        if (cluster == null) {
            throw new RuntimeException("No cluster found for pincode: " + pincode);
        }

        // Find or create the community-cluster mapping
        CommunityClusterMapping mapping = mappingRepository
                .findByCommunityIdAndClusterName(request.getCommunityId(), cluster.getClusterName())
                .orElseGet(() -> {
                    CommunityClusterMapping m = new CommunityClusterMapping();
                    m.setCommunityId(request.getCommunityId());
                    m.setClusterName(cluster.getClusterName());
                    return m;
                });

        mapping.getCustomerIds().add(request.getCustomerId());
        return mappingRepository.save(mapping);
    }

    /**
     * Returns all community-cluster mappings for a given community.
     */
    public List<CommunityClusterMapping> getMappingsForCommunity(String communityId) {
        return mappingRepository.findByCommunityId(communityId);
    }

    /**
     * Returns all clusters a community spans across (their full cluster info).
     */
    public List<Cluster> getClustersForCommunity(String communityId) {
        List<CommunityClusterMapping> mappings = mappingRepository.findByCommunityId(communityId);
        List<String> clusterNames = mappings.stream()
                .map(CommunityClusterMapping::getClusterName)
                .toList();
        return clusterRepository.findByClusterNameIn(clusterNames);
    }
}
