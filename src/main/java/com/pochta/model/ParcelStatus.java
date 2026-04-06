package com.pochta.model;

public enum ParcelStatus {
    CREATED,      // только создана
    QUEUED,       // ← добавили: в очереди на машину
    PAID,
    IN_TRANSIT,
    DELIVERED,
    FAILED
}