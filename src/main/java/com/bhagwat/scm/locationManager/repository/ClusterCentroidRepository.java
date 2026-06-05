package com.bhagwat.scm.locationManager.repository;

import com.bhagwat.scm.locationManager.entity.ClusterCentroid;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ClusterCentroidRepository extends MongoRepository<ClusterCentroid, String> {
    Optional<ClusterCentroid> findByClusterName(String clusterName);
    List<ClusterCentroid> findByClusterNameIn(List<String> clusterNames);
    List<ClusterCentroid> findByState(String state);
    List<ClusterCentroid> findByLatitudeIsNotNull();
}
