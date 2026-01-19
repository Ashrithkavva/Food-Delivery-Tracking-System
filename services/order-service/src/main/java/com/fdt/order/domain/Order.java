package com.fdt.order.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "driver_id")
    private UUID driverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "subtotal_cents", nullable = false)
    private long subtotalCents;

    @Column(name = "delivery_cents", nullable = false)
    private long deliveryCents;

    @Column(name = "total_cents", nullable = false)
    private long totalCents;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "dropoff_lat", nullable = false)
    private double dropoffLat;

    @Column(name = "dropoff_lon", nullable = false)
    private double dropoffLon;

    @Column(name = "pickup_lat", nullable = false)
    private double pickupLat;

    @Column(name = "pickup_lon", nullable = false)
    private double pickupLon;

    @Column(name = "idempotency_key", nullable = false, length = 128, updatable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
        // for JPA
    }

    /** Factory: a new order always starts in PENDING. */
    public static Order create(
            UUID customerId,
            UUID restaurantId,
            String idempotencyKey,
            double pickupLat,
            double pickupLon,
            double dropoffLat,
            double dropoffLon,
            long subtotalCents,
            long deliveryCents,
            String currency) {
        Order o = new Order();
        Instant now = Instant.now();
        o.id = UUID.randomUUID();
        o.customerId = Objects.requireNonNull(customerId);
        o.restaurantId = Objects.requireNonNull(restaurantId);
        o.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        o.pickupLat = pickupLat;
        o.pickupLon = pickupLon;
        o.dropoffLat = dropoffLat;
        o.dropoffLon = dropoffLon;
        o.subtotalCents = subtotalCents;
        o.deliveryCents = deliveryCents;
        o.totalCents = subtotalCents + deliveryCents;
        o.currency = currency == null ? "USD" : currency;
        o.status = OrderStatus.PENDING;
        o.createdAt = now;
        o.updatedAt = now;
        return o;
    }

    /**
     * Apply a state transition. Throws {@link IllegalStateTransitionException}
     * if the move isn't allowed from the current status.
     */
    public void transitionTo(OrderStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateTransitionException(
                    "Cannot transition order %s from %s to %s".formatted(id, status, target));
        }
        this.status = target;
        this.updatedAt = Instant.now();
        if (target == OrderStatus.DELIVERED) {
            this.deliveredAt = this.updatedAt;
        }
    }

    public void assignDriver(UUID driverId) {
        this.driverId = Objects.requireNonNull(driverId);
        this.updatedAt = Instant.now();
    }

    public void addItem(OrderItem item) {
        item.setOrder(this);
        this.items.add(item);
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public UUID getRestaurantId() { return restaurantId; }
    public UUID getDriverId() { return driverId; }
    public OrderStatus getStatus() { return status; }
    public long getSubtotalCents() { return subtotalCents; }
    public long getDeliveryCents() { return deliveryCents; }
    public long getTotalCents() { return totalCents; }
    public String getCurrency() { return currency; }
    public double getDropoffLat() { return dropoffLat; }
    public double getDropoffLon() { return dropoffLon; }
    public double getPickupLat() { return pickupLat; }
    public double getPickupLon() { return pickupLon; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public long getVersion() { return version; }
    public List<OrderItem> getItems() { return items; }
}
