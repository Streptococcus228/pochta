package com.pochta.service;

import com.pochta.model.Parcel;
import com.pochta.model.ParcelStatus;
import com.pochta.repository.ParcelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SimulatorService {

    private final ParcelRepository parcelRepository;
    private final Random random = new Random();

    // Доступные машины
    private final List<String> availableVehicles = new ArrayList<>(List.of("V001", "V002", "V003"));
    private final List<String> busyVehicles = new ArrayList<>();

    @Async
    public void startDeliverySimulation(Long parcelId) {
        Parcel parcel = parcelRepository.findById(parcelId).orElse(null);
        if (parcel == null) return;

        // Если посылка уже в процессе — ничего не делаем
        if (parcel.getStatus() == ParcelStatus.IN_TRANSIT ||
                parcel.getStatus() == ParcelStatus.DELIVERED) {
            return;
        }

        // Пытаемся назначить машину
        String vehicleId = assignVehicle();

        if (vehicleId == null) {
            System.out.println("⏳ Все машины заняты. Посылка " + parcel.getParcelNumber() + " будет ждать...");
            // Можно добавить небольшую задержку и попробовать позже
            try { TimeUnit.SECONDS.sleep(5); } catch (Exception ignored) {}
            startDeliverySimulation(parcelId); // повторная попытка
            return;
        }

        // Назначаем машину и начинаем доставку
        parcel.setVehicleId(vehicleId);
        parcel.setStatus(ParcelStatus.IN_TRANSIT);
        parcel.setProgress(0);
        parcelRepository.save(parcel);

        System.out.println("🚛 Машина " + vehicleId + " назначена на посылку " + parcel.getParcelNumber());

        // Время доставки зависит от расстояния
        int totalSeconds = calculateDeliveryTime(parcel.getFromBranch(), parcel.getToBranch());
        int steps = 10;
        int sleepPerStep = totalSeconds / steps;

        for (int i = 1; i <= steps; i++) {
            try {
                TimeUnit.SECONDS.sleep(sleepPerStep);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                releaseVehicle(vehicleId);
                return;
            }

            parcel.setProgress(i * 10);
            parcelRepository.save(parcel);
        }

        // Успешная доставка
        parcel.setStatus(ParcelStatus.DELIVERED);
        parcel.setProgress(100);
        parcelRepository.save(parcel);

        releaseVehicle(vehicleId);

        System.out.println("🎉 Посылка " + parcel.getParcelNumber() + " успешно доставлена машиной " + vehicleId + "!");
    }

    private String assignVehicle() {
        if (availableVehicles.isEmpty()) return null;
        String vehicle = availableVehicles.remove(0);
        busyVehicles.add(vehicle);
        return vehicle;
    }

    private void releaseVehicle(String vehicleId) {
        busyVehicles.remove(vehicleId);
        availableVehicles.add(vehicleId);
        System.out.println("✅ Машина " + vehicleId + " освободилась");
    }

    private int calculateDeliveryTime(String from, String to) {
        int distance = Math.abs(getDistance(from) - getDistance(to));
        int time = 22 + (int)(distance * 0.085);
        if (time < 25) time = 25;
        if (time > 90) time = 90;
        return time;
    }

    private int getDistance(String city) {
        return switch (city) {
            case "Одесса-Центр" -> 0;
            case "Киев-Главпочтамт" -> 480;
            case "Львов-1" -> 350;
            case "Харьков-Север" -> 700;
            case "Днепр-Юг" -> 400;
            default -> 400;
        };
    }
}