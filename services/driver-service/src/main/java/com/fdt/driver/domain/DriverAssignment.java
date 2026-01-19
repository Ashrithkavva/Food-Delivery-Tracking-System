package com.fdt.driver.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "driver_assignments")
public class DriverAssignment {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected DriverAssignment() {}

    public static DriverAssignment create(UUID driverId, UUID orderId) {
        DriverAssignment a = new DriverAssignment();
        a.id = UUID.randomUUID();
        a.driverId = driverId;
        a.orderId = orderId;
        a.assignedAt = Instant.now();
        return a;
    }

    public void complete() { this.completedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getDriverId() { return driverId; }
    public UUID getOrderId() { return orderId; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
