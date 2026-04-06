package com.pochta.service;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PricingService {

    // Примерные расстояния между отделениями (в км)
    private final Map<String, Integer> distances = Map.of(
            "Одесса-Центр", 0,
            "Киев-Главпочтамт", 480,
            "Львов-1", 350,
            "Харьков-Север", 700,
            "Днепр-Юг", 400
    );

    public double calculateCost(String fromBranch, String toBranch, double weight) {
        int distance = Math.abs(
                distances.getOrDefault(fromBranch, 400) -
                        distances.getOrDefault(toBranch, 400)
        );

        // Формула: базовый тариф + вес
        double cost = (distance * 0.18) + (weight * 12);

        // Минимальная стоимость
        if (cost < 50) cost = 50;

        return Math.round(cost * 100.0) / 100.0; // до копеек
    }
}