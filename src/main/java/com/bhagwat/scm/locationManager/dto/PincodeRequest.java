package com.bhagwat.scm.locationManager.dto;
import java.util.List;

public class PincodeRequest {
    private List<String> pincodes;

    public List<String> getPincodes() {
        return pincodes;
    }

    public void setPincodes(List<String> pincodes) {
        this.pincodes = pincodes;
    }
}

