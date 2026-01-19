package com.fdt.driver.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "drivers")
public class Driver {

    public enum Status { OFFLINE, AVAILABLE, ON_DELIVERY, BREAK }

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "phone", nullable = false, unique = true, length = 32)
    private String phone;

    @Column(name = "vehicle_type", nullable = false, length = 32)
    private String vehicleType;

    @Column(name = "license_plate", length = 32)
    private String licensePlate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private Status status = Status.OFFLINE;

    @Column(name = "rating", precision = 3, scale = 2)
    private BigDecimal rating = new BigDecimal("5.00");

    @Column(name = "onboarded_at", nullable = false, updatable = false)
    private Instant onboardedAt;

    @Column(name = "last_status_at", nullable = false)
    private Instant lastStatusAt;

    protected Driver() {}

    public static Driver onboard(String fullName, String phone, String vehicleType, String plate) {
        Driver d = new Driver();
        d.id = UUID.randomUUID();
        d.fullName = fullName;
        d.phone = phone;
        d.vehicleType = vehicleType;
        d.licensePlate = plate;
        d.onboardedAt = Instant.now();
        d.lastStatusAt = d.onboardedAt;
        return d;
    }

    public void setStatus(Status s) {
        this.status = s;
        this.lastStatusAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getVehicleType() { return vehicleType; }
    public String getLicensePlate() { return licensePlate; }
    public Status getStatus() { return status; }
    public BigDecimal getRating() { return rating; }
    public Instant getOnboardedAt() { return onboardedAt; }
    public Instant getLastStatusAt() { return lastStatusAt; }
}
