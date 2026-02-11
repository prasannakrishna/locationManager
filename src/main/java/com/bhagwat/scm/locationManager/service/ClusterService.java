package com.bhagwat.scm.locationManager.service;

public class ClusterService {
    private final ClusterRepository clusterRepository;

    public ClusterService(ClusterRepository clusterRepository) {
        this.clusterRepository = clusterRepository;
    }

    public List<ClusterResponse> getClustersByPincodes(List<String> pincodes) {
        List<ClusterResponse> responses = new ArrayList<>();

        for (String pincode : pincodes) {
            String clusterPrefix = pincode.substring(0, 3);

            // Find clusters matching the prefix
            List<Cluster> clusters = clusterRepository.findAll();

            clusters.stream()
                    .filter(cluster -> cluster.getClusterName().equals(clusterPrefix)
                            && cluster.getPincodes().containsKey(pincode))
                    .forEach(cluster -> {
                        ClusterResponse response = new ClusterResponse();
                        response.setClusterId(cluster.getClusterId());
                        response.setState(cluster.getState());
                        response.setClusterName(cluster.getClusterName());
                        response.setPincodes(Collections.singletonMap(pincode, cluster.getPincodes().get(pincode)));
                        response.setZone(cluster.getZone());
                        responses.add(response);
                    });
        }

        return responses;
    }
}
