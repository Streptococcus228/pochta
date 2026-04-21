package com.pochta.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardDto {
    private double gasolineCosts;
    private double averageDeliveryCost;
    @Builder.Default
    private List<String> popularRoutes = new java.util.ArrayList<>();
    private double averageDeliveryTime;
    @Builder.Default
    private List<VehicleStatusDto> vehicles = new java.util.ArrayList<>();
    @Builder.Default
    private List<Double> profitHistory = new java.util.ArrayList<>();
    @Builder.Default
    private List<Double> gasolineHistory = new java.util.ArrayList<>();
    @Builder.Default
    private List<String> labels = new java.util.ArrayList<>();

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class VehicleStatusDto {
        private String vehicleId;
        private String status; // FREE / BUSY
        private String currentTripId;
        private List<String> parcelNumbers;
        private int progress;
    }
}
