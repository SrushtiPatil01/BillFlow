package com.billflow.dto;

public class CheckoutResponse {

    private String checkoutUrl;
    private Long subscriptionId;

    public CheckoutResponse() {}

    public CheckoutResponse(String checkoutUrl, Long subscriptionId) {
        this.checkoutUrl = checkoutUrl;
        this.subscriptionId = subscriptionId;
    }

    public String getCheckoutUrl() { return checkoutUrl; }
    public void setCheckoutUrl(String checkoutUrl) { this.checkoutUrl = checkoutUrl; }

    public Long getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(Long subscriptionId) { this.subscriptionId = subscriptionId; }
}

