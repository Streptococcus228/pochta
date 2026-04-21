package com.pochta.service;

import com.pochta.model.Parcel;
import com.pochta.model.ParcelStatus;
import com.pochta.repository.ParcelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SimulatorService {

    private final ParcelRepository parcelRepository;
    private final BranchService branchService;
    private final Random random = new Random();

    private volatile int speedMultiplier = 1;

    public void setSpeedMultiplier(int speed) {
        log.info("Setting simulation speed multiplier to x{}", speed);
        this.speedMultiplier = speed;
    }

    public int getSpeedMultiplier() {
        return speedMultiplier;
    }

    // Доступные машины
    private final List<String> allVehicles = List.of("V001", "V002", "V003");
    private final List<String> availableVehicles = new ArrayList<>(allVehicles);
    private final List<String> busyVehicles = new ArrayList<>();

    public List<String> getAllVehicles() {
        return allVehicles;
    }

    public List<String> getBusyVehicles() {
        return new ArrayList<>(busyVehicles);
    }

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
            System.out.println("Всі машини зайняті. Посилка " + parcel.getParcelNumber() + " буде чекати...");
            // Можно добавить небольшую задержку и попробовать позже
            try { TimeUnit.SECONDS.sleep(5); } catch (Exception ignored) {}
            startDeliverySimulation(parcelId); // повторная попытка
            return;
        }

        // Назначаем машину и начинаем доставку
        String tripId = "TRIP-" + System.currentTimeMillis() + "-" + vehicleId;
        List<Parcel> parcelsToDeliver = new ArrayList<>();
        parcelsToDeliver.add(parcel);

        // Ищем другие посылки для того же маршрута
        List<Parcel> otherParcels = parcelRepository.findByFromBranchAndToBranchAndStatus(
                parcel.getFromBranch(), parcel.getToBranch(), ParcelStatus.CREATED);

        for (Parcel other : otherParcels) {
            if (!other.getId().equals(parcel.getId()) && parcelsToDeliver.size() < 3) {
                parcelsToDeliver.add(other);
            }
        }

        for (Parcel p : parcelsToDeliver) {
            p.setVehicleId(vehicleId);
            p.setTripId(tripId);
            p.setStatus(ParcelStatus.IN_TRANSIT);
            p.setProgress(0);
        }
        parcelRepository.saveAll(parcelsToDeliver);

        System.out.println("Машина " + vehicleId + " призначена на " + parcelsToDeliver.size() + " посилок (Trip: " + tripId + ")");

        // Час доставки залежить від відстані
        int totalSeconds = calculateDeliveryTime(parcel.getFromBranch(), parcel.getToBranch());
        int steps = 20; 
        int currentMultiplier = speedMultiplier;
        long totalMillis = (long) totalSeconds * 1000 / currentMultiplier;
        long sleepMillis = Math.max(100, totalMillis / steps); // Мінімум 100мс між оновленнями

        log.info("Starting simulation for parcel {} ({} to {}). Total seconds: {}, Multiplier: x{}", 
                parcel.getParcelNumber(), parcel.getFromBranch(), parcel.getToBranch(), totalSeconds, currentMultiplier);

        for (int i = 1; i <= steps; i++) {
            try {
                long stepMillis = ((long) totalSeconds * 1000 / speedMultiplier) / steps;
                Thread.sleep(Math.max(50, stepMillis));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                releaseVehicle(vehicleId);
                return;
            }

            int currentProgress = (i * 100) / steps;
            for (Parcel p : parcelsToDeliver) {
                p.setProgress(currentProgress);
            }
            parcelRepository.saveAll(parcelsToDeliver);
        }

        // Успешная доставка
        for (Parcel p : parcelsToDeliver) {
            p.setStatus(ParcelStatus.DELIVERED);
            p.setProgress(100);
        }
        parcelRepository.saveAll(parcelsToDeliver);

        releaseVehicle(vehicleId);

        System.out.println("Посилки в рейсі " + tripId + " успішно доставлені!");
    }

    @Async
    public void startManualTrip(List<Long> parcelIds, String vehicleId) {
        List<Parcel> parcels = parcelRepository.findAllById(parcelIds);
        if (parcels.isEmpty()) return;

        // Если машина не указана, пытаемся назначить
        if (vehicleId == null || vehicleId.isEmpty() || vehicleId.equals("AUTO")) {
            vehicleId = assignVehicle();
        } else {
            // Если указана конкретная, проверяем доступность
            if (busyVehicles.contains(vehicleId)) {
                System.out.println("Машина " + vehicleId + " зайнята!");
                return;
            }
            availableVehicles.remove(vehicleId);
            busyVehicles.add(vehicleId);
        }

        if (vehicleId == null) {
            System.out.println("Немає вільних машин для ручного рейсу");
            return;
        }

        String tripId = "MANUAL-" + System.currentTimeMillis() + "-" + vehicleId;
        
        for (Parcel p : parcels) {
            p.setVehicleId(vehicleId);
            p.setTripId(tripId);
            p.setStatus(ParcelStatus.IN_TRANSIT);
            p.setProgress(0);
        }
        parcelRepository.saveAll(parcels);

        // Для ручного рейса час рахуємо по першій посилці
        Parcel first = parcels.get(0);
        int totalSeconds = calculateDeliveryTime(first.getFromBranch(), first.getToBranch());
        int steps = 20;
        int currentMultiplier = speedMultiplier;
        long totalMillis = (long) totalSeconds * 1000 / currentMultiplier;
        long sleepMillis = Math.max(100, totalMillis / steps);

        log.info("Starting manual trip {} with {} parcels. Total seconds: {}, Multiplier: x{}", 
                tripId, parcels.size(), totalSeconds, currentMultiplier);

        for (int i = 1; i <= steps; i++) {
            try {
                long stepMillis = ((long) totalSeconds * 1000 / speedMultiplier) / steps;
                Thread.sleep(Math.max(50, stepMillis));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                releaseVehicle(vehicleId);
                return;
            }

            int currentProgress = (i * 100) / steps;
            for (Parcel p : parcels) {
                p.setProgress(currentProgress);
            }
            parcelRepository.saveAll(parcels);
        }

        for (Parcel p : parcels) {
            p.setStatus(ParcelStatus.DELIVERED);
            p.setProgress(100);
        }
        parcelRepository.saveAll(parcels);

        releaseVehicle(vehicleId);
        System.out.println("Ручний рейс " + tripId + " завершено!");
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
        System.out.println("Машина " + vehicleId + " звільнилася");
    }

    private int calculateDeliveryTime(String from, String to) {
        int distance = Math.abs(getDistance(from) - getDistance(to));
        int time = (22 + (int)(distance * 0.085)) * 2; // Увеличено в 2 раза
        return Math.max(50, Math.min(180, time)); // Также увеличил границы
    }

    private int getDistance(String cityName) {
        var branch = branchService.getBranchByName(cityName);
        return branch != null ? branch.getDistance() : 400;
    }
}