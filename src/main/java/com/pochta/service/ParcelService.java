package com.pochta.service;

import com.pochta.dto.ParcelHistoryDto;
import com.pochta.model.Parcel;
import com.pochta.model.ParcelStatus;
import com.pochta.model.User;
import com.pochta.repository.ParcelRepository;
import com.pochta.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ParcelService {

    private final PricingService pricingService;
    private final ParcelRepository parcelRepository;
    private final UserRepository userRepository;
    private final SimulatorService simulatorService;

    /**
     * Создание посылки + расчёт стоимости + запуск симуляции
     */
    public Parcel createParcel(Long userId, String fromBranch, String toBranch, double weight) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

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
                .createdAt(LocalDateTime.now())
                .build();

        Parcel savedParcel = parcelRepository.save(parcel);

        // Запускаем симуляцию доставки
        simulatorService.startDeliverySimulation(savedParcel.getId());

        return savedParcel;
    }

    /**
     * Получить все посылки пользователя для истории
     */
    public List<ParcelHistoryDto> getParcelsHistoryByUserId(Long userId) {
        return parcelRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
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
        parcelRepository.deleteById(parcelId);
    }

    // Получить все посылки (для админа)
    public List<Parcel> getAllParcels() {
        return parcelRepository.findAll();
    }

    // Сброс всех посылок (для тестирования)
    public void resetAllParcels() {
        parcelRepository.deleteAll();
    }
}