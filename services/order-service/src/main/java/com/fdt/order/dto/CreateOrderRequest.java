package com.fdt.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull UUID customerId,
        @NotNull UUID restaurantId,
        @NotBlank @Size(max = 128) String idempotencyKey,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double pickupLat,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double pickupLon,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double dropoffLat,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double dropoffLon,
        @NotNull @Min(0) Long deliveryCents,
        @Size(min = 3, max = 3) String currency,
        @NotEmpty @Valid List<Item> items
) {
    public record Item(
            @NotBlank @Size(max = 64) String sku,
            @NotBlank @Size(max = 255) String name,
            @Min(1) int quantity,
            @Min(0) long unitCents,
            @Size(max = 500) String notes
    ) {}
}
