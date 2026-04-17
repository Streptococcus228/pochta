package com.pochta.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ParcelAdminDto {

    private Long id;
    private String parcelNumber;
    private String fromBranch;
    private String toBranch;
    private double weight;
    private double cost;
    private String status;
    private String vehicleId;
    private int progress;
    private LocalDateTime createdAt;
    private String userFullName;
}