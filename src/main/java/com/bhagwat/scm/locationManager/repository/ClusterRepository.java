package com.bhagwat.scm.locationManager.repository;

import com.bhagwat.scm.locationManager.entity.Cluster;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClusterRepository extends MongoRepository<Cluster, String> {

    /** Find all clusters with a given 3-digit prefix (e.g. "560") */
    List<Cluster> findByClusterName(String clusterName);

    /** Find clusters by state — case-insensitive partial match to handle dirty \n data */
    @Query("{ 'state': { $regex: ?0, $options: 'i' } }")
    List<Cluster> findByStateContaining(String state);

    /** Find clusters by zone — case-insensitive partial match */
    @Query("{ 'zone': { $regex: ?0, $options: 'i' } }")
    List<Cluster> findByZoneContaining(String zone);

    /** Efficient pincode lookup: query by clusterName prefix AND check pincode key exists in map */
    @Query("{ 'clusterName': ?0, 'pincodes.?1': { $exists: true } }")
    Optional<Cluster> findByClusterNameAndPincode(String clusterName, String pincode);

    /** Batch lookup by multiple cluster prefixes */
    List<Cluster> findByClusterNameIn(List<String> clusterNames);
}
