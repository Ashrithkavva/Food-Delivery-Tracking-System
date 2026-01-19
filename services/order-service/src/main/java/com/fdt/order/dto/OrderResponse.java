package com.fdt.order.dto;

import com.fdt.order.domain.Order;
import com.fdt.order.domain.OrderItem;
import com.fdt.order.domain.OrderStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID customerId,
        UUID restaurantId,
        UUID driverId,
        OrderStatus status,
        long subtotalCents,
        long deliveryCents,
        long totalCents,
        String currency,
        double pickupLat,
        double pickupLon,
        double dropoffLat,
        double dropoffLon,
        List<Item> items,
        Instant createdAt,
        Instant updatedAt,
        Instant deliveredAt
) implements Serializable {

    public record Item(String sku, String name, int quantity, long unitCents, String notes)
            implements Serializable {}

    public static OrderResponse from(Order o) {
        List<Item> items = o.getItems().stream()
                .map(OrderResponse::toItem)
                .toList();
        return new OrderResponse(
                o.getId(), o.getCustomerId(), o.getRestaurantId(), o.getDriverId(),
                o.getStatus(), o.getSubtotalCents(), o.getDeliveryCents(), o.getTotalCents(),
                o.getCurrency(), o.getPickupLat(), o.getPickupLon(),
                o.getDropoffLat(), o.getDropoffLon(),
                items, o.getCreatedAt(), o.getUpdatedAt(), o.getDeliveredAt());
    }

    private static Item toItem(OrderItem i) {
        return new Item(i.getSku(), i.getName(), i.getQuantity(), i.getUnitCents(), i.getNotes());
    }
}
