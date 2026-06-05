package com.bhagwat.scm.locationManager.repository;

import com.bhagwat.scm.locationManager.entity.OrderAllocationPlan;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AllocationPlanRepository extends MongoRepository<OrderAllocationPlan, String> {
    Optional<OrderAllocationPlan> findByCommunityOrderId(String communityOrderId);
    List<OrderAllocationPlan> findByCommunityId(String communityId);
}
