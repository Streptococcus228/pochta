package com.pochta.service;

import com.pochta.model.Parcel;
import com.pochta.model.ParcelStatus;
import com.pochta.repository.ParcelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

        // Ищем другие посылки из того же пункта отправления
        List<Parcel> otherParcels = parcelRepository.findByFromBranchAndStatus(
                parcel.getFromBranch(), ParcelStatus.CREATED);

        for (Parcel other : otherParcels) {
            if (!other.getId().equals(parcel.getId()) && parcelsToDeliver.size() < 5) {
                parcelsToDeliver.add(other);
            }
        }

        System.out.println("Машина " + vehicleId + " призначена на " + parcelsToDeliver.size() + " посилок (Trip: " + tripId + ")");
        runMultiStopTrip(parcelsToDeliver, vehicleId, tripId);
    }

    @Async
    public void startManualTrip(List<Long> parcelIds, String vehicleId) {
        List<Parcel> parcels = parcelRepository.findAllById(parcelIds);
        if (parcels.isEmpty()) return;

        // Якщо машина не вказана, намагаємося призначити
        if (vehicleId == null || vehicleId.isEmpty() || vehicleId.equals("AUTO")) {
            vehicleId = assignVehicle();
        } else {
            // Якщо вказана конкретна, перевіряємо доступність
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
        runMultiStopTrip(parcels, vehicleId, tripId);
    }

    private void runMultiStopTrip(List<Parcel> parcels, String vehicleId, String tripId) {
        if (parcels.isEmpty()) {
            releaseVehicle(vehicleId);
            return;
        }

        String initialLocation = parcels.get(0).getFromBranch();

        for (Parcel p : parcels) {
            p.setVehicleId(vehicleId);
            p.setTripId(tripId);
            // Активуємо тільки ті, що вже на місці старту
            if (p.getFromBranch().equals(initialLocation)) {
                p.setStatus(ParcelStatus.IN_TRANSIT);
            }
            p.setProgress(0);
        }
        parcelRepository.saveAll(parcels);

        // Побудова маршруту: всі from та to міста
        Set<String> allStops = new java.util.HashSet<>();
        for (Parcel p : parcels) {
            allStops.add(p.getFromBranch());
            allStops.add(p.getToBranch());
        }
        allStops.remove(initialLocation);

        List<String> route = new java.util.ArrayList<>();
        List<String> remainingStops = new java.util.ArrayList<>(allStops);
        String tempLoc = initialLocation;

        while (!remainingStops.isEmpty()) {
            String next = findClosest(tempLoc, remainingStops);
            route.add(next);
            remainingStops.remove(next);
            tempLoc = next;
        }

        // Розрахунок часових міток для кожної зупинки
        Map<String, Integer> stopArrivalTimes = new java.util.HashMap<>();
        stopArrivalTimes.put(initialLocation, 0);
        int totalSeconds = 0;
        tempLoc = initialLocation;
        for (String step : route) {
            totalSeconds += calculateDeliveryTime(tempLoc, step);
            stopArrivalTimes.put(step, totalSeconds);
            tempLoc = step;
        }
        if (totalSeconds == 0) totalSeconds = 1;

        log.info("Starting multi-stop trip {} with {} parcels and {} stops. Total seconds: {}, Multiplier: x{}",
                tripId, parcels.size(), route.size(), totalSeconds, speedMultiplier);

        int elapsedSeconds = 0;
        tempLoc = initialLocation;

        for (String step : route) {
            int segmentSeconds = calculateDeliveryTime(tempLoc, step);
            int stepsPerSegment = 10;

            for (int i = 1; i <= stepsPerSegment; i++) {
                try {
                    long stepMillis = ((long) segmentSeconds * 1000 / speedMultiplier) / stepsPerSegment;
                    Thread.sleep(Math.max(50, stepMillis));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    releaseVehicle(vehicleId);
                    return;
                }

                double segmentProgressFraction = (double) i / stepsPerSegment;
                int currentSegmentElapsed = (int) (segmentSeconds * segmentProgressFraction);
                int totalElapsed = elapsedSeconds + currentSegmentElapsed;
                int totalProgress = Math.min(100, (totalElapsed * 100) / totalSeconds);

                for (Parcel p : parcels) {
                    p.setTripProgress(totalProgress);
                    if (p.getStatus() == ParcelStatus.IN_TRANSIT) {
                        Integer startTime = stopArrivalTimes.get(p.getFromBranch());
                        Integer endTime = stopArrivalTimes.get(p.getToBranch());
                        
                        if (startTime != null && endTime != null) {
                            int parcelTotalTime = endTime - startTime;
                            if (parcelTotalTime > 0) {
                                int parcelElapsed = totalElapsed - startTime;
                                int parcelProgress = Math.min(100, Math.max(0, (parcelElapsed * 100) / parcelTotalTime));
                                p.setProgress(parcelProgress);
                            } else {
                                p.setProgress(100);
                            }
                        }
                    } else if (p.getStatus() == ParcelStatus.DELIVERED) {
                        p.setProgress(100);
                    } else {
                        p.setProgress(0);
                    }
                }
                parcelRepository.saveAll(parcels);
            }

            elapsedSeconds += segmentSeconds;
            // Прибули в пункт. Завантажуємо/Вивантажуємо.
            for (Parcel p : parcels) {
                // Вивантаження
                if (p.getToBranch().equals(step) && p.getStatus() == ParcelStatus.IN_TRANSIT) {
                    p.setStatus(ParcelStatus.DELIVERED);
                    p.setProgress(100);
                    log.info("Delivered parcel {} at {}", p.getParcelNumber(), step);
                }
                // Завантаження
                if (p.getFromBranch().equals(step) && p.getStatus() != ParcelStatus.IN_TRANSIT && p.getStatus() != ParcelStatus.DELIVERED) {
                    p.setStatus(ParcelStatus.IN_TRANSIT);
                    log.info("Picked up parcel {} at {}", p.getParcelNumber(), step);
                }
            }
            
            int currentTripProgress = Math.min(100, (elapsedSeconds * 100) / totalSeconds);
            for (Parcel p : parcels) {
                p.setTripProgress(currentTripProgress);
            }
            parcelRepository.saveAll(parcels);
            tempLoc = step;
            log.info("Vehicle {} reached stop: {}.", vehicleId, step);
        }

        releaseVehicle(vehicleId);
        System.out.println("Рейс " + tripId + " завершено!");
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

    private String findClosest(String current, List<String> candidates) {
        String closest = null;
        int minDistance = Integer.MAX_VALUE;
        int currentDist = getDistance(current);

        for (String cand : candidates) {
            int d = Math.abs(currentDist - getDistance(cand));
            if (d < minDistance) {
                minDistance = d;
                closest = cand;
            }
        }
        return closest;
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