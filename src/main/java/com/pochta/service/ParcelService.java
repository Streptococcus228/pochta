package com.pochta.service;

import com.pochta.dto.AdminDashboardDto;
import com.pochta.dto.ParcelAdminDto;
import com.pochta.dto.ParcelHistoryDto;
import com.pochta.model.Parcel;
import com.pochta.model.ParcelStatus;
import com.pochta.model.User;
import com.pochta.repository.ParcelRepository;
import com.pochta.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParcelService {

    private final PricingService pricingService;
    private final ParcelRepository parcelRepository;
    private final UserRepository userRepository;
    private final SimulatorService simulatorService;
    private final BranchService branchService;

    @PostConstruct
    public void migrateOldBranchNames() {
        log.info("Checking for old branch names in existing parcels...");
        Map<String, String> mapping = Map.of(
            "Одесса-Центр", "Одеса-Центр",
            "Киев-Главпочтамт", "Київ-Головпоштамт",
            "Львов-1", "Львів-1",
            "Харьков-Север", "Харків-Північ",
            "Днепр-Юг", "Дніпро-Південь"
        );

        List<Parcel> allParcels = parcelRepository.findAll();
        boolean changed = false;
        for (Parcel p : allParcels) {
            boolean pChanged = false;
            if (mapping.containsKey(p.getFromBranch())) {
                p.setFromBranch(mapping.get(p.getFromBranch()));
                pChanged = true;
            }
            if (mapping.containsKey(p.getToBranch())) {
                p.setToBranch(mapping.get(p.getToBranch()));
                pChanged = true;
            }
            if (pChanged) {
                parcelRepository.save(p);
                changed = true;
            }
        }
        if (changed) {
            log.info("Parcel branch names migration completed.");
        }
    }

    /**
     * Создание посылки + расчёт стоимости + запуск симуляции
     */
    public Parcel createParcel(Long userId, String fromBranch, String toBranch, double weight) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Користувач не знайдений"));

        String parcelNumber = "P" + System.currentTimeMillis();

        // Расчёт стоимости доставки
        double cost = pricingService.calculateCost(fromBranch, toBranch, weight);

        Parcel parcel = Parcel.builder()
                .parcelNumber(parcelNumber)
                .user(user)
                .fromBranch(fromBranch)
                .toBranch(toBranch)
                .weight(weight)
                .cost(cost)
                .status(ParcelStatus.CREATED)
                .createdAt(LocalDateTime.now(java.time.ZoneId.of("Europe/Kyiv")))
                .build();

        Parcel savedParcel = parcelRepository.save(parcel);

        // Автоматичний запуск симуляції вимкнено для ручного керування
        // simulatorService.startDeliverySimulation(savedParcel.getId());

        return savedParcel;
    }

    /**
     * Получить все посылки пользователя для истории
     */
    public List<ParcelHistoryDto> getParcelsHistoryByUserId(Long userId) {
        return parcelRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .filter(p -> !p.isDeletedByUser())
                .map(parcel -> new ParcelHistoryDto(
                        parcel.getId(),
                        parcel.getParcelNumber(),
                        parcel.getFromBranch(),
                        parcel.getToBranch(),
                        parcel.getWeight(),
                        parcel.getCost(),
                        parcel.getStatus(),
                        parcel.getVehicleId(),
                        parcel.getProgress(),
                        parcel.getCreatedAt()
                ))
                .toList();
    }

    /**
     * Удаление посылки
     */
    public void deleteParcel(Long parcelId) {
        parcelRepository.findById(parcelId).ifPresent(p -> {
            p.setDeletedByUser(true);
            parcelRepository.save(p);
        });
    }

    // Получить все посылки (для админа)
    public List<Parcel> getAllParcels() {
        return parcelRepository.findAll();
    }

    // Сброс всех посылок (для тестирования)
    public void resetAllParcels() {
        parcelRepository.deleteAll();
    }

    public List<ParcelAdminDto> getAllParcelsForAdmin(String search) {
        List<Parcel> parcels;
        if (search != null && !search.isEmpty()) {
            parcels = parcelRepository.findByParcelNumberContainingIgnoreCase(search);
        } else {
            parcels = parcelRepository.findAll();
        }

        return parcels.stream()
                .map(this::convertToAdminDto)
                .toList();
    }

    private ParcelAdminDto convertToAdminDto(Parcel p) {
        return new ParcelAdminDto(
                p.getId(),
                p.getParcelNumber(),
                p.getFromBranch(),
                p.getToBranch(),
                p.getWeight(),
                p.getCost(),
                p.getStatus().name(),
                p.getVehicleId(),
                p.getTripId(),
                p.getProgress(),
                p.getCreatedAt(),
                (p.getUser() != null && p.getUser().getFullName() != null)
                        ? p.getUser().getFullName()
                        : "—"
        );
    }

    public AdminDashboardDto getAdminDashboardStats() {
        List<Parcel> allParcels = parcelRepository.findAll();

        double totalCost = allParcels.stream().mapToDouble(Parcel::getCost).sum();
        double avgDeliveryCost = allParcels.isEmpty() ? 0 : totalCost / allParcels.size();

        // Затраты на бензин теперь считаем с меньшим коэффициентом
        double gasolineCosts = allParcels.stream()
                .collect(Collectors.groupingBy(p -> p.getTripId() != null ? p.getTripId() : "SINGLE-" + p.getId()))
                .values().stream()
                .mapToDouble(tripParcels -> {
                    Parcel p = tripParcels.get(0);
                    return Math.abs(getDistance(p.getFromBranch()) - getDistance(p.getToBranch()));
                })
                .sum() * 1.2; // Было 5.0

        // Популярные маршруты
        Map<String, Long> routeCounts = allParcels.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getFromBranch() + " → " + p.getToBranch(),
                        Collectors.counting()
                ));

        List<String> popularRoutes = routeCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .map(e -> e.getKey() + " (" + e.getValue() + ")")
                .toList();

        // Среднее время доставки (симуляция на основе расстояния)
        double avgDeliveryTime = allParcels.stream()
                .mapToDouble(p -> calculateSimulatedDeliveryTime(p.getFromBranch(), p.getToBranch()))
                .average()
                .orElse(0);

        // Статус автомобилей
        List<String> allVehicles = simulatorService.getAllVehicles();
        List<String> busyVehicles = simulatorService.getBusyVehicles();

        List<AdminDashboardDto.VehicleStatusDto> vehicleStatuses = allVehicles.stream()
                .map(v -> {
                    String status = busyVehicles.contains(v) ? "BUSY" : "FREE";
                    String currentTripId = null;
                    List<String> pNums = new java.util.ArrayList<>();
                    int progress = 0;

                    if (status.equals("BUSY")) {
                        List<Parcel> vehicleParcels = allParcels.stream()
                                .filter(p -> v.equals(p.getVehicleId()) && (p.getStatus() != ParcelStatus.CREATED))
                                .sorted((p1, p2) -> p2.getId().compareTo(p1.getId())) // Останній рейс машини
                                .toList();
                        if (!vehicleParcels.isEmpty()) {
                            String tripId = vehicleParcels.get(0).getTripId();
                            currentTripId = tripId;
                            pNums = vehicleParcels.stream()
                                    .filter(p -> tripId != null && tripId.equals(p.getTripId()))
                                    .map(Parcel::getParcelNumber)
                                    .toList();
                            progress = vehicleParcels.get(0).getProgress();
                        }
                    }

                    return AdminDashboardDto.VehicleStatusDto.builder()
                            .vehicleId(v)
                            .status(status)
                            .currentTripId(currentTripId)
                            .parcelNumbers(pNums)
                            .progress(progress)
                            .build();
                })
                .toList();

        // Данные для графика (группировка по дням или часам)
        Map<String, List<Parcel>> groupedByDate = allParcels.stream()
                .filter(p -> p.getCreatedAt() != null)
                .collect(Collectors.groupingBy(p -> {
                    try {
                        return p.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM"));
                    } catch (Exception e) {
                        return "unknown";
                    }
                }, Collectors.toList()));

        List<String> labels = groupedByDate.keySet().stream()
                .filter(label -> !"unknown".equals(label))
                .sorted((s1, s2) -> {
                    try {
                        var formatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM");
                        var d1 = java.time.MonthDay.parse(s1, formatter);
                        var d2 = java.time.MonthDay.parse(s2, formatter);
                        return d1.compareTo(d2);
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .toList();

        // Ограничиваем количество дней для графика (последние 10)
        if (labels.size() > 10) {
            labels = labels.subList(labels.size() - 10, labels.size());
        }

        List<Double> profitHistory = new java.util.ArrayList<>();
        List<Double> gasolineHistory = new java.util.ArrayList<>();

        for (String label : labels) {
            List<Parcel> dayParcels = groupedByDate.get(label);
            double dayProfit = dayParcels.stream().mapToDouble(Parcel::getCost).sum();
            double dayGasoline = dayParcels.stream()
                    .collect(Collectors.groupingBy(p -> p.getTripId() != null ? p.getTripId() : "S-" + p.getId()))
                    .values().stream()
                    .mapToDouble(tp -> Math.abs(getDistance(tp.get(0).getFromBranch()) - getDistance(tp.get(0).getToBranch())))
                    .sum() * 1.2;
            profitHistory.add(Math.round(dayProfit * 100.0) / 100.0);
            gasolineHistory.add(Math.round(dayGasoline * 100.0) / 100.0);
        }

        return AdminDashboardDto.builder()
                .gasolineCosts(Math.round(gasolineCosts * 100.0) / 100.0)
                .averageDeliveryCost(Math.round(avgDeliveryCost * 100.0) / 100.0)
                .popularRoutes(popularRoutes)
                .averageDeliveryTime(Math.round(avgDeliveryTime * 10.0) / 10.0)
                .vehicles(vehicleStatuses)
                .profitHistory(profitHistory)
                .gasolineHistory(gasolineHistory)
                .labels(labels)
                .build();
    }

    private int getDistance(String cityName) {
        var branch = branchService.getBranchByName(cityName);
        return branch != null ? branch.getDistance() : 400;
    }

    private int calculateSimulatedDeliveryTime(String from, String to) {
        int distance = Math.abs(getDistance(from) - getDistance(to));
        int time = 22 + (int) (distance * 0.085);
        return Math.max(25, Math.min(90, time));
    }
}