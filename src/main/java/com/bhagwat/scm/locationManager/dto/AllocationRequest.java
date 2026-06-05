package com.bhagwat.scm.locationManager.dto;

import lombok.Data;

import java.util.List;

@Data
public class AllocationRequest {

    private String communityId;
    private String communityOrderId;

    /** One entry per product in the community order. */
    private List<ProductSourceInput> products;

    /** All customers in this community order and their delivery pincodes. */
    private List<CustomerDeliveryInput> customers;

    @Data
    public static class ProductSourceInput {
        private String productId;
        /** One or more sources where the product is available (seller / store / warehouse). */
        private List<SourceInput> sources;
    }

    @Data
    public static class SourceInput {
        private String sourceId;      // seller ID, store ID, or warehouse ID
        private String sourcePincode;
        private SourceType sourceType;
    }

    @Data
    public static class CustomerDeliveryInput {
        private String customerId;
        private String deliveryPincode;
    }

    public enum SourceType { SELLER, STORE, WAREHOUSE }
}
