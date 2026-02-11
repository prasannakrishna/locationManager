package com.bhagwat.scm.locationManager.entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashSet;
import java.util.Set;

@Document(collection = "communityClusterMappings")
@CompoundIndexes({
        @CompoundIndex(name = "community_cluster_idx", def = "{'communityId': 1, 'clusterName': 1}", unique = true)
})
public class CommunityClusterMapping {

    @Id
    private String id;

    private String communityId;
    private String clusterName;
    private Set<String> customerIds = new HashSet<>();

    // Getters & Setters
    public String getCommunityId() {
        return communityId;
    }

    public void setCommunityId(String communityId) {
        this.communityId = communityId;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public Set<String> getCustomerIds() {
        return customerIds;
    }

    public void setCustomerIds(Set<String> customerIds) {
        this.customerIds = customerIds;
    }
}