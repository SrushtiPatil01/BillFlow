package com.billflow.dto;

public class SubscribeRequest {

    private Long userId;
    private String priceId;

    public SubscribeRequest() {}

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getPriceId() { return priceId; }
    public void setPriceId(String priceId) { this.priceId = priceId; }
}