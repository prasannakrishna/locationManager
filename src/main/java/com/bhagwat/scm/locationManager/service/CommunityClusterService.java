package com.bhagwat.scm.locationManager.service;
@Service
public class CommunityClusterService {

    private final ClusterRepository clusterRepository;
    private final CommunityClusterMappingRepository mappingRepository;

    public CommunityClusterService(ClusterRepository clusterRepository,
                                   CommunityClusterMappingRepository mappingRepository) {
        this.clusterRepository = clusterRepository;
        this.mappingRepository = mappingRepository;
    }

    public CommunityClusterMapping addCustomerToCommunityCluster(CustomerCommunityRequest request) {
        String pincode = request.getPincode();
        String clusterPrefix = pincode.substring(0, 3);

        // Find the cluster this pincode belongs to
        Cluster cluster = clusterRepository.findAll().stream()
                .filter(c -> c.getClusterName().equals(clusterPrefix)
                        && c.getPincodes().containsKey(pincode))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Cluster not found for pincode: " + pincode));

        // Find or create mapping
        CommunityClusterMapping mapping = mappingRepository
                .findByCommunityIdAndClusterName(request.getCommunityId(), cluster.getClusterName())
                .orElseGet(() -> {
                    CommunityClusterMapping newMapping = new CommunityClusterMapping();
                    newMapping.setCommunityId(request.getCommunityId());
                    newMapping.setClusterName(cluster.getClusterName());
                    return newMapping;
                });

        // Add customer
        mapping.getCustomerIds().add(request.getCustomerId());

        return mappingRepository.save(mapping);
    }
}