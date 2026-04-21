package com.pochta.service;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@lombok.RequiredArgsConstructor
public class PricingService {

    private final BranchService branchService;

    public double calculateCost(String fromBranch, String toBranch, double weight) {
        int distance = Math.abs(
                getDistance(fromBranch) - getDistance(toBranch)
        );

        // Формула: базовый тариф + вес
        double cost = (distance * 0.18) + (weight * 12);

        // Минимальная стоимость
        if (cost < 50) cost = 50;

        return Math.round(cost * 100.0) / 100.0; // до копеек
    }

    private int getDistance(String cityName) {
        var branch = branchService.getBranchByName(cityName);
        return branch != null ? branch.getDistance() : 400;
    }
}