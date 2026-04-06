package com.pochta.controller;

import com.pochta.model.Parcel;
import com.pochta.model.User;
import com.pochta.service.ParcelService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/parcels")
@RequiredArgsConstructor
public class ParcelController {

    private final ParcelService parcelService;

    @PostMapping("/create")
    public ResponseEntity<?> createParcel(@RequestBody CreateParcelRequest request, HttpSession session) {

        // Получаем текущего пользователя из сессии
        User currentUser = (User) session.getAttribute("currentUser");

        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "Вы не авторизованы. Пожалуйста, войдите в аккаунт."
            ));
        }

        try {
            Parcel parcel = parcelService.createParcel(
                    currentUser.getId(),
                    request.getFromBranch(),
                    request.getToBranch(),
                    request.getWeight()
            );

            return ResponseEntity.ok(Map.of(
                    "message", "Посылка успешно создана!",
                    "parcelNumber", parcel.getParcelNumber(),
                    "status", parcel.getStatus().name(),
                    "userFullName", currentUser.getFullName()
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // DTO
    public static class CreateParcelRequest {
        private String fromBranch;
        private String toBranch;
        private double weight;

        public String getFromBranch() { return fromBranch; }
        public void setFromBranch(String fromBranch) { this.fromBranch = fromBranch; }
        public String getToBranch() { return toBranch; }
        public void setToBranch(String toBranch) { this.toBranch = toBranch; }
        public double getWeight() { return weight; }
        public void setWeight(double weight) { this.weight = weight; }
    }
}