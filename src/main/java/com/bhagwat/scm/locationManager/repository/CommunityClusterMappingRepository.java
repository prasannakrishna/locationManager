package com.bhagwat.scm.locationManager.repository;
import com.bhagwat.scm.locationManager.entity.CommunityClusterMapping;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CommunityClusterMappingRepository extends MongoRepository<CommunityClusterMapping, String> {
    Optional<CommunityClusterMapping> findByCommunityIdAndClusterName(String communityId, String clusterName);
}