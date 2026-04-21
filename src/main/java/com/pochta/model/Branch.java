package com.pochta.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "branches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    @Column(nullable = false)
    private int distance; // Расстояние от условного "0" (Одесса)

    // Координаты для карты (Leaflet)
    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;
}
