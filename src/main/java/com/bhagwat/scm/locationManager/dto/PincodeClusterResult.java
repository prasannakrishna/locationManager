package com.bhagwat.scm.locationManager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PincodeClusterResult {
    private String pincode;
    private String placeName;
    private String clusterName;
    private String zone;
    private String state;
    private Integer clusterId;
}
