package com.pochta.dto;

import com.pochta.model.ParcelStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ParcelHistoryDto {

    private Long id;
    private String parcelNumber;
    private String fromBranch;
    private String toBranch;
    private double weight;
    private double cost;
    private ParcelStatus status;
    private String vehicleId;
    private int progress;
    private LocalDateTime createdAt;
}