package com.fdt.order.kafka;

import com.fdt.order.domain.OrderStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Sealed hierarchy of events that order-service emits. The {@code type}
 * discriminator on the wire matches {@link #type()}, so downstream consumers
 * can filter without parsing the full payload.
 */
public sealed interface OrderEvent permits
        OrderEvent.OrderCreated,
        OrderEvent.OrderStatusChanged,
        OrderEvent.DriverAssigned,
        OrderEvent.OrderCancelled {

    UUID orderId();
    Instant occurredAt();
    String type();

    record OrderCreated(
            UUID orderId,
            UUID customerId,
            UUID restaurantId,
            double pickupLat,
            double pickupLon,
            double dropoffLat,
            double dropoffLon,
            long totalCents,
            String currency,
            Instant occurredAt
    ) implements OrderEvent {
        public String type() { return "OrderCreated"; }
    }

    record OrderStatusChanged(
            UUID orderId,
            OrderStatus from,
            OrderStatus to,
            Instant occurredAt
    ) implements OrderEvent {
        public String type() { return "OrderStatusChanged"; }
    }

    record DriverAssigned(
            UUID orderId,
            UUID driverId,
            Instant occurredAt
    ) implements OrderEvent {
        public String type() { return "DriverAssigned"; }
    }

    record OrderCancelled(
            UUID orderId,
            String reason,
            Instant occurredAt
    ) implements OrderEvent {
        public String type() { return "OrderCancelled"; }
    }
}
