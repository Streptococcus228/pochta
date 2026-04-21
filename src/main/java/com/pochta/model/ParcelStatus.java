package com.pochta.model;

public enum ParcelStatus {
    CREATED,      // щойно створена
    QUEUED,       // у черзі на відправлення
    PAID,         // оплачено
    IN_TRANSIT,   // в дорозі
    DELIVERED,    // доставлено
    FAILED        // помилка доставки
}