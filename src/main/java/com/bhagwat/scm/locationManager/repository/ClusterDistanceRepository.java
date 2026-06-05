package com.bhagwat.scm.locationManager.repository;

import com.bhagwat.scm.locationManager.entity.ClusterDistance;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ClusterDistanceRepository extends MongoRepository<ClusterDistance, String> {

    Optional<ClusterDistance> findByFromClusterAndToCluster(String fromCluster, String toCluster);

    /** Find all clusters within a distance from a given cluster */
    @Query("{ $or: [ { 'fromCluster': ?0, 'distanceKm': { $lte: ?1 } }, { 'toCluster': ?0, 'distanceKm': { $lte: ?1 } } ] }")
    List<ClusterDistance> findWithinDistance(String clusterName, double maxDistanceKm);

    /** Find nearest N clusters to a given cluster */
    @Query(value = "{ $or: [ { 'fromCluster': ?0 }, { 'toCluster': ?0 } ] }", sort = "{ 'distanceKm': 1 }")
    List<ClusterDistance> findNearestClusters(String clusterName);

    /** Count total precomputed distances */
    long count();
}
