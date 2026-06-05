package com.bhagwat.scm.locationManager.dto;

import lombok.Data;

import java.util.List;

@Data
public class PincodeClusteringRequest {
    /** Where customers/recipients are — to be grouped by cluster. */
    private List<String> demandPincodes;

    /** Where products/stock is available — ranked per group by proximity. */
    private List<String> supplyPincodes;
}
